package ie.ucd.meitheal.engagement;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.port.MemberManagerPort;
import ie.ucd.meitheal.shared.port.MemberManagerPort.MemberProfile;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.EngagementView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngagementManagerCancellationTest {
    private static final MemberId REQUESTER = new MemberId("100001");
    private static final MemberId HELPER = new MemberId("100002");
    private static final MemberId STRANGER = new MemberId("100003");
    private static final CategoryId CATEGORY = new CategoryId("gardening");
    private static final RequestId REQUEST = new RequestId("r1");
    private static final ProposalId PROPOSAL = new ProposalId("p1");
    private static final EngagementId ENGAGEMENT = new EngagementId("e1");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 4);
    private static final LocalDate SCHEDULED = TODAY.plusDays(3);

    @Test
    void eitherParticipantCanCancelEarlyOrLateAndHistoryIsPreserved() {
        for (MemberId actor : List.of(REQUESTER, HELPER)) {
            Fixture earlyFixture = new Fixture();
            earlyFixture.select();
            EngagementView earlyBefore = success(earlyFixture.manager.withConfirmation(HELPER, ENGAGEMENT,
                SCHEDULED, Result::success));
            EngagementView early = success(earlyFixture.manager.withCancellation(actor, ENGAGEMENT,
                SCHEDULED.minusDays(2), Result::success));
            assertCancellation(earlyBefore, early, actor, SCHEDULED.minusDays(2), false);

            Fixture lateFixture = new Fixture();
            lateFixture.select();
            EngagementView lateBefore = success(lateFixture.manager.findEngagement(actor, ENGAGEMENT));
            EngagementView late = success(lateFixture.manager.withCancellation(actor, ENGAGEMENT,
                SCHEDULED.minusDays(1), Result::success));
            assertCancellation(lateBefore, late, actor, SCHEDULED.minusDays(1), true);
        }
    }

    @Test
    void exactScheduledDateAndLaterAreClosed() {
        Fixture fixture = new Fixture();
        fixture.select();
        fail(FailureCode.CANCELLATION_CLOSED, fixture.manager.withCancellation(REQUESTER, ENGAGEMENT,
            SCHEDULED, ignored -> { throw new AssertionError("Closed cancellation callback invoked"); }));
        fail(FailureCode.CANCELLATION_CLOSED, fixture.manager.withCancellation(HELPER, ENGAGEMENT,
            SCHEDULED.plusDays(1), ignored -> { throw new AssertionError("Closed cancellation callback invoked"); }));
        assertEquals(EngagementStatus.AGREED, success(fixture.manager.findEngagement(REQUESTER,
            ENGAGEMENT)).status());
    }

    @Test
    void validatesInputsEngagementParticipationAndState() {
        Fixture fixture = new Fixture();
        fixture.select();
        fail(FailureCode.MALFORMED_INPUT, fixture.manager.withCancellation(null, ENGAGEMENT, TODAY,
            ignored -> { throw new AssertionError("Invalid cancellation callback invoked"); }));
        fail(FailureCode.MALFORMED_INPUT, fixture.manager.withCancellation(REQUESTER, null, TODAY,
            ignored -> { throw new AssertionError("Invalid cancellation callback invoked"); }));
        fail(FailureCode.MALFORMED_INPUT, fixture.manager.withCancellation(REQUESTER, ENGAGEMENT, null,
            ignored -> { throw new AssertionError("Invalid cancellation callback invoked"); }));
        fail(FailureCode.MALFORMED_INPUT, fixture.manager.withCancellation(REQUESTER, ENGAGEMENT, TODAY,
            null));
        fail(FailureCode.ENGAGEMENT_NOT_FOUND, fixture.manager.withCancellation(REQUESTER,
            new EngagementId("missing"), TODAY,
            ignored -> { throw new AssertionError("Invalid cancellation callback invoked"); }));
        fail(FailureCode.NOT_PARTICIPANT, fixture.manager.withCancellation(STRANGER, ENGAGEMENT, TODAY,
            ignored -> { throw new AssertionError("Invalid cancellation callback invoked"); }));

        success(fixture.manager.withCancellation(REQUESTER, ENGAGEMENT, TODAY, Result::success));
        fail(FailureCode.INVALID_STATE, fixture.manager.withCancellation(REQUESTER, ENGAGEMENT, TODAY,
            ignored -> { throw new AssertionError("Invalid cancellation callback invoked"); }));

        Fixture completed = new Fixture();
        completed.select();
        success(completed.manager.withConfirmation(REQUESTER, ENGAGEMENT, SCHEDULED, Result::success));
        success(completed.manager.withConfirmation(HELPER, ENGAGEMENT, SCHEDULED, Result::success));
        fail(FailureCode.INVALID_STATE, completed.manager.withCancellation(HELPER, ENGAGEMENT, TODAY,
            ignored -> { throw new AssertionError("Invalid cancellation callback invoked"); }));
    }

    @Test
    void suspendedParticipantMayCancelAndDateLimitsAreSafe() {
        for (MemberId actor : List.of(REQUESTER, HELPER)) {
            Fixture fixture = new Fixture();
            fixture.select();
            fixture.status.put(actor, MemberStatus.SUSPENDED);
            EngagementView cancelled = success(fixture.manager.withCancellation(actor, ENGAGEMENT,
                TODAY, Result::success));
            assertEquals(actor, cancelled.cancellationActor());
        }

        Fixture minDate = new Fixture();
        minDate.select(LocalDate.MIN, LocalDate.MIN.plusDays(3));
        EngagementView early = success(minDate.manager.withCancellation(HELPER, ENGAGEMENT,
            LocalDate.MIN, Result::success));
        assertFalse(early.lateCancellation());

        Fixture lateDate = new Fixture();
        lateDate.select(LocalDate.MAX.minusDays(2), LocalDate.MAX);
        EngagementView late = success(lateDate.manager.withCancellation(REQUESTER, ENGAGEMENT,
            LocalDate.MAX.minusDays(1), Result::success));
        assertTrue(late.lateCancellation());

        Fixture maxDate = new Fixture();
        maxDate.select(LocalDate.MAX.minusDays(1), LocalDate.MAX);
        fail(FailureCode.CANCELLATION_CLOSED, maxDate.manager.withCancellation(REQUESTER, ENGAGEMENT,
            LocalDate.MAX, ignored -> { throw new AssertionError("Closed cancellation callback invoked"); }));
    }

    @Test
    void callbackRunsOnceUnderSharedLockOnCandidateWhileOriginalIsVisible() {
        Fixture fixture = new Fixture();
        fixture.select();
        EngagementView original = success(fixture.manager.findEngagement(HELPER, ENGAGEMENT));
        AtomicInteger calls = new AtomicInteger();
        EngagementView[] observed = new EngagementView[1];
        Result<EngagementView> returned = fixture.manager.withCancellation(REQUESTER, ENGAGEMENT,
            SCHEDULED.minusDays(1), candidate -> {
                calls.incrementAndGet();
                assertTrue(Thread.holdsLock(fixture.monitor));
                assertFalse(Thread.holdsLock(fixture.manager));
                assertEquals(EngagementStatus.CANCELLED, candidate.status());
                assertSame(original, success(fixture.manager.findEngagement(REQUESTER, ENGAGEMENT)));
                observed[0] = candidate;
                return Result.success(candidate);
            });
        assertEquals(1, calls.get());
        assertSame(observed[0], success(returned));
        assertSame(observed[0], success(fixture.manager.findEngagement(HELPER, ENGAGEMENT)));
    }

    @Test
    void refusalAndThrowPreserveStateAndAllowSameActorRetry() {
        Fixture fixture = new Fixture();
        fixture.select();
        EngagementView original = success(fixture.manager.findEngagement(REQUESTER, ENGAGEMENT));
        Result<EngagementView> refusal = Result.failure(FailureCode.BUSINESS_REJECTION, "release refused");
        Result<EngagementView> returned = fixture.manager.withCancellation(HELPER, ENGAGEMENT, TODAY,
            ignored -> refusal);
        assertSame(refusal, returned);
        assertSame(original, success(fixture.manager.findEngagement(REQUESTER, ENGAGEMENT)));
        assertThrows(IllegalStateException.class, () -> fixture.manager.withCancellation(HELPER,
            ENGAGEMENT, TODAY, ignored -> { throw new IllegalStateException("release failed"); }));
        assertSame(original, success(fixture.manager.findEngagement(HELPER, ENGAGEMENT)));
        EngagementView cancelled = success(fixture.manager.withCancellation(HELPER, ENGAGEMENT, TODAY,
            Result::success));
        assertEquals(HELPER, cancelled.cancellationActor());
    }

    @Test
    void callbackReentryCannotMutateEngagement() {
        Fixture fixture = new Fixture();
        fixture.select();
        AtomicInteger calls = new AtomicInteger();
        EngagementView cancelled = success(fixture.manager.withCancellation(REQUESTER, ENGAGEMENT, TODAY,
            candidate -> {
                calls.incrementAndGet();
                fail(FailureCode.INVALID_STATE, fixture.manager.withCancellation(HELPER, ENGAGEMENT,
                    TODAY, Result::success));
                fail(FailureCode.INVALID_STATE, fixture.manager.withConfirmation(HELPER, ENGAGEMENT,
                    SCHEDULED, Result::success));
                return Result.success(candidate);
            }));
        assertEquals(EngagementStatus.CANCELLED, cancelled.status());
        assertEquals(1, calls.get());
    }

    private static void assertCancellation(EngagementView before, EngagementView after, MemberId actor,
            LocalDate date, boolean late) {
        assertEquals(EngagementStatus.CANCELLED, after.status());
        assertEquals(before.id(), after.id());
        assertEquals(before.proposalId(), after.proposalId());
        assertEquals(before.requestId(), after.requestId());
        assertEquals(before.requesterId(), after.requesterId());
        assertEquals(before.helperId(), after.helperId());
        assertEquals(before.durationMinutes(), after.durationMinutes());
        assertEquals(before.scheduledDate(), after.scheduledDate());
        assertEquals(before.confirmationActors(), after.confirmationActors());
        assertEquals(actor, after.cancellationActor());
        assertEquals(date, after.cancellationDate());
        assertEquals(late, after.lateCancellation());
    }

    private static EngagementView success(Result<EngagementView> result) {
        assertTrue(result.isSuccess(), () -> "Expected success, got " + result.failureCode());
        return result.value().orElseThrow();
    }

    private static void fail(FailureCode code, Result<?> result) {
        assertEquals(code, result.failureCode().orElseThrow());
    }

    private static final class Fixture {
        private final Map<MemberId, MemberStatus> status = new HashMap<>();
        private final Map<MemberId, Set<CategoryId>> skills = new HashMap<>();
        private final Object monitor = new Object();
        private LocalDate today = TODAY;
        private final EngagementManager manager = new EngagementManager(new Members(), new Catalog(),
            new Clock(), monitor);

        private Fixture() {
            for (MemberId member : List.of(REQUESTER, HELPER, STRANGER)) {
                status.put(member, MemberStatus.ACTIVE);
                skills.put(member, new HashSet<>(Set.of(CATEGORY)));
            }
        }

        private void select() {
            select(TODAY, SCHEDULED);
        }

        private void select(LocalDate today, LocalDate scheduled) {
            this.today = today;
            assertTrue(manager.createRequest(REQUESTER, REQUEST, CATEGORY, 30, today, scheduled)
                .isSuccess());
            assertTrue(manager.submitProposal(HELPER, REQUEST, PROPOSAL, scheduled).isSuccess());
            assertTrue(manager.withSelection(REQUESTER, PROPOSAL, ENGAGEMENT, Result::success).isSuccess());
        }

        private final class Members implements MemberManagerPort {
            @Override public Result<MemberProfile> findById(MemberId id) {
                return status.containsKey(id) ? Result.success(new MemberProfile(id, id.value(),
                    id.value() + "@example.test", status.get(id), skills.get(id)))
                    : Result.failure(FailureCode.MEMBER_NOT_FOUND, "missing");
            }
            @Override public Result<MemberProfile> findByCanonicalEmail(String email) { throw new UnsupportedOperationException(); }
            @Override public Result<Boolean> isEligible(MemberId id) { throw new UnsupportedOperationException(); }
            @Override public long memberCount() { throw new UnsupportedOperationException(); }
            @Override public Result<MemberProfile> offer(MemberId member, CategoryId category) { throw new UnsupportedOperationException(); }
            @Override public Result<MemberProfile> reinstate(MemberId member) { throw new UnsupportedOperationException(); }
        }

        private final class Catalog implements CategoryCatalog {
            @Override public Result<CategoryView> find(CategoryId id) {
                return CATEGORY.equals(id) ? Result.success(new CategoryView(CATEGORY, "Gardening"))
                    : Result.failure(FailureCode.CATEGORY_NOT_FOUND, "missing");
            }
            @Override public List<CategoryView> list() { return List.of(new CategoryView(CATEGORY, "Gardening")); }
        }

        private final class Clock implements SimulatedClock {
            @Override public LocalDate currentDate() { return today; }
            @Override public Result<LocalDate> advanceTo(LocalDate date) { throw new UnsupportedOperationException(); }
        }
    }
}
