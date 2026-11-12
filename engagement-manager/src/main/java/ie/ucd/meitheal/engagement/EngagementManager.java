package ie.ucd.meitheal.engagement;

import ie.ucd.meitheal.engagement.port.CategoryReadAccess;
import ie.ucd.meitheal.engagement.port.MemberReadAccess;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.model.RequestStatus;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.port.MemberManagerPort;
import ie.ucd.meitheal.shared.port.MemberManagerPort.MemberProfile;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.view.CategoryView;
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
    private final Map<RequestId, RequestView> requestsById = new LinkedHashMap<>();
    private final Map<ProposalId, ProposalView> proposalsById = new LinkedHashMap<>();

    public EngagementManager(MemberManagerPort members, CategoryCatalog categories, SimulatedClock clock) {
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(categories, "categories");
        this.findMember = id -> members.findById(id).value().map(MemberEligibility::new);
        this.findCategory = id -> categories.find(id).value().map(CategoryView::id);
        this.simulatedDate = Objects.requireNonNull(clock, "clock")::currentDate;
    }

    /** Supports the provisional request-creation callers and their UUID-based read ports. */
    public EngagementManager(MemberReadAccess members, CategoryReadAccess categories) {
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

    public synchronized Result<RequestView> createRequest(MemberId requesterId,
            RequestId requestId, CategoryId categoryId,
            int durationMinutes, LocalDate windowStart, LocalDate windowEnd) {
        if (requesterId == null || requestId == null || categoryId == null) {
            return Result.failure(FailureCode.MALFORMED_INPUT, "Request IDs are required");
        }

        final RequestDuration duration;
        final RequestWindow window;
        try {
            duration = new RequestDuration(durationMinutes);
            window = new RequestWindow(windowStart, windowEnd);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Result.failure(FailureCode.MALFORMED_INPUT, "Duration or request window is invalid");
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
            duration.minutes(), window.earliestDate(), window.latestDate(),
            RequestStatus.OPEN);
        requestsById.put(requestId, request);
        return Result.success(request);
    }

    public synchronized Result<RequestView> findRequest(RequestId requestId) {
        if (requestId == null) {
            return Result.failure(FailureCode.MALFORMED_INPUT, "Request ID is required");
        }
        RequestView request = requestsById.get(requestId);
        return request == null
            ? Result.failure(FailureCode.REQUEST_NOT_FOUND, "Request not found")
            : Result.success(request);
    }

    public synchronized Result<List<RequestView>> matchingRequests(MemberId helperId) {
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
            .filter(request -> findMember.apply(request.requesterId()).filter(EngagementManager::isActive).isPresent())
            .toList();
        return Result.success(matches);
    }

    public synchronized Result<ProposalView> submitProposal(MemberId helperId,
            RequestId requestId, ProposalId proposalId, LocalDate proposedOn) {
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
            return Result.failure(FailureCode.SUSPENDED_MEMBER, "Both participants must be active");
        }
        if (request.status() != RequestStatus.OPEN) {
            return Result.failure(FailureCode.INVALID_STATE, "Request is closed");
        }
        if (helperId.equals(request.requesterId())) {
            return Result.failure(FailureCode.BUSINESS_REJECTION, "Helper must differ from requester");
        }
        if (!offersCategory(helper, request.categoryId())) {
            return Result.failure(FailureCode.BUSINESS_REJECTION, "Helper must offer the request category");
        }
        if (!new RequestWindow(request.windowStart(), request.windowEnd()).contains(proposedOn)
                || proposedOn.isBefore(simulatedDate.get())) {
            return Result.failure(FailureCode.BUSINESS_REJECTION, "Proposed date must be within the window and on or after today");
        }
        if (proposalsById.containsKey(proposalId)) {
            return Result.failure(FailureCode.INVALID_STATE, "Proposal ID already exists");
        }
        boolean alreadyPending = proposalsById.values().stream()
            .anyMatch(proposal -> proposal.status() == ProposalStatus.PENDING
                && proposal.requestId().equals(requestId) && proposal.helperId().equals(helperId));
        if (alreadyPending) {
            return Result.failure(FailureCode.DUPLICATE_PROPOSAL,
                "A pending proposal already exists for this helper and request");
        }

        ProposalView proposal = new ProposalView(proposalId, requestId, helperId, proposedOn, ProposalStatus.PENDING);
        proposalsById.put(proposalId, proposal);
        return Result.success(proposal);
    }

    public synchronized Result<ProposalView> findProposal(MemberId requesterId, ProposalId proposalId) {
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
        if (!requestsById.get(proposal.requestId()).requesterId().equals(requesterId)) {
            return Result.failure(FailureCode.NOT_PARTICIPANT, "Requester does not own the proposal's request");
        }
        return Result.success(proposal);
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

    public synchronized RequestCreationResult createRequest(
        ie.ucd.meitheal.engagement.RequestId requestId,
        ie.ucd.meitheal.engagement.port.MemberId requesterId,
        ie.ucd.meitheal.engagement.port.CategoryId categoryId,
        RequestDuration duration,
        RequestWindow window
    ) {
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(window, "window must not be null");
        return createRequest(requestId, requesterId, categoryId, duration.minutes(),
            window.earliestDate(), window.latestDate());
    }

    public synchronized RequestCreationResult createRequest(
        ie.ucd.meitheal.engagement.RequestId requestId,
        ie.ucd.meitheal.engagement.port.MemberId requesterId,
        ie.ucd.meitheal.engagement.port.CategoryId categoryId,
        int durationMinutes,
        LocalDate earliestDate,
        LocalDate latestDate
    ) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(requesterId, "requesterId must not be null");
        Objects.requireNonNull(categoryId, "categoryId must not be null");
        Result<RequestView> result = createRequest(new MemberId(requesterId.value().toString()),
            sharedId(requestId), new CategoryId(categoryId.value()), durationMinutes, earliestDate, latestDate);
        if (result.isSuccess()) {
            return new RequestCreationResult.Success(localRequest(result.value().orElseThrow()).orElseThrow());
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

    public synchronized Optional<Request> findRequestById(ie.ucd.meitheal.engagement.RequestId requestId) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        return findRequest(sharedId(requestId)).value().flatMap(EngagementManager::localRequest);
    }

    /** The provisional view can represent requests with UUID request and requester IDs. */
    public synchronized List<Request> getRequests() {
        return requestsById.values().stream().map(EngagementManager::localRequest).flatMap(Optional::stream).toList();
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
