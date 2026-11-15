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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProposalCleanupTest {
    private static final MemberId OWNER = new MemberId("100001");
    private static final MemberId FIRST_PENDING_HELPER = new MemberId("100002");
    private static final MemberId SECOND_PENDING_HELPER = new MemberId("100003");
    private static final MemberId OTHER_OWNER = new MemberId("100004");
    private static final MemberId AGREED_HELPER = new MemberId("100006");
    private static final MemberId REJECTED_HELPER = new MemberId("100007");
    private static final CategoryId CATEGORY = new CategoryId("gardening");
    private static final RequestId FIRST_REQUEST = new RequestId("r1");
    private static final RequestId SECOND_REQUEST = new RequestId("r2");
    private static final RequestId THIRD_REQUEST = new RequestId("r3");
    private static final ProposalId FIRST_PENDING_PROPOSAL = new ProposalId("proposal-owner-pending-first-helper");
    private static final ProposalId SECOND_PENDING_PROPOSAL = new ProposalId("proposal-owner-pending-second-helper");
    private static final ProposalId OWNER_AS_HELPER_PROPOSAL = new ProposalId("proposal-owner-as-helper-pending");
    private static final ProposalId UNRELATED_PROPOSAL = new ProposalId("proposal-unrelated-pending");
    private static final ProposalId AGREED_PROPOSAL = new ProposalId("proposal-agreed-selected-helper");
    private static final ProposalId REJECTED_PROPOSAL = new ProposalId("proposal-agreed-rejected-helper");
    private static final EngagementId ENGAGEMENT = new EngagementId("engagement-owner-agreed");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 4);

    @Test
    void invalidatesEveryPendingProposalOnEitherSideAndPreservesOtherState() {
        Fixture fixture = new Fixture();
        fixture.seed();
        Map<ProposalId, ProposalView> proposalSnapshots = fixture.proposalSnapshots();
        List<RequestView> requestSnapshots = fixture.requests();
        List<EngagementView> engagementSnapshots = fixture.manager.engagementsForMember(OWNER).value().orElseThrow();
        assertEquals(List.of(RequestStatus.OPEN, RequestStatus.CLOSED, RequestStatus.OPEN),
            requestSnapshots.stream().map(RequestView::status).toList());
        assertEquals(ProposalStatus.PENDING, proposalSnapshots.get(FIRST_PENDING_PROPOSAL).status());
        assertEquals(ProposalStatus.PENDING, proposalSnapshots.get(SECOND_PENDING_PROPOSAL).status());
        assertEquals(ProposalStatus.PENDING, proposalSnapshots.get(OWNER_AS_HELPER_PROPOSAL).status());
        assertEquals(ProposalStatus.PENDING, proposalSnapshots.get(UNRELATED_PROPOSAL).status());
        assertEquals(ProposalStatus.SELECTED, proposalSnapshots.get(AGREED_PROPOSAL).status());
        assertEquals(ProposalStatus.REJECTED, proposalSnapshots.get(REJECTED_PROPOSAL).status());
        EngagementView agreedBefore = engagementSnapshots.get(0);
        Result<String> callbackResult = Result.success("released");
        AtomicInteger calls = new AtomicInteger();

        Result<String> returned = fixture.manager.withProposalCleanup(OWNER, () -> {
            calls.incrementAndGet();
            assertTrue(Thread.holdsLock(fixture.monitor));
            assertFalse(Thread.holdsLock(fixture.manager));
            assertEquals(requestSnapshots, fixture.requests());
            assertEquals(proposalSnapshots, fixture.proposalSnapshots());
            assertEquals(engagementSnapshots, fixture.manager.engagementsForMember(OWNER).value().orElseThrow());
            assertEquals(EngagementStatus.AGREED, agreedBefore.status());
            assertEquals(ProposalStatus.PENDING, fixture.proposal(OWNER, FIRST_PENDING_PROPOSAL).status());
            return callbackResult;
        });

        assertEquals(callbackResult, returned);
        assertEquals(1, calls.get());
        assertEquals(ProposalStatus.INVALIDATED, fixture.proposal(OWNER, FIRST_PENDING_PROPOSAL).status());
        assertEquals(ProposalStatus.INVALIDATED, fixture.proposal(OWNER, SECOND_PENDING_PROPOSAL).status());
        assertEquals(ProposalStatus.INVALIDATED, fixture.proposal(OTHER_OWNER, OWNER_AS_HELPER_PROPOSAL).status());
        assertEquals(3, fixture.proposalSnapshots().values().stream()
            .filter(proposal -> proposal.status() == ProposalStatus.INVALIDATED).count());
        assertEquals(proposalSnapshots.get(UNRELATED_PROPOSAL), fixture.proposal(OTHER_OWNER, UNRELATED_PROPOSAL));
        assertEquals(proposalSnapshots.get(AGREED_PROPOSAL), fixture.proposal(OWNER, AGREED_PROPOSAL));
        assertEquals(proposalSnapshots.get(REJECTED_PROPOSAL), fixture.proposal(OWNER, REJECTED_PROPOSAL));
        assertEquals(requestSnapshots, fixture.requests());
        assertEquals(engagementSnapshots, fixture.manager.engagementsForMember(OWNER).value().orElseThrow());
    }

    @Test
    void refusalAndExceptionLeaveAllStateUntouchedAndAllowRetry() {
        Fixture fixture = new Fixture();
        fixture.seed();
        List<RequestView> requestsBefore = fixture.requests();
        List<EngagementView> engagementsBefore = fixture.manager.engagementsForMember(OWNER).value().orElseThrow();
        Map<ProposalId, ProposalView> proposalSnapshots = fixture.proposalSnapshots();
        Result<String> refusal = Result.failure(FailureCode.BUSINESS_REJECTION, "cleanup refused");

        assertEquals(refusal, fixture.manager.withProposalCleanup(OWNER, () -> refusal));
        assertEquals(requestsBefore, fixture.requests());
        assertEquals(proposalSnapshots, fixture.proposalSnapshots());
        assertEquals(engagementsBefore, fixture.manager.engagementsForMember(OWNER).value().orElseThrow());
        assertThrows(IllegalStateException.class, () -> fixture.manager.withProposalCleanup(OWNER,
            () -> { throw new IllegalStateException("cleanup failed"); }));
        assertEquals(requestsBefore, fixture.requests());
        assertEquals(proposalSnapshots, fixture.proposalSnapshots());
        assertEquals(engagementsBefore, fixture.manager.engagementsForMember(OWNER).value().orElseThrow());
        assertTrue(fixture.manager.withProposalCleanup(OWNER, () -> Result.success("retry")).isSuccess());
        assertEquals(ProposalStatus.INVALIDATED, fixture.proposal(OWNER, FIRST_PENDING_PROPOSAL).status());
    }

    @Test
    void invokesCallbackWhenNoProposalsMatchAndRejectsReentrantWrites() {
        Fixture fixture = new Fixture();
        fixture.seed();
        AtomicInteger calls = new AtomicInteger();
        Result<String> returned = fixture.manager.withProposalCleanup(new MemberId("100099"), () -> {
            calls.incrementAndGet();
            return Result.success("empty cleanup");
        });
        assertTrue(returned.isSuccess());
        assertEquals(1, calls.get());

        Result<String> guarded = fixture.manager.withProposalCleanup(OWNER, () -> {
            assertEquals(FailureCode.INVALID_STATE, fixture.manager.submitProposal(AGREED_HELPER, THIRD_REQUEST,
                new ProposalId("callback-proposal"), TODAY).failureCode().orElseThrow());
            assertEquals(FailureCode.INVALID_STATE, fixture.manager.withProposalCleanup(OWNER,
                () -> { throw new AssertionError("nested callback must not run"); })
                .failureCode().orElseThrow());
            return Result.success("guarded");
        });
        assertTrue(guarded.isSuccess());
        assertEquals(ProposalStatus.INVALIDATED, fixture.proposal(OWNER, FIRST_PENDING_PROPOSAL).status());
    }

    private static final class Fixture {
        private final Map<MemberId, Set<CategoryId>> skills = new HashMap<>();
        private final Object monitor = new Object();
        private final EngagementManager manager = new EngagementManager(new Members(), new Catalog(),
            new Clock(), monitor);

        private Fixture() {
            for (MemberId member : List.of(OWNER, FIRST_PENDING_HELPER, SECOND_PENDING_HELPER, OTHER_OWNER,
                    AGREED_HELPER, REJECTED_HELPER)) {
                skills.put(member, Set.of(CATEGORY));
            }
        }

        private void seed() {
            createRequest(OWNER, FIRST_REQUEST);
            createRequest(OWNER, SECOND_REQUEST);
            createRequest(OTHER_OWNER, THIRD_REQUEST);
            submit(FIRST_PENDING_HELPER, FIRST_REQUEST, FIRST_PENDING_PROPOSAL);
            submit(SECOND_PENDING_HELPER, FIRST_REQUEST, SECOND_PENDING_PROPOSAL);
            submit(OWNER, THIRD_REQUEST, OWNER_AS_HELPER_PROPOSAL);
            submit(SECOND_PENDING_HELPER, THIRD_REQUEST, UNRELATED_PROPOSAL);
            submit(AGREED_HELPER, SECOND_REQUEST, AGREED_PROPOSAL);
            submit(REJECTED_HELPER, SECOND_REQUEST, REJECTED_PROPOSAL);
            assertTrue(manager.withSelection(OWNER, AGREED_PROPOSAL, ENGAGEMENT, Result::success).isSuccess());
        }

        private void createRequest(MemberId owner, RequestId requestId) {
            assertTrue(manager.createRequest(owner, requestId, CATEGORY, 30, TODAY, TODAY.plusDays(4)).isSuccess());
        }

        private void submit(MemberId helper, RequestId requestId, ProposalId proposalId) {
            assertTrue(manager.submitProposal(helper, requestId, proposalId, TODAY.plusDays(1)).isSuccess());
        }

        private List<RequestView> requests() {
            return List.of(FIRST_REQUEST, SECOND_REQUEST, THIRD_REQUEST).stream()
                .map(id -> manager.findRequest(id).value().orElseThrow()).toList();
        }

        private List<ProposalView> proposals() {
            return List.of(
                proposal(OWNER, FIRST_PENDING_PROPOSAL), proposal(OWNER, SECOND_PENDING_PROPOSAL),
                proposal(OTHER_OWNER, OWNER_AS_HELPER_PROPOSAL), proposal(OWNER, AGREED_PROPOSAL),
                proposal(OWNER, REJECTED_PROPOSAL), proposal(OTHER_OWNER, UNRELATED_PROPOSAL));
        }

        private Map<ProposalId, ProposalView> proposalSnapshots() {
            Map<ProposalId, ProposalView> snapshots = new HashMap<>();
            for (ProposalView proposal : proposals()) {
                snapshots.put(proposal.id(), proposal);
            }
            return Map.copyOf(snapshots);
        }

        private ProposalView proposal(MemberId owner, ProposalId proposalId) {
            return manager.findProposal(owner, proposalId).value().orElseThrow();
        }

        private final class Members implements MemberManagerPort {
            @Override public Result<MemberProfile> findById(MemberId id) {
                return skills.containsKey(id) ? Result.success(new MemberProfile(id, id.value(),
                    id.value() + "@example.test", MemberStatus.ACTIVE, skills.get(id)))
                    : Result.failure(FailureCode.MEMBER_NOT_FOUND, "missing");
            }
            @Override public Result<MemberProfile> findByCanonicalEmail(String email) { throw new UnsupportedOperationException(); }
            @Override public Result<Boolean> isEligible(MemberId id) { throw new UnsupportedOperationException(); }
            @Override public long memberCount() { throw new UnsupportedOperationException(); }
            @Override public Result<MemberProfile> offer(MemberId member, CategoryId category) { throw new UnsupportedOperationException(); }
            @Override public Result<MemberProfile> reinstate(MemberId member) { throw new UnsupportedOperationException(); }
        }

        private static final class Catalog implements CategoryCatalog {
            @Override public Result<CategoryView> find(CategoryId id) {
                return CATEGORY.equals(id) ? Result.success(new CategoryView(CATEGORY, "Gardening"))
                    : Result.failure(FailureCode.CATEGORY_NOT_FOUND, "missing");
            }
            @Override public List<CategoryView> list() { return List.of(new CategoryView(CATEGORY, "Gardening")); }
        }

        private static final class Clock implements SimulatedClock {
            @Override public LocalDate currentDate() { return TODAY; }
            @Override public Result<LocalDate> advanceTo(LocalDate date) { throw new UnsupportedOperationException(); }
        }
    }
}
