package ie.ucd.meitheal.app;

import static org.junit.jupiter.api.Assertions.*;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.EngagementView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConfirmationCoordinatorTest {
    private static final LocalDate DAY_ONE = LocalDate.of(2026, 11, 21);
    private static final LocalDate DAY_TWO = DAY_ONE.plusDays(1);
    private static final MemberId REQUESTER = new MemberId("requester");
    private static final MemberId HELPER = new MemberId("helper");
    private static final EngagementId ID = new EngagementId("eng-1");

    @Test
    void eitherActorOrderSettlesExactlyOnceAndCompletedConfirmationIsInvalid() {
        for (boolean requesterFirst : List.of(true, false)) {
            Fixture f = new Fixture();
            MemberId first = requesterFirst ? REQUESTER : HELPER;
            MemberId second = requesterFirst ? HELPER : REQUESTER;
            f.expectedFirstActor = first;
            EngagementView agreed = f.coordinator.confirm(first, ID).value().orElseThrow();
            assertEquals(EngagementStatus.AGREED, agreed.status());
            assertEquals(Set.of(first), agreed.confirmationActors());
            assertEquals(List.of(first), f.engagement.actors);
            assertTrue(f.ledger.dates.isEmpty());
            assertEquals(0, f.ledger.postings.size());
            assertTrue(f.ledger.requestedIds.isEmpty());
            assertEquals(0, f.hookCount);
            EngagementView completed = f.coordinator.confirm(second, ID).value().orElseThrow();
            assertEquals(EngagementStatus.COMPLETED, completed.status());
            assertEquals(Set.of(REQUESTER, HELPER), completed.confirmationActors());
            assertEquals(Set.of(REQUESTER, HELPER), Set.copyOf(f.engagement.actors));
            assertEquals(2, f.engagement.actors.size());
            assertEquals(1, f.ledger.postings.size());
            assertEquals(FailureCode.INVALID_STATE,
                    f.coordinator.confirm(second, ID).failureCode().orElseThrow());
            assertEquals(1, f.ledger.postings.size());
        }
    }

    @Test
    void dateIsReadOncePerCommandInsideMonitorAndSharedByBothOwners() {
        Fixture f = new Fixture();
        assertTrue(f.coordinator.confirm(REQUESTER, ID).isSuccess());
        f.date = DAY_TWO;
        assertTrue(f.coordinator.confirm(HELPER, ID).isSuccess());
        assertEquals(2, f.dateReads);
        assertEquals(List.of(DAY_ONE, DAY_TWO), f.engagement.dates);
        assertEquals(List.of(DAY_TWO), f.ledger.dates);
    }

    @Test
    void failedSettlementRollsBackEngagementAndLedgerThenRetryUsesFreshIdsInOrder() {
        Fixture f = new Fixture();
        f.coordinator.confirm(REQUESTER, ID);
        f.failCallback = true;
        assertEquals(FailureCode.ATOMIC_OPERATION_FAILED,
                f.coordinator.confirm(HELPER, ID).failureCode().orElseThrow());
        assertEquals(List.of(REQUESTER), f.engagement.actors);
        assertTrue(f.ledger.postings.isEmpty());
        f.failCallback = false;
        assertTrue(f.coordinator.confirm(HELPER, ID).isSuccess());
        assertEquals(List.of("tx-1", "tx-2", "tx-3", "tx-4"), f.ledger.requestedIds);
        assertEquals(List.of(REQUESTER, HELPER), f.engagement.actors);
        assertEquals(1, f.ledger.postings.size());
        assertEquals(List.of(List.of("tx-3", "tx-4")), f.ledger.postings);
    }

    @Test
    void rejectionIsPreservedAndMissingHoldAllocatesNoIds() {
        Fixture f = new Fixture();
        f.engagement.actors.add(REQUESTER);
        f.ledger.missingHold = true;
        assertEquals(FailureCode.INVALID_STATE,
                f.coordinator.confirm(HELPER, ID).failureCode().orElseThrow());
        assertEquals(List.of(REQUESTER), f.engagement.actors);
        assertTrue(f.ledger.requestedIds.isEmpty());
        assertTrue(f.ledger.postings.isEmpty());
    }

    @Test
    void reentrantConfirmationRunsInsideTheRealSettlementCallbackAndIsRejected() {
        Fixture f = new Fixture();
        f.engagement.actors.add(REQUESTER);
        f.duringCallback = () -> f.reentrant = f.coordinator.confirm(HELPER, ID);
        assertTrue(f.coordinator.confirm(HELPER, ID).isSuccess());
        assertEquals(FailureCode.ATOMIC_OPERATION_FAILED, f.reentrant.failureCode().orElseThrow());
        assertEquals(1, f.hookCount);
    }

    private static final class Fixture {
        final Object lock = new Object();
        LocalDate date = DAY_ONE;
        int dateReads;
        final SimulatedClock clock = new SimulatedClock() {
            @Override public LocalDate currentDate() {
                assertTrue(Thread.holdsLock(lock));
                dateReads++;
                return date;
            }
            @Override public Result<LocalDate> advanceTo(LocalDate next) { return Result.success(next); }
        };
        final AtomicInteger sequence = new AtomicInteger();
        final NarrowEngagement engagement = new NarrowEngagement(lock);
        final ContractLedger ledger = new ContractLedger(lock);
        MemberId expectedFirstActor = REQUESTER;
        boolean failCallback;
        int hookCount;
        Runnable duringCallback = () -> { };
        final ConfirmationCoordinator coordinator = new ConfirmationCoordinator(lock, clock,
                () -> new TransactionId("tx-" + sequence.incrementAndGet()), engagement::confirm,
                ledger::settle, this::beforePublication);
        Result<EngagementView> reentrant;

        void beforePublication(EngagementView candidate) {
            assertTrue(Thread.holdsLock(lock));
            hookCount++;
            assertEquals(EngagementStatus.COMPLETED, candidate.status());
            assertEquals(Set.of(REQUESTER, HELPER), candidate.confirmationActors());
            assertEquals(List.of(expectedFirstActor), engagement.actors);
            assertTrue(ledger.postings.isEmpty());
            if (failCallback) throw new IllegalStateException("injected callback failure");
            duringCallback.run();
        }
    }

    /** Models only the confirmation transitions and callback rollback needed by this contract. */
    private static final class NarrowEngagement {
        final Object lock;
        final List<MemberId> actors = new ArrayList<>();
        final List<LocalDate> dates = new ArrayList<>();
        boolean callbackActive;

        NarrowEngagement(Object lock) { this.lock = lock; }

        Result<EngagementView> confirm(MemberId actor, EngagementId engagementId, LocalDate date,
                java.util.function.Function<EngagementView, Result<EngagementView>> settlement) {
            assertTrue(Thread.holdsLock(lock));
            assertEquals(ID, engagementId);
            if (callbackActive) return Result.failure(FailureCode.INVALID_STATE, "callback active");
            if (!actor.equals(REQUESTER) && !actor.equals(HELPER)) {
                return Result.failure(FailureCode.NOT_PARTICIPANT, "not a participant");
            }
            dates.add(date);
            if (actors.size() == 2) return Result.failure(FailureCode.INVALID_STATE, "already completed");
            if (actors.contains(actor)) return Result.failure(FailureCode.DUPLICATE, "already confirmed");
            if (actors.isEmpty()) {
                actors.add(actor);
                return Result.success(view(EngagementStatus.AGREED, actors));
            }
            List<MemberId> proposed = List.of(REQUESTER, HELPER);
            EngagementView candidate = view(EngagementStatus.COMPLETED, proposed);
            callbackActive = true;
            try {
                Result<EngagementView> result = settlement.apply(candidate);
                if (result.isSuccess()) {
                    actors.clear();
                    actors.addAll(proposed);
                }
                return result;
            } finally {
                callbackActive = false;
            }
        }
    }

    /** Commits its fake posting only after callback success; failure and rejection preserve state. */
    private static final class ContractLedger {
        final Object lock;
        final List<List<String>> postings = new ArrayList<>();
        final List<LocalDate> dates = new ArrayList<>();
        final List<String> requestedIds = new ArrayList<>();
        boolean missingHold;

        ContractLedger(Object lock) { this.lock = lock; }

        Result<EngagementView> settle(EngagementId engagementId, LocalDate date,
                java.util.function.Supplier<TransactionId> freshId,
                java.util.function.Supplier<Result<EngagementView>> callback) {
            assertTrue(Thread.holdsLock(lock));
            assertEquals(ID, engagementId);
            dates.add(date);
            if (missingHold) return Result.failure(FailureCode.INVALID_STATE, "hold missing");
            String first = freshId.get().value();
            requestedIds.add(first);
            String second = freshId.get().value();
            requestedIds.add(second);
            Result<EngagementView> candidate = callback.get();
            if (candidate.isSuccess()) {
                postings.add(List.of(first, second));
            }
            return candidate;
        }
    }

    private static EngagementView view(EngagementStatus status, List<MemberId> actors) {
        return new EngagementView(ID, new ProposalId("prop"), new RequestId("req"), REQUESTER, HELPER,
                60, DAY_ONE, status, Set.copyOf(actors), null, null, false);
    }
}
