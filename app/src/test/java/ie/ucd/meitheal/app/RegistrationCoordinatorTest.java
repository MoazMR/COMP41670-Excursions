package ie.ucd.meitheal.app;

import static org.junit.jupiter.api.Assertions.*;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.port.InMemorySimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.MemberView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegistrationCoordinatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    @Test
    void successfulRegistrationUsesPreparedNormalizedMemberAndMetadata() {
        Object lock = new Object();
        Fixture f = new Fixture(lock);
        f.projectedName = "Normalized Name";
        f.projectedEmail = "normalized@example.test";
        Result<MemberView> result = f.coordinator.register(" raw ", "RAW@example.test");
        assertTrue(result.isSuccess());
        assertEquals("Normalized Name", result.value().orElseThrow().name());
        assertEquals("normalized@example.test", result.value().orElseThrow().email());
        assertEquals(120, result.value().orElseThrow().ledgerCredits());
        assertEquals(new TransactionId("tx-1"), f.transactionSeen);
        assertEquals(TODAY, f.dateSeen);
        assertEquals(List.of("100001"), f.committedMembers);
        assertEquals(1, f.committedGrants);
    }

    @Test
    void failureAfterBothPreparationsLeavesIdAvailableForRetry() {
        Fixture f = new Fixture(new Object());
        f.failAfterBothPrepared = true;
        assertEquals(FailureCode.ATOMIC_OPERATION_FAILED,
                f.coordinator.register("A", "a@example.test").failureCode().orElseThrow());
        assertTrue(f.memberCandidatePrepared);
        assertTrue(f.grantCandidatePrepared);
        assertTrue(f.committedMembers.isEmpty());
        assertEquals(0, f.committedGrants);
        f.failAfterBothPrepared = false;
        assertEquals("100001", f.coordinator.register("A", "a@example.test")
                .value().orElseThrow().id().value());
    }

    @Test
    void typedRejectionsArePreservedAndOtherFailureResultsBecomeAtomic() {
        Fixture f = new Fixture(new Object());
        f.reject = FailureCode.DUPLICATE_EMAIL;
        assertEquals(FailureCode.DUPLICATE_EMAIL,
                f.coordinator.register("A", "a@example.test").failureCode().orElseThrow());
        f.reject = FailureCode.MALFORMED_INPUT;
        assertEquals(FailureCode.MALFORMED_INPUT,
                f.coordinator.register("", "a@example.test").failureCode().orElseThrow());
        f.reject = null;
        f.ledgerRefusal = true;
        assertEquals(FailureCode.ATOMIC_OPERATION_FAILED,
                f.coordinator.register("A", "a@example.test").failureCode().orElseThrow());
        assertTrue(f.callbackAborted);
        assertTrue(f.committedMembers.isEmpty());
        assertEquals(0, f.committedGrants);
    }

    @Test
    void lockIsHeldThroughBothPreparationsAndReentrantCallCannotAllocateTwice() {
        Object lock = new Object();
        Fixture f = new Fixture(lock);
        f.duringLedger = () -> {
            assertTrue(Thread.holdsLock(lock));
            assertEquals(FailureCode.ATOMIC_OPERATION_FAILED,
                    f.coordinator.register("Nested", "nested@example.test")
                            .failureCode().orElseThrow());
        };
        assertEquals("100001", f.coordinator.register("A", "a@example.test")
                .value().orElseThrow().id().value());
        assertEquals(List.of("100001"), f.committedMembers);
    }

    @Test
    void transactionAndDateAreReadOncePerAttemptAndNextIdAdvancesOnlyOnSuccess() {
        Fixture f = new Fixture(new Object());
        f.ledgerRefusal = true;
        f.coordinator.register("A", "a@example.test");
        f.ledgerRefusal = false;
        assertEquals("100001", f.coordinator.register("A", "a@example.test")
                .value().orElseThrow().id().value());
        assertEquals("100002", f.coordinator.register("B", "b@example.test")
                .value().orElseThrow().id().value());
        assertEquals(3, f.txCalls);
        assertEquals(3, f.dateReads);
    }

    @Test
    void finalMemberIdCanBeUsedThenSequenceIsExhausted() {
        Fixture f = new Fixture(new Object(), 999999);
        f.failAfterBothPrepared = true;
        assertEquals(FailureCode.ATOMIC_OPERATION_FAILED,
                f.coordinator.register("Last", "last@example.test").failureCode().orElseThrow());
        assertTrue(f.committedMembers.isEmpty());
        assertEquals(0, f.committedGrants);
        f.failAfterBothPrepared = false;
        assertEquals("999999", f.coordinator.register("Last", "last@example.test")
                .value().orElseThrow().id().value());
        assertEquals(FailureCode.LIMIT_EXCEEDED,
                f.coordinator.register("Next", "next@example.test").failureCode().orElseThrow());
        assertEquals(2, f.txCalls);
        assertEquals(2, f.dateReads);
    }

    private static final class Fixture {
        final List<String> committedMembers = new ArrayList<>();
        TransactionId transactionSeen;
        LocalDate dateSeen;
        int txCalls;
        int dateReads;
        boolean callbackAborted;
        final InMemorySimulatedClock rawClock = new InMemorySimulatedClock(TODAY);
        final Object lock;
        RegistrationCoordinator coordinator;
        String projectedName = "A";
        String projectedEmail = "a@example.test";
        FailureCode reject;
        boolean ledgerRefusal;
        boolean failAfterBothPrepared;
        boolean memberCandidatePrepared;
        boolean grantCandidatePrepared;
        Runnable duringLedger = () -> {};
        int committedGrants;

        Fixture(Object lock) { this(lock, 100001); }
        Fixture(Object lock, long initialSequence) {
            this.lock = lock;
            coordinator = new RegistrationCoordinator(lock,
                    new ie.ucd.meitheal.shared.port.SimulatedClock() {
                        @Override public LocalDate currentDate() { dateReads++; return rawClock.currentDate(); }
                        @Override public Result<LocalDate> advanceTo(LocalDate d) { return rawClock.advanceTo(d); }
                    }, () -> { return new TransactionId("tx-" + ++txCalls); },
                    (id, name, email, callback) -> {
                        assertTrue(Thread.holdsLock(lock));
                        if (reject != null) throw new RegistrationCoordinator.RegistrationRejectedException(reject, "rejected");
                        memberCandidatePrepared = true;
                        Result<MemberView> prepared;
                        try {
                            prepared = callback.apply(credits -> {
                                MemberView member = new MemberView(id, projectedName, projectedEmail,
                                        MemberStatus.ACTIVE, Set.of(), credits);
                                if (failAfterBothPrepared) {
                                    throw new IllegalStateException("injected after both preparations");
                                }
                                return member;
                            });
                        } catch (RuntimeException failure) {
                            callbackAborted = true;
                            throw failure;
                        }
                        if (prepared.isSuccess()) committedMembers.add(id.value());
                        return prepared;
                    },
                    (id, tx, date, callback) -> {
                        assertTrue(Thread.holdsLock(lock));
                        duringLedger.run();
                        transactionSeen = tx;
                        dateSeen = date;
                        if (ledgerRefusal) return Result.failure(FailureCode.BUSINESS_REJECTION, "refused");
                        grantCandidatePrepared = true;
                        Result<MemberView> prepared = callback.apply(120);
                        if (prepared.isSuccess()) committedGrants++;
                        return prepared;
                    }, initialSequence);
        }
    }
}
