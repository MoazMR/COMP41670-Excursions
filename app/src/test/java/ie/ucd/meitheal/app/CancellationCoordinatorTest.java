package ie.ucd.meitheal.app;

import static org.junit.jupiter.api.Assertions.*;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.EngagementView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CancellationCoordinatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private static final LocalDate SCHEDULED_DATE = TODAY.plusDays(7);
    private static final MemberId PARTICIPANT = new MemberId("participant");
    private static final EngagementId ID = new EngagementId("eng-original");
    private static final EngagementId CANDIDATE_ID = new EngagementId("eng-candidate");
    private static final EngagementView ORIGINAL = new EngagementView(ID, new ProposalId("p"),
            new RequestId("r"), PARTICIPANT, new MemberId("helper"), 30, SCHEDULED_DATE,
            EngagementStatus.AGREED, Set.of(PARTICIPANT), null, null, false);

    @Test
    void forwardsInputsReadsDateOnceAndRunsAllBoundariesUnderSharedMonitor() {
        Fixture f = new Fixture();
        Result<EngagementView> result = f.coordinator.cancel(PARTICIPANT, ID);
        assertTrue(result.isSuccess());
        assertSame(f.candidate, result.value().orElseThrow());
        assertEquals(1, f.dateReads);
        assertEquals(1, f.engagementCalls);
        assertSame(PARTICIPANT, f.participant);
        assertSame(ID, f.engagementId);
        assertSame(TODAY, f.date);
        assertEquals(List.of(CANDIDATE_ID), f.releaseIds);
        assertEquals(1, f.releaseIds.size());
        assertEquals(1, f.releases);
        assertEquals(1, f.hooks);
        assertEquals(1, f.publications);
        assertSame(f.candidate, f.published.get(0));
    }

    @Test
    void callbackFailurePreservesOriginalViewAndActiveHoldAndRetryPublishesOnce() {
        Fixture f = new Fixture();
        f.failHook = true;
        assertEquals(FailureCode.ATOMIC_OPERATION_FAILED,
                f.coordinator.cancel(PARTICIPANT, ID).failureCode().orElseThrow());
        assertEquals(1, f.engagementCalls);
        assertEquals(1, f.dateReads);
        assertEquals(1, f.releaseIds.size());
        assertEquals(0, f.releases);
        assertEquals(1, f.hooks);
        assertEquals(0, f.cancellations);
        assertEquals(0, f.publications);
        assertSame(ORIGINAL, f.currentView);
        assertTrue(f.holdActive);

        f.failHook = false;
        Result<EngagementView> retry = f.coordinator.cancel(PARTICIPANT, ID);
        assertTrue(retry.isSuccess());
        assertSame(f.candidate, retry.value().orElseThrow());
        assertEquals(2, f.engagementCalls);
        assertEquals(2, f.dateReads);
        assertEquals(List.of(CANDIDATE_ID, CANDIDATE_ID), f.releaseIds);
        assertEquals(2, f.releaseIds.size());
        assertEquals(1, f.releases);
        assertEquals(2, f.hooks);
        assertEquals(1, f.cancellations);
        assertEquals(1, f.publications);
        assertEquals(List.of(f.candidate), f.published);
        assertSame(f.candidate, f.currentView);
        assertFalse(f.holdActive);
    }

    @Test
    void returnedLedgerBusinessFailurePassesThroughAndRetrySucceeds() {
        Fixture f = new Fixture();
        f.releaseFailure = true;
        Result<EngagementView> result = f.coordinator.cancel(PARTICIPANT, ID);
        assertEquals(FailureCode.INVALID_STATE, result.failureCode().orElseThrow());
        assertSame(f.returnedReleaseFailure, result);
        assertEquals(1, f.engagementCalls);
        assertEquals(1, f.dateReads);
        assertEquals(1, f.releaseIds.size());
        assertEquals(0, f.releases);
        assertEquals(0, f.hooks);
        assertEquals(0, f.cancellations);
        assertEquals(0, f.publications);
        assertSame(ORIGINAL, f.currentView);
        assertTrue(f.holdActive);

        f.releaseFailure = false;
        Result<EngagementView> retry = f.coordinator.cancel(PARTICIPANT, ID);
        assertTrue(retry.isSuccess());
        assertSame(f.candidate, retry.value().orElseThrow());
        assertEquals(2, f.engagementCalls);
        assertEquals(2, f.dateReads);
        assertEquals(List.of(CANDIDATE_ID, CANDIDATE_ID), f.releaseIds);
        assertEquals(2, f.releaseIds.size());
        assertEquals(1, f.releases);
        assertEquals(1, f.hooks);
        assertEquals(1, f.cancellations);
        assertEquals(1, f.publications);
        assertEquals(List.of(f.candidate), f.published);
    }

    @Test
    void engagementFailurePassesThroughWithoutReleaseOrHook() {
        Fixture f = new Fixture();
        f.engagementFailure = Result.failure(FailureCode.CANCELLATION_CLOSED, "closed");
        assertSame(f.engagementFailure, f.coordinator.cancel(PARTICIPANT, ID));
        assertEquals(1, f.engagementCalls);
        assertEquals(1, f.dateReads);
        assertEquals(0, f.releaseIds.size());
        assertEquals(0, f.releases);
        assertEquals(0, f.hooks);
        assertEquals(0, f.cancellations);
        assertEquals(0, f.publications);
        assertSame(ORIGINAL, f.currentView);
        assertTrue(f.holdActive);
    }

    private static final class Fixture {
        final Object lock = new Object();
        int dateReads;
        int engagementCalls;
        int cancellations;
        int releases;
        int hooks;
        int publications;
        boolean failHook;
        boolean holdActive = true;
        MemberId participant;
        EngagementId engagementId;
        LocalDate date;
        Result<?> returnedReleaseFailure;
        Result<EngagementView> engagementFailure;
        final EngagementView candidate = new EngagementView(CANDIDATE_ID, new ProposalId("p"),
                new RequestId("r"), PARTICIPANT, new MemberId("helper"), 30, SCHEDULED_DATE,
                EngagementStatus.CANCELLED, Set.of(), PARTICIPANT, TODAY, false);
        EngagementView currentView = ORIGINAL;
        final List<EngagementId> releaseIds = new ArrayList<>();
        final List<EngagementView> published = new ArrayList<>();
        final SimulatedClock clock = new SimulatedClock() {
            @Override public LocalDate currentDate() {
                assertTrue(Thread.holdsLock(lock));
                dateReads++;
                return TODAY;
            }
            @Override public Result<LocalDate> advanceTo(LocalDate next) { return Result.success(next); }
        };
        final CancellationCoordinator coordinator = new CancellationCoordinator(lock, clock,
                this::withCancellation, this::withRelease, this::beforePublication);

        Result<EngagementView> withCancellation(MemberId actor, EngagementId id, LocalDate today,
                Function<EngagementView, Result<EngagementView>> callback) {
            assertTrue(Thread.holdsLock(lock));
            engagementCalls++;
            participant = actor;
            engagementId = id;
            date = today;
            if (engagementFailure != null) return engagementFailure;
            Result<EngagementView> result = callback.apply(candidate);
            if (result.isSuccess()) {
                currentView = result.value().orElseThrow();
                cancellations++;
                publications++;
                published.add(currentView);
            }
            return result;
        }

        <T> Result<T> withRelease(EngagementId id, Supplier<Result<T>> callback) {
            assertTrue(Thread.holdsLock(lock));
            releaseIds.add(id);
            if (releaseFailure) {
                Result<T> typedFailure = Result.failure(FailureCode.INVALID_STATE, "refused");
                returnedReleaseFailure = typedFailure;
                return typedFailure;
            }
            Result<T> result = callback.get();
            if (result.isSuccess()) {
                releases++;
                holdActive = false;
            }
            return result;
        }

        boolean releaseFailure;

        void beforePublication(EngagementView view) {
            assertTrue(Thread.holdsLock(lock));
            hooks++;
            assertSame(candidate, view);
            assertSame(ORIGINAL, currentView);
            assertTrue(holdActive);
            if (failHook) throw new IllegalStateException("injected hook failure");
        }
    }
}
