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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngagementManagerConfirmationTest {
    private static final MemberId REQUESTER = new MemberId("100001");
    private static final MemberId HELPER = new MemberId("100002");
    private static final MemberId STRANGER = new MemberId("100003");
    private static final CategoryId CATEGORY = new CategoryId("gardening");
    private static final RequestId REQUEST = new RequestId("r1");
    private static final ProposalId PROPOSAL = new ProposalId("p1");
    private static final EngagementId ENGAGEMENT = new EngagementId("e1");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 4);
    private static final LocalDate SCHEDULED = TODAY.plusDays(1);

    @Test
    void firstConfirmationPublishesAgreedWithoutSettlementInEitherOrder() {
        for (MemberId firstActor : List.of(REQUESTER, HELPER)) {
            Fixture fixture = new Fixture();
            fixture.select();
            AtomicInteger invocations = new AtomicInteger();

            EngagementView confirmed = success(fixture.manager.withConfirmation(firstActor, ENGAGEMENT, SCHEDULED,
                candidate -> { invocations.incrementAndGet(); return Result.success(candidate); }));

            assertEquals(EngagementStatus.AGREED, confirmed.status());
            assertEquals(Set.of(firstActor), confirmed.confirmationActors());
            assertEquals(0, invocations.get());
            assertEquals(confirmed, success(fixture.manager.findEngagement(firstActor, ENGAGEMENT)));
        }
    }

    @Test
    void enforcesParticipationDateAndDuplicateRules() {
        Fixture fixture = new Fixture();
        fixture.select();
        fail(FailureCode.ENGAGEMENT_NOT_FOUND, fixture.manager.withConfirmation(REQUESTER,
            new EngagementId("missing"), SCHEDULED, Result::success));
        fail(FailureCode.NOT_PARTICIPANT, fixture.manager.withConfirmation(STRANGER,
            ENGAGEMENT, SCHEDULED, Result::success));
        fail(FailureCode.BUSINESS_REJECTION, fixture.manager.withConfirmation(REQUESTER,
            ENGAGEMENT, SCHEDULED.minusDays(1), Result::success));
        assertTrue(fixture.manager.withConfirmation(REQUESTER, ENGAGEMENT, SCHEDULED,
            Result::success).isSuccess());
        fail(FailureCode.DUPLICATE, fixture.manager.withConfirmation(REQUESTER,
            ENGAGEMENT, SCHEDULED, Result::success));
        assertTrue(fixture.manager.withConfirmation(HELPER, ENGAGEMENT, SCHEDULED,
            Result::success).isSuccess());
        fail(FailureCode.INVALID_STATE, fixture.manager.withConfirmation(HELPER, ENGAGEMENT,
            SCHEDULED, Result::success));
    }

    @Test
    void secondConfirmationCompletesAndSettlementSeesCandidateUnderSharedMonitor() {
        for (MemberId firstActor : List.of(REQUESTER, HELPER)) {
            Fixture fixture = new Fixture();
            fixture.select();
            MemberId secondActor = firstActor.equals(REQUESTER) ? HELPER : REQUESTER;
            EngagementView first = success(fixture.manager.withConfirmation(firstActor, ENGAGEMENT,
                SCHEDULED, Result::success));
            EngagementView[] candidateObserved = new EngagementView[1];

            EngagementView completed = success(fixture.manager.withConfirmation(secondActor, ENGAGEMENT,
                SCHEDULED.plusDays(10), candidate -> {
                    assertTrue(Thread.holdsLock(fixture.monitor));
                    assertFalse(Thread.holdsLock(fixture.manager));
                    assertEquals(EngagementStatus.COMPLETED, candidate.status());
                    assertEquals(Set.of(REQUESTER, HELPER), candidate.confirmationActors());
                    assertEquals(first, success(fixture.manager.findEngagement(firstActor, ENGAGEMENT)));
                    candidateObserved[0] = candidate;
                    fail(FailureCode.INVALID_STATE, fixture.manager.createRequest(STRANGER,
                        new RequestId("callback"), CATEGORY, 20, TODAY, TODAY.plusDays(2)));
                    fail(FailureCode.INVALID_STATE, fixture.manager.withConfirmation(secondActor,
                        ENGAGEMENT, SCHEDULED, Result::success));
                    return Result.success(candidate);
                }));

            assertTrue(completed == candidateObserved[0]);
            assertEquals(EngagementStatus.COMPLETED, success(fixture.manager.findEngagement(secondActor,
                ENGAGEMENT)).status());
        }
    }

    @Test
    void refusalAndThrowKeepOnlyFirstActorAndAllowSameActorRetry() {
        Fixture fixture = new Fixture();
        fixture.select();
        success(fixture.manager.withConfirmation(REQUESTER, ENGAGEMENT, SCHEDULED, Result::success));
        Result<EngagementView> refusal = Result.failure(FailureCode.BUSINESS_REJECTION, "settlement refused");
        Result<EngagementView> returned = fixture.manager.withConfirmation(HELPER, ENGAGEMENT, SCHEDULED,
            ignored -> refusal);
        assertTrue(returned == refusal);
        assertEquals(Set.of(REQUESTER), success(fixture.manager.findEngagement(REQUESTER, ENGAGEMENT))
            .confirmationActors());
        assertThrows(IllegalStateException.class, () -> fixture.manager.withConfirmation(HELPER,
            ENGAGEMENT, SCHEDULED, ignored -> { throw new IllegalStateException("settlement failed"); }));
        assertEquals(Set.of(REQUESTER), success(fixture.manager.findEngagement(REQUESTER, ENGAGEMENT))
            .confirmationActors());
        EngagementView completed = success(fixture.manager.withConfirmation(HELPER, ENGAGEMENT,
            SCHEDULED, Result::success));
        assertEquals(EngagementStatus.COMPLETED, completed.status());
        assertEquals(Set.of(REQUESTER, HELPER), completed.confirmationActors());
    }

    @Test
    void suspensionAfterAgreementDoesNotPreventConfirmationAndNullsAreMalformed() {
        Fixture fixture = new Fixture();
        fixture.select();
        fixture.status.put(REQUESTER, MemberStatus.SUSPENDED);
        fixture.status.put(HELPER, MemberStatus.SUSPENDED);
        assertTrue(fixture.manager.withConfirmation(REQUESTER, ENGAGEMENT, SCHEDULED,
            Result::success).isSuccess());
        assertTrue(fixture.manager.withConfirmation(HELPER, ENGAGEMENT, SCHEDULED,
            Result::success).isSuccess());
        fail(FailureCode.MALFORMED_INPUT,
            fixture.manager.withConfirmation(null, ENGAGEMENT, SCHEDULED, Result::success));
        fail(FailureCode.MALFORMED_INPUT,
            fixture.manager.withConfirmation(REQUESTER, ENGAGEMENT, null, Result::success));
        fail(FailureCode.MALFORMED_INPUT,
            fixture.manager.withConfirmation(REQUESTER, ENGAGEMENT, SCHEDULED, null));
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
        private LocalDate now = TODAY;
        private final EngagementManager manager = new EngagementManager(new Members(), new Catalog(),
            new Clock(), monitor);

        private Fixture() {
            for (MemberId member : List.of(REQUESTER, HELPER, STRANGER)) {
                status.put(member, MemberStatus.ACTIVE);
                skills.put(member, new HashSet<>(Set.of(CATEGORY)));
            }
        }

        private void select() {
            assertTrue(manager.createRequest(REQUESTER, REQUEST, CATEGORY, 30, TODAY, SCHEDULED.plusDays(2))
                .isSuccess());
            assertTrue(manager.submitProposal(HELPER, REQUEST, PROPOSAL, SCHEDULED).isSuccess());
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
            @Override public LocalDate currentDate() { return now; }
            @Override public Result<LocalDate> advanceTo(LocalDate date) { throw new UnsupportedOperationException(); }
        }
    }
}
