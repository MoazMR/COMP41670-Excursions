package ie.ucd.meitheal.app;

import ie.ucd.meitheal.engagement.EngagementManager;
import ie.ucd.meitheal.ledger.LedgerManager;
import ie.ucd.meitheal.membermanager.MemberDirectory;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.RequestView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.membermanager.Member;
import ie.ucd.meitheal.membermanager.MemberManager;
import ie.ucd.meitheal.membermanager.MemberManagerException;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.port.InMemorySimulatedClock;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.EngagementView;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** In-memory implementation of registration backed by the membership and ledger owners. */
public final class InMemoryRegistrationService implements CancellationService {
    private final Object lock;
    private final MemberManager members;
    private final LedgerManager ledger;
    private final RegistrationCoordinator coordinator;

    private final CategoryCatalog catalogue;
    private final MemberDirectory directory;
    private final EngagementManager engagements;
    private final SelectionCoordinator selectionCoordinator;
    private final ConfirmationCoordinator confirmationCoordinator;
    private final CancellationCoordinator cancellationCoordinator;
    private long nextRequest = 1;
    private long nextProposal = 1;

    public static InMemoryRegistrationService create() {
        Object lock = new Object();
        var ids = new AtomicLong(1);
        Supplier<TransactionId> generator = () -> new TransactionId("tx-" + ids.getAndIncrement());
        CategoryCatalog catalogue = StartupCatalogue.load(
                InMemoryRegistrationService.class.getResourceAsStream("/categories.csv"));
        var categoryIds = catalogue.list().stream()
                .map(row -> new ie.ucd.meitheal.membermanager.CategoryId(row.id().value())).toList();
        MemberManager members = new MemberManager(lock, categoryIds);
        SimulatedClock clock = new InMemorySimulatedClock(LocalDate.of(2026, 9, 1));
        LedgerManager ledger = new LedgerManager(lock, generator, clock::currentDate);
        return new InMemoryRegistrationService(lock, members, ledger, clock, generator, catalogue,
                () -> { }, ignored -> { });
    }

    InMemoryRegistrationService(Object lock, MemberManager members, LedgerManager ledger,
            SimulatedClock clock, Supplier<TransactionId> transactionIds, CategoryCatalog catalogue,
            Runnable beforePublication) {
        this(lock, members, ledger, clock, transactionIds, catalogue, beforePublication, ignored -> { });
    }

    InMemoryRegistrationService(Object lock, MemberManager members, LedgerManager ledger,
            SimulatedClock clock, Supplier<TransactionId> transactionIds, CategoryCatalog catalogue,
            Runnable beforePublication, Consumer<EngagementView> beforeSelectionPublication) {
        this(lock, members, ledger, clock, transactionIds, catalogue, beforePublication,
                beforeSelectionPublication, ignored -> { });
    }

    InMemoryRegistrationService(Object lock, MemberManager members, LedgerManager ledger,
            SimulatedClock clock, Supplier<TransactionId> transactionIds, CategoryCatalog catalogue,
            Runnable beforePublication, Consumer<EngagementView> beforeSelectionPublication,
            Consumer<EngagementView> beforeConfirmationPublication) {
        this(lock, members, ledger, clock, transactionIds, catalogue, beforePublication,
                beforeSelectionPublication, beforeConfirmationPublication, ignored -> { });
    }

    InMemoryRegistrationService(Object lock, MemberManager members, LedgerManager ledger,
            SimulatedClock clock, Supplier<TransactionId> transactionIds, CategoryCatalog catalogue,
            Runnable beforePublication, Consumer<EngagementView> beforeSelectionPublication,
            Consumer<EngagementView> beforeConfirmationPublication,
            Consumer<EngagementView> beforeCancellationPublication) {
        this.lock = Objects.requireNonNull(lock, "lock");
        this.members = Objects.requireNonNull(members, "members");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.catalogue = Objects.requireNonNull(catalogue);
        this.directory = new MemberDirectory(members);
        this.engagements = new EngagementManager(directory, catalogue, clock, lock);
        this.selectionCoordinator = new SelectionCoordinator(lock, engagements, ledger, beforeSelectionPublication);
        this.confirmationCoordinator = new ConfirmationCoordinator(lock, clock,
                Objects.requireNonNull(transactionIds, "transactionIds"), engagements::withConfirmation,
                ledger::withSettlement, beforeConfirmationPublication);
        this.cancellationCoordinator = new CancellationCoordinator(lock, clock,
                engagements::withCancellation, ledger::withRelease, beforeCancellationPublication);
        Objects.requireNonNull(beforePublication, "beforePublication");
        RegistrationCoordinator.MembershipRegistration membership = (id, name, email, callback) -> {
            try {
                return members.withRegistration(id, name, email, member ->
                        callback.apply(credits -> toView(member, credits)));
            } catch (MemberManagerException failure) {
                if (failure.code() == MemberManagerException.Code.DUPLICATE_EMAIL) {
                    throw new RegistrationCoordinator.RegistrationRejectedException(
                            FailureCode.DUPLICATE_EMAIL, failure.getMessage());
                }
                if (failure.code() == MemberManagerException.Code.INVALID_NAME
                        || failure.code() == MemberManagerException.Code.INVALID_EMAIL) {
                    throw new RegistrationCoordinator.RegistrationRejectedException(
                            FailureCode.MALFORMED_INPUT, failure.getMessage());
                }
                throw failure;
            }
        };
        RegistrationCoordinator.WelcomeGrant welcomeGrant = (id, transactionId, date, callback) ->
                ledger.withWelcomeGrant(id, transactionId, date, transaction -> {
                    Result<MemberView> candidate = callback.apply(transaction.amount());
                    beforePublication.run();
                    return candidate;
                });
        this.coordinator = new RegistrationCoordinator(this.lock, clock,
                Objects.requireNonNull(transactionIds, "transactionIds"), membership, welcomeGrant);
    }

    @Override
    public Result<MemberView> register(String name, String email) {
        return coordinator.register(name, email);
    }

    @Override
    public Result<MemberView> findMember(MemberId memberId) {
        synchronized (lock) {
            if (memberId == null) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Member ID must not be null");
            }
            return members.findById(memberId).map(member -> Result.success(toView(member,
                    ledger.balanceOf(memberId)))).orElseGet(() -> Result.failure(
                    FailureCode.MEMBER_NOT_FOUND, "Member does not exist"));
        }
    }

    @Override
    public Result<MemberView> findMemberByEmail(String email) {
        synchronized (lock) {
            try {
                return members.findByEmail(email).map(member -> Result.success(toView(member,
                        ledger.balanceOf(member.id())))).orElseGet(() -> Result.failure(
                        FailureCode.MEMBER_NOT_FOUND, "Member does not exist"));
            } catch (MemberManagerException failure) {
                if (failure.code() == MemberManagerException.Code.INVALID_EMAIL) {
                    return Result.failure(FailureCode.MALFORMED_INPUT, failure.getMessage());
                }
                throw failure;
            }
        }
    }

    @Override
    public Result<BalanceView> balance(MemberId memberId) {
        synchronized (lock) {
            if (memberId == null || members.findById(memberId).isEmpty()) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Member does not exist");
            }
            return Result.success(ledger.balanceView(memberId));
        }
    }

    @Override
    public List<CategoryView> categories() {
        return catalogue.list();
    }

    @Override
    public Result<MemberView> offerSkill(MemberId memberId, CategoryId categoryId) {
        synchronized (lock) {
            var offer = directory.offer(memberId, categoryId);
            if (offer.isFailure()) {
                return Result.failure(offer.failureCode().orElseThrow(), offer.detail().orElse(""));
            }
            return findMember(memberId);
        }
    }

    @Override
    public Result<RequestView> createRequest(MemberId requesterId, CategoryId categoryId,
            int durationMinutes, LocalDate windowStart, LocalDate windowEnd) {
        synchronized (lock) {
            var result = engagements.createRequest(requesterId, new RequestId("req-" + nextRequest),
                    categoryId, durationMinutes, windowStart, windowEnd);
            if (result.isSuccess()) nextRequest++;
            return result;
        }
    }

    @Override
    public Result<List<RequestView>> matchingRequests(MemberId helperId) {
        synchronized (lock) {
            return engagements.matchingRequests(helperId);
        }
    }

    @Override
    public Result<ProposalView> submitProposal(MemberId helperId, RequestId requestId, LocalDate proposedDate) {
        synchronized (lock) {
            var result = engagements.submitProposal(helperId, requestId, new ProposalId("prop-" + nextProposal),
                    proposedDate);
            if (result.isSuccess()) nextProposal++;
            return result;
        }
    }

    @Override
    public Result<List<ProposalView>> proposalsForRequest(MemberId requesterId, RequestId requestId) {
        synchronized (lock) {
            return engagements.proposalsForRequest(requesterId, requestId);
        }
    }

    @Override
    public Result<EngagementView> selectProposal(MemberId requesterId, ProposalId proposalId) {
        return selectionCoordinator.select(requesterId, proposalId);
    }

    @Override
    public Result<EngagementView> confirmEngagement(MemberId participantId, EngagementId engagementId) {
        return confirmationCoordinator.confirm(participantId, engagementId);
    }

    @Override
    public Result<EngagementView> cancelEngagement(MemberId participantId, EngagementId engagementId) {
        return cancellationCoordinator.cancel(participantId, engagementId);
    }

    EngagementManager engagementManagerForTests() { return engagements; }

    LedgerManager ledgerManagerForTests() { return ledger; }

    MemberManager memberManagerForTests() { return members; }

    private static MemberView toView(Member member, long ledgerCredits) {
        var skills = member.offeredCategories().stream()
                .map(category -> new CategoryId(category.value()))
                .collect(Collectors.toSet());
        return new MemberView(member.id(), member.name(), member.email().value(),
                MemberStatus.valueOf(member.status().name()),
                skills, ledgerCredits);
    }
}
