package ie.ucd.meitheal.engagement;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.model.RequestStatus;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.port.MemberManagerPort;
import ie.ucd.meitheal.shared.port.MemberManagerPort.MemberProfile;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.RequestView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProposalSelectionTest {
    private static final MemberId OWNER_R1 = new MemberId("100001");
    private static final MemberId HELPER = new MemberId("100002");
    private static final MemberId OWNER_R2 = new MemberId("100003");
    private static final MemberId STRANGER = new MemberId("100004");
    private static final CategoryId GARDENING = new CategoryId("gardening");
    private static final RequestId REQUEST_R1 = new RequestId("r1");
    private static final RequestId REQUEST_R2 = new RequestId("r2");
    private static final ProposalId PROPOSAL_R1_HELPER = new ProposalId("p1");
    private static final ProposalId PROPOSAL_R1_OTHER = new ProposalId("p2");
    private static final ProposalId PROPOSAL_R2_HELPER = new ProposalId("p3");
    private static final EngagementId ENGAGEMENT_ONE = new EngagementId("e1");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 4);

    @Test
    void publishesSelectionAndPreservesOtherRequestAndLimitsHistoryToActors() {
        Fixture fixture = new Fixture();
        fixture.seed();

        Result<String> selected = fixture.manager.withSelection(OWNER_R1, PROPOSAL_R1_HELPER, ENGAGEMENT_ONE, engagement -> {
            assertEquals(EngagementStatus.AGREED, engagement.status());
            assertEquals(30, engagement.durationMinutes());
            assertEquals(TODAY.plusDays(1), engagement.scheduledDate());
            assertEquals(RequestStatus.OPEN, fixture.manager.findRequest(REQUEST_R1).value().orElseThrow().status());
            assertEquals(ProposalStatus.PENDING,
                fixture.manager.findProposal(OWNER_R1, PROPOSAL_R1_HELPER).value().orElseThrow().status());
            assertFailure(FailureCode.INVALID_STATE,
                fixture.manager.submitProposal(OWNER_R2, REQUEST_R2, new ProposalId("callback"), TODAY));
            return Result.success("hold-prepared");
        });

        assertEquals("hold-prepared", selected.value().orElseThrow());
        assertEquals(RequestStatus.CLOSED, fixture.manager.findRequest(REQUEST_R1).value().orElseThrow().status());
        assertEquals(ProposalStatus.SELECTED,
            fixture.manager.findProposal(OWNER_R1, PROPOSAL_R1_HELPER).value().orElseThrow().status());
        assertEquals(ProposalStatus.REJECTED,
            fixture.manager.findProposal(OWNER_R1, PROPOSAL_R1_OTHER).value().orElseThrow().status());
        assertEquals(ProposalStatus.PENDING,
            fixture.manager.findProposal(OWNER_R2, PROPOSAL_R2_HELPER).value().orElseThrow().status());

        EngagementView engagement = fixture.manager.findEngagement(HELPER, ENGAGEMENT_ONE).value().orElseThrow();
        assertEquals(List.of(engagement), fixture.manager.engagementsForMember(OWNER_R1).value().orElseThrow());
        assertFailure(FailureCode.NOT_PARTICIPANT, fixture.manager.findEngagement(STRANGER, ENGAGEMENT_ONE));
        assertEquals(List.of(), fixture.manager.engagementsForMember(STRANGER).value().orElseThrow());
        assertThrows(UnsupportedOperationException.class,
            () -> fixture.manager.engagementsForMember(OWNER_R1).value().orElseThrow().clear());
        assertFailure(FailureCode.INVALID_STATE,
            fixture.manager.withSelection(OWNER_R1, PROPOSAL_R1_HELPER, new EngagementId("e2"),
                ignored -> Result.success("again")));
    }

    @Test
    void validatesOwnershipSuspensionAndElapsedDate() {
        Fixture fixture = new Fixture();
        fixture.seed();

        assertFailure(FailureCode.NOT_PARTICIPANT,
            fixture.manager.withSelection(STRANGER, PROPOSAL_R1_HELPER, ENGAGEMENT_ONE,
                ignored -> Result.success("unused")));
        for (MemberId member : List.of(OWNER_R1, HELPER)) {
            fixture.status.put(member, MemberStatus.SUSPENDED);
            assertFailure(FailureCode.SUSPENDED_MEMBER,
                fixture.manager.withSelection(OWNER_R1, PROPOSAL_R1_HELPER, ENGAGEMENT_ONE,
                    ignored -> Result.success("unused")));
            fixture.status.put(member, MemberStatus.ACTIVE);
        }

        fixture.now = TODAY.plusDays(2);
        assertFailure(FailureCode.BUSINESS_REJECTION,
            fixture.manager.withSelection(OWNER_R1, PROPOSAL_R1_HELPER, ENGAGEMENT_ONE,
                ignored -> Result.success("unused")));
        assertEquals(RequestStatus.OPEN, fixture.manager.findRequest(REQUEST_R1).value().orElseThrow().status());
        assertTrue(fixture.manager.engagementsForMember(OWNER_R1).value().orElseThrow().isEmpty());
    }

    @Test
    void callbackFailureThrowAndNullLeaveStateUntouchedAndAllowRetry() {
        Fixture fixture = new Fixture();
        fixture.seed();

        assertFailure(FailureCode.BUSINESS_REJECTION,
            fixture.manager.withSelection(OWNER_R1, PROPOSAL_R1_HELPER, ENGAGEMENT_ONE,
                ignored -> Result.failure(FailureCode.BUSINESS_REJECTION, "hold refused")));
        assertThrows(IllegalStateException.class,
            () -> fixture.manager.withSelection(OWNER_R1, PROPOSAL_R1_HELPER, ENGAGEMENT_ONE,
                ignored -> { throw new IllegalStateException("technical failure"); }));
        assertThrows(NullPointerException.class,
            () -> fixture.manager.withSelection(OWNER_R1, PROPOSAL_R1_HELPER, ENGAGEMENT_ONE,
                ignored -> null));
        assertSelectionStateUntouched(fixture);

        assertTrue(fixture.manager.withSelection(OWNER_R1, PROPOSAL_R1_HELPER, ENGAGEMENT_ONE,
            ignored -> Result.success("retry")).isSuccess());
        assertEquals(1, fixture.manager.engagementsForMember(OWNER_R1).value().orElseThrow().size());
    }

    @Test
    void serializesSelectionCallbacksOnTheSharedMonitor() {
        Fixture fixture = new Fixture();
        fixture.seed();

        fixture.manager.withSelection(OWNER_R1, PROPOSAL_R1_HELPER, ENGAGEMENT_ONE, ignored -> {
            assertTrue(Thread.holdsLock(fixture.sharedMonitor));
            assertFalse(Thread.holdsLock(fixture.manager));
            return Result.success("prepared");
        }).value().orElseThrow();
    }

    @Test
    void duplicateEngagementIdCollisionLeavesStateUntouchedAndFreshIdCanRetry() {
        Fixture fixture = new Fixture();
        fixture.seed();
        assertTrue(fixture.manager.withSelection(OWNER_R1, PROPOSAL_R1_HELPER,
            ENGAGEMENT_ONE, ignored -> Result.success("first")).isSuccess());
        EngagementView existingEngagement = fixture.manager.findEngagement(OWNER_R1, ENGAGEMENT_ONE)
            .value().orElseThrow();

        assertFailure(FailureCode.ATOMIC_OPERATION_FAILED,
            fixture.manager.withSelection(OWNER_R2, PROPOSAL_R2_HELPER, ENGAGEMENT_ONE,
                ignored -> Result.success("collision")));
        assertEquals(RequestStatus.OPEN, fixture.manager.findRequest(REQUEST_R2).value().orElseThrow().status());
        assertEquals(ProposalStatus.PENDING,
            fixture.manager.findProposal(OWNER_R2, PROPOSAL_R2_HELPER).value().orElseThrow().status());
        assertEquals(existingEngagement,
            fixture.manager.findEngagement(OWNER_R1, ENGAGEMENT_ONE).value().orElseThrow());

        EngagementId freshEngagementId = new EngagementId("e2");
        assertTrue(fixture.manager.withSelection(OWNER_R2, PROPOSAL_R2_HELPER,
            freshEngagementId, ignored -> Result.success("fresh id")).isSuccess());
        EngagementView retriedEngagement = fixture.manager.findEngagement(OWNER_R2, freshEngagementId)
            .value().orElseThrow();
        assertNotSame(existingEngagement, retriedEngagement);
        assertEquals(RequestStatus.CLOSED, fixture.manager.findRequest(REQUEST_R2).value().orElseThrow().status());
        assertEquals(ProposalStatus.SELECTED,
            fixture.manager.findProposal(OWNER_R2, PROPOSAL_R2_HELPER).value().orElseThrow().status());
    }

    private static void assertSelectionStateUntouched(Fixture fixture) {
        assertEquals(RequestStatus.OPEN, fixture.manager.findRequest(REQUEST_R1).value().orElseThrow().status());
        assertEquals(ProposalStatus.PENDING,
            fixture.manager.findProposal(OWNER_R1, PROPOSAL_R1_HELPER).value().orElseThrow().status());
        assertTrue(fixture.manager.engagementsForMember(OWNER_R1).value().orElseThrow().isEmpty());
    }

    private static void assertFailure(FailureCode code, Result<?> result) {
        assertEquals(code, result.failureCode().orElseThrow());
    }

    private static final class Fixture {
        private final Map<MemberId, MemberStatus> status = new HashMap<>();
        private final Map<MemberId, Set<CategoryId>> skills = new HashMap<>();
        private final Map<CategoryId, CategoryView> categories = Map.of(
            GARDENING, new CategoryView(GARDENING, "Gardening"));
        private LocalDate now = TODAY;
        private final Object sharedMonitor = new Object();
        private final EngagementManager manager = new EngagementManager(
            new Members(), new Catalog(), new Clock(), sharedMonitor);

        private void seed() {
            for (MemberId member : List.of(OWNER_R1, HELPER, OWNER_R2, STRANGER)) {
                status.put(member, MemberStatus.ACTIVE);
                skills.put(member, new HashSet<>(Set.of(GARDENING)));
            }
            createRequest(REQUEST_R1, OWNER_R1);
            createRequest(REQUEST_R2, OWNER_R2);
            assertSuccess(manager.submitProposal(HELPER, REQUEST_R1, PROPOSAL_R1_HELPER, TODAY.plusDays(1)));
            assertSuccess(manager.submitProposal(OWNER_R2, REQUEST_R1, PROPOSAL_R1_OTHER, TODAY.plusDays(2)));
            assertSuccess(manager.submitProposal(HELPER, REQUEST_R2, PROPOSAL_R2_HELPER, TODAY.plusDays(1)));
        }

        private void createRequest(RequestId requestId, MemberId requester) {
            assertSuccess(manager.createRequest(requester, requestId, GARDENING, 30, TODAY, TODAY.plusDays(3)));
        }

        private static void assertSuccess(Result<?> result) {
            assertTrue(result.isSuccess(), () -> "Expected fixture operation to succeed: " + result);
        }

        private final class Members implements MemberManagerPort {
            @Override
            public Result<MemberProfile> findById(MemberId id) {
                if (!status.containsKey(id)) {
                    return Result.failure(FailureCode.MEMBER_NOT_FOUND, "missing");
                }
                return Result.success(new MemberProfile(id, id.value(), id.value() + "@example.test",
                    status.get(id), skills.get(id)));
            }

            @Override
            public Result<MemberProfile> findByCanonicalEmail(String email) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Result<Boolean> isEligible(MemberId id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long memberCount() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Result<MemberProfile> offer(MemberId member, CategoryId category) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Result<MemberProfile> reinstate(MemberId member) {
                throw new UnsupportedOperationException();
            }
        }

        private final class Catalog implements CategoryCatalog {
            @Override
            public Result<CategoryView> find(CategoryId id) {
                return categories.containsKey(id)
                    ? Result.success(categories.get(id))
                    : Result.failure(FailureCode.CATEGORY_NOT_FOUND, "missing");
            }

            @Override
            public List<CategoryView> list() {
                return List.copyOf(categories.values());
            }
        }

        private final class Clock implements SimulatedClock {
            @Override
            public LocalDate currentDate() {
                return now;
            }

            @Override
            public Result<LocalDate> advanceTo(LocalDate date) {
                throw new UnsupportedOperationException();
            }
        }
    }
}
