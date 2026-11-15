package ie.ucd.meitheal.engagement;

import ie.ucd.meitheal.engagement.port.CategoryReadAccess;
import ie.ucd.meitheal.engagement.port.MemberReadAccess;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.model.RequestStatus;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.port.MemberManagerPort;
import ie.ucd.meitheal.shared.port.MemberManagerPort.MemberProfile;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.RequestView;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class EngagementManager {
    private final Function<MemberId, Optional<MemberEligibility>> findMember;
    private final Function<CategoryId, Optional<CategoryId>> findCategory;
    private final Supplier<LocalDate> simulatedDate;
    private final Object commonLock;
    private Map<RequestId, RequestView> requestsById = new LinkedHashMap<>();
    private Map<ProposalId, ProposalView> proposalsById = new LinkedHashMap<>();
    private Map<EngagementId, EngagementView> engagementsById = new LinkedHashMap<>();
    private boolean ownerCallbackActive;

    public EngagementManager(MemberManagerPort members, CategoryCatalog categories, SimulatedClock clock) {
        this(members, categories, clock, new Object());
    }

    public EngagementManager(
            MemberManagerPort members,
            CategoryCatalog categories,
            SimulatedClock clock,
            Object commonLock) {
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(categories, "categories");
        this.commonLock = Objects.requireNonNull(commonLock, "commonLock");
        this.findMember = id -> members.findById(id).value().map(MemberEligibility::new);
        this.findCategory = id -> categories.find(id).value().map(CategoryView::id);
        this.simulatedDate = Objects.requireNonNull(clock, "clock")::currentDate;
    }

    /** Supports the provisional request-creation callers and their UUID-based read ports. */
    public EngagementManager(MemberReadAccess members, CategoryReadAccess categories) {
        this.commonLock = new Object();
        Objects.requireNonNull(members, "memberReadAccess must not be null");
        Objects.requireNonNull(categories, "categoryReadAccess must not be null");
        this.findMember = id -> uuid(id.value()).flatMap(value -> {
            var localId = new ie.ucd.meitheal.engagement.port.MemberId(value);
            if (!members.exists(localId)) {
                return Optional.empty();
            }
            MemberStatus status = members.isActive(localId) ? MemberStatus.ACTIVE : MemberStatus.SUSPENDED;
            return Optional.of(new MemberEligibility(status, Set.of()));
        });
        this.findCategory = id -> categories.isKnown(new ie.ucd.meitheal.engagement.port.CategoryId(id.value()))
            ? Optional.of(id) : Optional.empty();
        this.simulatedDate = () -> {
            throw new IllegalStateException("A simulated clock is required for matching and proposals");
        };
    }

    public Result<RequestView> createRequest(
            MemberId requesterId,
            RequestId requestId,
            CategoryId categoryId,
            int durationMinutes,
            LocalDate windowStart,
            LocalDate windowEnd) {
        synchronized (commonLock) {
            if (ownerCallbackActive) {
                return callbackMutationFailure();
            }
            if (requesterId == null || requestId == null || categoryId == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Request IDs are required");
            }

            final RequestDuration duration;
            final RequestWindow window;
            try {
                duration = new RequestDuration(durationMinutes);
                window = new RequestWindow(windowStart, windowEnd);
            } catch (IllegalArgumentException | NullPointerException exception) {
                return Result.failure(FailureCode.MALFORMED_INPUT,
                    "Duration or request window is invalid");
            }

            MemberEligibility requester = findMember.apply(requesterId).orElse(null);
            if (requester == null) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Requester not found");
            }
            if (!isActive(requester)) {
                return Result.failure(FailureCode.SUSPENDED_MEMBER, "Requester is not active");
            }
            Optional<CategoryId> canonicalCategory = findCategory.apply(categoryId);
            if (canonicalCategory.isEmpty()) {
                return Result.failure(FailureCode.CATEGORY_NOT_FOUND, "Category is not known");
            }
            if (requestsById.containsKey(requestId)) {
                return Result.failure(FailureCode.INVALID_STATE, "Request ID already exists");
            }

            RequestView request = new RequestView(requestId, requesterId, canonicalCategory.orElseThrow(),
                duration.minutes(), window.earliestDate(), window.latestDate(), RequestStatus.OPEN);
            requestsById.put(requestId, request);
            return Result.success(request);
        }
    }

    public Result<RequestView> findRequest(RequestId requestId) {
        synchronized (commonLock) {
            if (requestId == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Request ID is required");
            }
            RequestView request = requestsById.get(requestId);
            return request == null
                ? Result.failure(FailureCode.REQUEST_NOT_FOUND, "Request not found")
                : Result.success(request);
        }
    }

    public Result<List<RequestView>> matchingRequests(MemberId helperId) {
        synchronized (commonLock) {
            if (helperId == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Helper ID is required");
            }
            MemberEligibility helper = findMember.apply(helperId).orElse(null);
            if (helper == null) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Helper not found");
            }
            if (!isActive(helper)) {
                return Result.failure(FailureCode.SUSPENDED_MEMBER, "Helper is not active");
            }

            LocalDate today = simulatedDate.get();
            List<RequestView> matches = requestsById.values().stream()
                .filter(request -> request.status() == RequestStatus.OPEN)
                .filter(request -> !helperId.equals(request.requesterId()))
                .filter(request -> !request.windowEnd().isBefore(today))
                .filter(request -> offersCategory(helper, request.categoryId()))
                .filter(request -> findMember.apply(request.requesterId())
                    .filter(EngagementManager::isActive)
                    .isPresent())
                .toList();
            return Result.success(matches);
        }
    }

    public Result<ProposalView> submitProposal(
            MemberId helperId,
            RequestId requestId,
            ProposalId proposalId,
            LocalDate proposedOn) {
        synchronized (commonLock) {
            if (ownerCallbackActive) {
                return callbackMutationFailure();
            }
            if (helperId == null || requestId == null || proposalId == null || proposedOn == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Proposal fields are required");
            }
            RequestView request = requestsById.get(requestId);
            if (request == null) {
                return Result.failure(FailureCode.REQUEST_NOT_FOUND, "Request not found");
            }
            MemberEligibility helper = findMember.apply(helperId).orElse(null);
            MemberEligibility requester = findMember.apply(request.requesterId()).orElse(null);
            if (helper == null || requester == null) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Participant not found");
            }
            if (!isActive(helper) || !isActive(requester)) {
                return Result.failure(FailureCode.SUSPENDED_MEMBER,
                    "Both participants must be active");
            }
            if (request.status() != RequestStatus.OPEN) {
                return Result.failure(FailureCode.INVALID_STATE, "Request is closed");
            }
            if (helperId.equals(request.requesterId())) {
                return Result.failure(FailureCode.BUSINESS_REJECTION,
                    "Helper must differ from requester");
            }
            if (!offersCategory(helper, request.categoryId())) {
                return Result.failure(FailureCode.BUSINESS_REJECTION,
                    "Helper must offer the request category");
            }
            if (!new RequestWindow(request.windowStart(), request.windowEnd()).contains(proposedOn)
                    || proposedOn.isBefore(simulatedDate.get())) {
                return Result.failure(FailureCode.BUSINESS_REJECTION,
                    "Proposed date must be within the window and on or after today");
            }
            if (proposalsById.containsKey(proposalId)) {
                return Result.failure(FailureCode.INVALID_STATE, "Proposal ID already exists");
            }
            boolean alreadyPending = proposalsById.values().stream()
                .anyMatch(proposal -> proposal.status() == ProposalStatus.PENDING
                    && proposal.requestId().equals(requestId)
                    && proposal.helperId().equals(helperId));
            if (alreadyPending) {
                return Result.failure(FailureCode.DUPLICATE_PROPOSAL,
                    "A pending proposal already exists for this helper and request");
            }

            ProposalView proposal = new ProposalView(proposalId, requestId, helperId,
                proposedOn, ProposalStatus.PENDING);
            proposalsById.put(proposalId, proposal);
            return Result.success(proposal);
        }
    }

    public Result<ProposalView> findProposal(MemberId requesterId, ProposalId proposalId) {
        synchronized (commonLock) {
            if (requesterId == null || proposalId == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Requester and proposal IDs are required");
            }
            if (findMember.apply(requesterId).isEmpty()) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Requester not found");
            }
            ProposalView proposal = proposalsById.get(proposalId);
            if (proposal == null) {
                return Result.failure(FailureCode.PROPOSAL_NOT_FOUND, "Proposal not found");
            }
            RequestView request = requestsById.get(proposal.requestId());
            if (request == null || !request.requesterId().equals(requesterId)) {
                return Result.failure(FailureCode.NOT_PARTICIPANT,
                    "Requester does not own the proposal's request");
            }
            return Result.success(proposal);
        }
    }

    public <T> Result<T> withSelection(MemberId requesterId, ProposalId proposalId, EngagementId engagementId,
            Function<EngagementView, Result<T>> beforePublish) {
        synchronized (commonLock) {
            if (requesterId == null || proposalId == null || engagementId == null || beforePublish == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Selection fields are required");
            }
            if (ownerCallbackActive) {
                return Result.failure(FailureCode.INVALID_STATE, "Selection callback is active");
            }
            ProposalView proposal = proposalsById.get(proposalId);
            if (proposal == null) {
                return Result.failure(FailureCode.PROPOSAL_NOT_FOUND, "Proposal not found");
            }
            RequestView request = requestsById.get(proposal.requestId());
            if (request == null || !request.requesterId().equals(requesterId)) {
                return Result.failure(FailureCode.NOT_PARTICIPANT, "Requester does not own proposal request");
            }
            MemberEligibility requester = findMember.apply(requesterId).orElse(null);
            MemberEligibility helper = findMember.apply(proposal.helperId()).orElse(null);
            if (requester == null || helper == null) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Participant not found");
            }
            if (!isActive(requester) || !isActive(helper)) {
                return Result.failure(FailureCode.SUSPENDED_MEMBER, "Both participants must be active");
            }
            if (request.status() != RequestStatus.OPEN || proposal.status() != ProposalStatus.PENDING) {
                return Result.failure(FailureCode.INVALID_STATE, "Request or proposal is no longer selectable");
            }
            if (!offersCategory(helper, request.categoryId())) {
                return Result.failure(FailureCode.BUSINESS_REJECTION, "Helper is not eligible for the request category");
            }
            LocalDate today = simulatedDate.get();
            if (!new RequestWindow(request.windowStart(), request.windowEnd()).contains(proposal.proposedOn())
                    || proposal.proposedOn().isBefore(today)) {
                return Result.failure(FailureCode.BUSINESS_REJECTION, "Proposed date is outside the eligible window");
            }
            if (engagementsById.containsKey(engagementId)) {
                return Result.failure(FailureCode.ATOMIC_OPERATION_FAILED, "Engagement ID already exists");
            }

            EngagementView engagement = new EngagementView(engagementId, proposalId, request.id(), requesterId,
                proposal.helperId(), request.durationMinutes(), proposal.proposedOn(), EngagementStatus.AGREED,
                Set.of(), null, null, false);
            RequestView closedRequest = new RequestView(request.id(), request.requesterId(), request.categoryId(),
                request.durationMinutes(), request.windowStart(), request.windowEnd(), RequestStatus.CLOSED);
            Map<RequestId, RequestView> candidateRequests = new LinkedHashMap<>(requestsById);
            candidateRequests.put(request.id(), closedRequest);
            Map<ProposalId, ProposalView> candidateProposals = new LinkedHashMap<>(proposalsById);
            for (ProposalView candidate : proposalsById.values()) {
                if (candidate.requestId().equals(request.id()) && candidate.status() == ProposalStatus.PENDING) {
                    ProposalStatus status = candidate.id().equals(proposalId) ? ProposalStatus.SELECTED : ProposalStatus.REJECTED;
                    candidateProposals.put(candidate.id(), new ProposalView(candidate.id(), candidate.requestId(),
                        candidate.helperId(), candidate.proposedOn(), status));
                }
            }
            Map<EngagementId, EngagementView> candidateEngagements = new LinkedHashMap<>(engagementsById);
            candidateEngagements.put(engagementId, engagement);
            Result<T> callbackResult;
            ownerCallbackActive = true;
            try {
                callbackResult = beforePublish.apply(engagement);
                if (callbackResult == null) {
                    throw new NullPointerException("Selection callback returned null");
                }
            } finally {
                ownerCallbackActive = false;
            }
            if (callbackResult.isFailure()) {
                return callbackResult;
            }
            requestsById = candidateRequests;
            proposalsById = candidateProposals;
            engagementsById = candidateEngagements;
            return callbackResult;
        }
    }

    public Result<List<ProposalView>> proposalsForRequest(MemberId requesterId, RequestId requestId) {
        synchronized (commonLock) {
            if (requesterId == null || requestId == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Requester and request IDs are required");
            }
            if (findMember.apply(requesterId).isEmpty()) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Requester not found");
            }
            RequestView request = requestsById.get(requestId);
            if (request == null) {
                return Result.failure(FailureCode.REQUEST_NOT_FOUND, "Request not found");
            }
            if (!request.requesterId().equals(requesterId)) {
                return Result.failure(FailureCode.NOT_PARTICIPANT, "Requester does not own request");
            }
            return Result.success(proposalsById.values().stream()
                .filter(proposal -> proposal.requestId().equals(requestId))
                .toList());
        }
    }

    public Result<EngagementView> findEngagement(MemberId participantId, EngagementId engagementId) {
        synchronized (commonLock) {
            if (participantId == null || engagementId == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT,
                    "Participant and engagement IDs are required");
            }
            if (findMember.apply(participantId).isEmpty()) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Participant not found");
            }
            EngagementView engagement = engagementsById.get(engagementId);
            if (engagement == null) {
                return Result.failure(FailureCode.ENGAGEMENT_NOT_FOUND, "Engagement not found");
            }
            if (!participantId.equals(engagement.requesterId())
                    && !participantId.equals(engagement.helperId())) {
                return Result.failure(FailureCode.NOT_PARTICIPANT,
                    "Member is not an engagement participant");
            }
            return Result.success(engagement);
        }
    }

    public <T> Result<T> withProposalCleanup(MemberId memberId, Supplier<Result<T>> beforePublish) {
        synchronized (commonLock) {
            if (ownerCallbackActive) {
                return callbackMutationFailure();
            }
            if (memberId == null || beforePublish == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Member ID and cleanup callback are required");
            }

            Map<ProposalId, ProposalView> candidateProposals = new LinkedHashMap<>(proposalsById);
            for (ProposalView proposal : proposalsById.values()) {
                RequestView request = requestsById.get(proposal.requestId());
                if (proposal.status() == ProposalStatus.PENDING
                        && (memberId.equals(proposal.helperId())
                            || (request != null && memberId.equals(request.requesterId())))) {
                    candidateProposals.put(proposal.id(), new ProposalView(proposal.id(), proposal.requestId(),
                        proposal.helperId(), proposal.proposedOn(), ProposalStatus.INVALIDATED));
                }
            }

            Result<T> callbackResult;
            ownerCallbackActive = true;
            try {
                callbackResult = beforePublish.get();
                if (callbackResult == null) {
                    throw new NullPointerException("Proposal cleanup callback returned null");
                }
            } finally {
                ownerCallbackActive = false;
            }
            if (callbackResult.isFailure()) {
                return callbackResult;
            }
            proposalsById = candidateProposals;
            return callbackResult;
        }
    }

    public Result<EngagementView> withConfirmation(MemberId participantId, EngagementId engagementId,
            LocalDate today, Function<EngagementView, Result<EngagementView>> settleBeforePublish) {
        synchronized (commonLock) {
            if (ownerCallbackActive) {
                return callbackMutationFailure();
            }
            if (participantId == null || engagementId == null || today == null || settleBeforePublish == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Confirmation fields are required");
            }
            EngagementView engagement = engagementsById.get(engagementId);
            if (engagement == null) {
                return Result.failure(FailureCode.ENGAGEMENT_NOT_FOUND, "Engagement not found");
            }
            if (!participantId.equals(engagement.requesterId()) && !participantId.equals(engagement.helperId())) {
                return Result.failure(FailureCode.NOT_PARTICIPANT, "Member is not an engagement participant");
            }
            if (engagement.status() != EngagementStatus.AGREED) {
                return Result.failure(FailureCode.INVALID_STATE, "Engagement is not awaiting confirmation");
            }
            if (today.isBefore(engagement.scheduledDate())) {
                return Result.failure(FailureCode.BUSINESS_REJECTION, "Engagement cannot be confirmed before its scheduled date");
            }
            if (engagement.confirmationActors().contains(participantId)) {
                return Result.failure(FailureCode.DUPLICATE, "Participant has already confirmed");
            }

            Set<MemberId> actors = new java.util.LinkedHashSet<>(engagement.confirmationActors());
            actors.add(participantId);
            if (actors.size() == 1) {
                EngagementView candidate = new EngagementView(engagement.id(), engagement.proposalId(),
                    engagement.requestId(), engagement.requesterId(), engagement.helperId(),
                    engagement.durationMinutes(), engagement.scheduledDate(), EngagementStatus.AGREED,
                    actors, null, null, false);
                Result<EngagementView> preparedResult = Result.success(candidate);
                engagementsById.put(engagementId, candidate);
                return preparedResult;
            }

            EngagementView candidate = new EngagementView(engagement.id(), engagement.proposalId(),
                engagement.requestId(), engagement.requesterId(), engagement.helperId(),
                engagement.durationMinutes(), engagement.scheduledDate(), EngagementStatus.COMPLETED,
                actors, null, null, false);
            Map<EngagementId, EngagementView> candidateEngagements = new LinkedHashMap<>(engagementsById);
            candidateEngagements.put(engagementId, candidate);
            Result<EngagementView> preparedResult = Result.success(candidate);
            Result<EngagementView> callbackResult;
            ownerCallbackActive = true;
            try {
                callbackResult = settleBeforePublish.apply(candidate);
                if (callbackResult == null) {
                    throw new NullPointerException("Confirmation callback returned null");
                }
            } finally {
                ownerCallbackActive = false;
            }
            if (callbackResult.isFailure()) {
                return callbackResult;
            }
            engagementsById = candidateEngagements;
            return preparedResult;
        }
    }

    public Result<EngagementView> withCancellation(MemberId participantId, EngagementId engagementId,
            LocalDate today, Function<EngagementView, Result<EngagementView>> releaseBeforePublish) {
        synchronized (commonLock) {
            if (ownerCallbackActive) {
                return callbackMutationFailure();
            }
            if (participantId == null || engagementId == null || today == null || releaseBeforePublish == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Cancellation fields are required");
            }
            EngagementView engagement = engagementsById.get(engagementId);
            if (engagement == null) {
                return Result.failure(FailureCode.ENGAGEMENT_NOT_FOUND, "Engagement not found");
            }
            if (!participantId.equals(engagement.requesterId()) && !participantId.equals(engagement.helperId())) {
                return Result.failure(FailureCode.NOT_PARTICIPANT, "Member is not an engagement participant");
            }
            if (engagement.status() != EngagementStatus.AGREED) {
                return Result.failure(FailureCode.INVALID_STATE, "Engagement is not awaiting cancellation");
            }
            if (!today.isBefore(engagement.scheduledDate())) {
                return Result.failure(FailureCode.CANCELLATION_CLOSED, "Cancellation closes on the scheduled date");
            }

            boolean late = today.plusDays(1).equals(engagement.scheduledDate());
            EngagementView candidate = new EngagementView(engagement.id(), engagement.proposalId(),
                engagement.requestId(), engagement.requesterId(), engagement.helperId(),
                engagement.durationMinutes(), engagement.scheduledDate(), EngagementStatus.CANCELLED,
                engagement.confirmationActors(), participantId, today, late);
            Map<EngagementId, EngagementView> candidateEngagements = new LinkedHashMap<>(engagementsById);
            candidateEngagements.put(engagementId, candidate);
            Result<EngagementView> preparedResult = Result.success(candidate);
            Result<EngagementView> callbackResult;
            ownerCallbackActive = true;
            try {
                callbackResult = releaseBeforePublish.apply(candidate);
                if (callbackResult == null) {
                    throw new NullPointerException("Cancellation callback returned null");
                }
            } finally {
                ownerCallbackActive = false;
            }
            if (callbackResult.isFailure()) {
                return callbackResult;
            }
            engagementsById = candidateEngagements;
            return preparedResult;
        }
    }

    public Result<List<EngagementView>> engagementsForMember(MemberId memberId) {
        synchronized (commonLock) {
            if (memberId == null) {
                return Result.failure(FailureCode.MALFORMED_INPUT, "Member ID is required");
            }
            if (findMember.apply(memberId).isEmpty()) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Member not found");
            }
            return Result.success(engagementsById.values().stream()
                .filter(engagement -> memberId.equals(engagement.requesterId())
                    || memberId.equals(engagement.helperId()))
                .toList());
        }
    }

    private static <T> Result<T> callbackMutationFailure() {
        return Result.failure(FailureCode.INVALID_STATE, "Owner mutation is not allowed during an owner callback");
    }

    private boolean offersCategory(MemberEligibility helper, CategoryId categoryId) {
        return helper.categories().contains(categoryId) && findCategory.apply(categoryId).isPresent();
    }

    private record MemberEligibility(MemberStatus status, Set<CategoryId> categories) {
        private MemberEligibility(MemberProfile profile) {
            this(profile.status(), profile.categories());
        }
    }

    private static boolean isActive(MemberEligibility member) {
        return member.status() == MemberStatus.ACTIVE;
    }

    public RequestCreationResult createRequest(
        ie.ucd.meitheal.engagement.RequestId requestId,
        ie.ucd.meitheal.engagement.port.MemberId requesterId,
        ie.ucd.meitheal.engagement.port.CategoryId categoryId,
        RequestDuration duration,
        RequestWindow window
    ) {
        synchronized (commonLock) {
            if (ownerCallbackActive) {
                return new RequestCreationResult.Failure(RequestCreationError.INVALID_STATE,
                    "Owner mutation is not allowed during an owner callback");
            }
            Objects.requireNonNull(duration, "duration must not be null");
            Objects.requireNonNull(window, "window must not be null");
            return createRequest(requestId, requesterId, categoryId, duration.minutes(),
                window.earliestDate(), window.latestDate());
        }
    }

    public RequestCreationResult createRequest(
        ie.ucd.meitheal.engagement.RequestId requestId,
        ie.ucd.meitheal.engagement.port.MemberId requesterId,
        ie.ucd.meitheal.engagement.port.CategoryId categoryId,
        int durationMinutes,
        LocalDate earliestDate,
        LocalDate latestDate
    ) {
        synchronized (commonLock) {
            if (ownerCallbackActive) {
                return new RequestCreationResult.Failure(RequestCreationError.INVALID_STATE,
                    "Owner mutation is not allowed during an owner callback");
            }
            Objects.requireNonNull(requestId, "requestId must not be null");
            Objects.requireNonNull(requesterId, "requesterId must not be null");
            Objects.requireNonNull(categoryId, "categoryId must not be null");
            Result<RequestView> result = createRequest(new MemberId(requesterId.value().toString()),
                sharedId(requestId), new CategoryId(categoryId.value()), durationMinutes,
                earliestDate, latestDate);
            if (result.isSuccess()) {
                return new RequestCreationResult.Success(
                    localRequest(result.value().orElseThrow()).orElseThrow());
            }
            RequestCreationError error = switch (result.failureCode().orElseThrow()) {
                case MEMBER_NOT_FOUND -> RequestCreationError.NOT_FOUND;
                case SUSPENDED_MEMBER -> RequestCreationError.SUSPENDED_MEMBER;
                case CATEGORY_NOT_FOUND -> RequestCreationError.INVALID_CATEGORY;
                case MALFORMED_INPUT -> RequestCreationError.MALFORMED_INPUT;
                case INVALID_STATE -> RequestCreationError.INVALID_STATE;
                default -> throw new IllegalStateException("Unexpected request failure");
            };
            return new RequestCreationResult.Failure(error, result.detail().orElseThrow());
        }
    }

    public Optional<Request> findRequestById(ie.ucd.meitheal.engagement.RequestId requestId) {
        synchronized (commonLock) {
            Objects.requireNonNull(requestId, "requestId must not be null");
            return findRequest(sharedId(requestId)).value().flatMap(EngagementManager::localRequest);
        }
    }

    /** The provisional view can represent requests with UUID request and requester IDs. */
    public List<Request> getRequests() {
        synchronized (commonLock) {
            return requestsById.values().stream()
                .map(EngagementManager::localRequest)
                .flatMap(Optional::stream)
                .toList();
        }
    }

    private static RequestId sharedId(ie.ucd.meitheal.engagement.RequestId id) {
        return new RequestId(id.value().toString());
    }

    private static Optional<Request> localRequest(RequestView request) {
        return uuid(request.id().value()).flatMap(id -> uuid(request.requesterId().value()).map(requester ->
            new Request(new ie.ucd.meitheal.engagement.RequestId(id), new ie.ucd.meitheal.engagement.port.MemberId(requester),
                new ie.ucd.meitheal.engagement.port.CategoryId(request.categoryId().value()),
                new RequestDuration(request.durationMinutes()), new RequestWindow(request.windowStart(), request.windowEnd()),
                ie.ucd.meitheal.engagement.RequestStatus.valueOf(request.status().name()))));
    }

    private static Optional<UUID> uuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equals(value) ? Optional.of(parsed) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
