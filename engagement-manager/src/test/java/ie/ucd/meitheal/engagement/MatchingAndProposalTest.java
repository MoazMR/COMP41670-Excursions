package ie.ucd.meitheal.engagement;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.port.MemberManagerPort;
import ie.ucd.meitheal.shared.port.SimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.RequestView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MatchingAndProposalTest {
    private static final CategoryId GARDEN = new CategoryId("gardening");
    private static final CategoryId COOK = new CategoryId("cooking");
    private static final MemberId OWNER = new MemberId("100001");
    private static final MemberId HELPER = new MemberId("100002");
    private static final MemberId OTHER_HELPER = new MemberId("100003");
    private static final MemberId UNKNOWN = new MemberId("999999");
    private static final RequestId REQUEST = new RequestId("r1");
    private static final ProposalId PROPOSAL = new ProposalId("p1");
    private static final ProposalId OTHER_PROPOSAL = new ProposalId("p2");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 4);
    private static final LocalDate END = TODAY.plusDays(2);

    private final Map<MemberId, MemberStatus> status = new HashMap<>();
    private final Map<MemberId, Set<CategoryId>> offers = new HashMap<>();
    private final Map<CategoryId, CategoryView> catalogue = new HashMap<>();
    private LocalDate now = TODAY;
    private EngagementManager manager;

    @BeforeEach
    void createManager() {
        for (MemberId member : List.of(OWNER, HELPER, OTHER_HELPER)) {
            status.put(member, MemberStatus.ACTIVE);
            offers.put(member, Set.of(GARDEN));
        }
        catalogue.put(GARDEN, new CategoryView(GARDEN, "Gardening"));
        catalogue.put(COOK, new CategoryView(COOK, "Cooking"));
        manager = new EngagementManager(new Members(), new Categories(), new Clock());
    }

    @Test
    void matchingExcludesOwnExpiredUnskilledAndIneligibleRequestsAndReturnsSnapshot() {
        RequestView matched = create(OWNER, REQUEST, GARDEN, END, END);
        RequestView lastDay = create(OWNER, new RequestId("r2"), GARDEN, TODAY.minusDays(2), TODAY);
        create(HELPER, new RequestId("own"), GARDEN, TODAY, END);
        create(OWNER, new RequestId("expired"), GARDEN, TODAY.minusDays(2), TODAY.minusDays(1));
        create(OWNER, new RequestId("unskilled"), COOK, TODAY, END);
        create(OTHER_HELPER, new RequestId("suspended"), GARDEN, TODAY, END);
        status.put(OTHER_HELPER, MemberStatus.SUSPENDED);

        List<RequestView> found = manager.matchingRequests(HELPER).value().orElseThrow();
        assertEquals(List.of(matched, lastDay), found);
        assertThrows(UnsupportedOperationException.class, found::clear);
        assertFailure(FailureCode.PROPOSAL_NOT_FOUND, manager.findProposal(OWNER, PROPOSAL));
        assertEquals(matched, manager.findRequest(REQUEST).value().orElseThrow());

        status.remove(OTHER_HELPER);
        now = TODAY.plusDays(1);
        assertEquals(List.of(matched), manager.matchingRequests(HELPER).value().orElseThrow());
        catalogue.remove(GARDEN);
        assertEquals(List.of(), manager.matchingRequests(HELPER).value().orElseThrow());
        assertEquals(List.of(matched, lastDay), found);
        assertFailure(FailureCode.MALFORMED_INPUT, manager.matchingRequests(null));
        assertFailure(FailureCode.MEMBER_NOT_FOUND, manager.matchingRequests(UNKNOWN));
        status.put(HELPER, MemberStatus.SUSPENDED);
        assertFailure(FailureCode.SUSPENDED_MEMBER, manager.matchingRequests(HELPER));
    }

    @Test
    void acceptsBothWindowEndsAndKeepsTheRequestOpen() {
        RequestView open = create(OWNER, REQUEST, GARDEN, TODAY, END);
        var first = manager.submitProposal(HELPER, REQUEST, PROPOSAL, TODAY).value().orElseThrow();
        var second = manager.submitProposal(OTHER_HELPER, REQUEST, OTHER_PROPOSAL, END).value().orElseThrow();

        assertEquals(ProposalStatus.PENDING, first.status());
        assertEquals(TODAY, first.proposedOn());
        assertEquals(END, second.proposedOn());
        assertEquals(first, manager.findProposal(OWNER, PROPOSAL).value().orElseThrow());
        assertEquals(second, manager.findProposal(OWNER, OTHER_PROPOSAL).value().orElseThrow());
        assertEquals(open, manager.findRequest(REQUEST).value().orElseThrow());
        assertFailure(FailureCode.NOT_PARTICIPANT, manager.findProposal(HELPER, PROPOSAL));
        assertFailure(FailureCode.MEMBER_NOT_FOUND, manager.findProposal(UNKNOWN, PROPOSAL));
        assertFailure(FailureCode.MALFORMED_INPUT, manager.findProposal(OWNER, null));
    }

    @Test
    void rejectionDoesNotStoreAProposalOrChangeTheRequestAndTheSameIdCanBeRetried() {
        RequestView open = create(OWNER, REQUEST, GARDEN, TODAY.minusDays(1), END);
        assertFailure(FailureCode.MALFORMED_INPUT, manager.submitProposal(HELPER, REQUEST, PROPOSAL, null));
        assertFailure(FailureCode.REQUEST_NOT_FOUND,
            manager.submitProposal(HELPER, new RequestId("missing"), PROPOSAL, TODAY));
        assertFailure(FailureCode.MEMBER_NOT_FOUND, manager.submitProposal(UNKNOWN, REQUEST, PROPOSAL, TODAY));
        assertFailure(FailureCode.BUSINESS_REJECTION, manager.submitProposal(OWNER, REQUEST, PROPOSAL, TODAY));
        assertFailure(FailureCode.BUSINESS_REJECTION,
            manager.submitProposal(HELPER, REQUEST, PROPOSAL, TODAY.minusDays(1)));
        assertFailure(FailureCode.BUSINESS_REJECTION,
            manager.submitProposal(HELPER, REQUEST, PROPOSAL, TODAY.minusDays(2)));
        assertFailure(FailureCode.BUSINESS_REJECTION,
            manager.submitProposal(HELPER, REQUEST, PROPOSAL, END.plusDays(1)));

        offers.put(HELPER, Set.of(COOK));
        assertFailure(FailureCode.BUSINESS_REJECTION, manager.submitProposal(HELPER, REQUEST, PROPOSAL, TODAY));
        offers.put(HELPER, Set.of(GARDEN));
        CategoryView gardening = catalogue.remove(GARDEN);
        assertFailure(FailureCode.BUSINESS_REJECTION, manager.submitProposal(HELPER, REQUEST, PROPOSAL, TODAY));
        catalogue.put(GARDEN, gardening);

        for (MemberId participant : List.of(OWNER, HELPER)) {
            status.put(participant, MemberStatus.SUSPENDED);
            assertFailure(FailureCode.SUSPENDED_MEMBER, manager.submitProposal(HELPER, REQUEST, PROPOSAL, TODAY));
            status.put(participant, MemberStatus.ACTIVE);
        }
        status.remove(OWNER);
        assertFailure(FailureCode.MEMBER_NOT_FOUND, manager.submitProposal(HELPER, REQUEST, PROPOSAL, TODAY));
        status.put(OWNER, MemberStatus.ACTIVE);
        assertFailure(FailureCode.PROPOSAL_NOT_FOUND, manager.findProposal(OWNER, PROPOSAL));
        assertEquals(open, manager.findRequest(REQUEST).value().orElseThrow());
        assertTrue(manager.submitProposal(HELPER, REQUEST, PROPOSAL, TODAY).isSuccess());
    }

    @Test
    void duplicatesPreserveTheOriginalProposalAndReserveNoTimeAcrossRequests() {
        create(OWNER, REQUEST, GARDEN, TODAY, END);
        RequestId anotherRequest = new RequestId("r2");
        create(OWNER, anotherRequest, GARDEN, TODAY, END);
        var existing = manager.submitProposal(HELPER, REQUEST, PROPOSAL, TODAY).value().orElseThrow();

        assertFailure(FailureCode.INVALID_STATE, manager.submitProposal(OTHER_HELPER, REQUEST, PROPOSAL, TODAY));
        assertFailure(FailureCode.DUPLICATE_PROPOSAL,
            manager.submitProposal(HELPER, REQUEST, OTHER_PROPOSAL, END));
        assertFailure(FailureCode.PROPOSAL_NOT_FOUND, manager.findProposal(OWNER, OTHER_PROPOSAL));
        assertEquals(existing, manager.findProposal(OWNER, PROPOSAL).value().orElseThrow());
        assertTrue(manager.submitProposal(HELPER, anotherRequest, OTHER_PROPOSAL, TODAY).isSuccess());
    }

    @Test
    void sharedCreationStoresCanonicalCategoryAndRejectsBadInputsWithoutOverwrite() {
        RequestView original = create(OWNER, REQUEST, new CategoryId("GARDENING"), TODAY, END);
        assertEquals(GARDEN, original.categoryId());
        assertFailure(FailureCode.INVALID_STATE, manager.createRequest(OWNER, REQUEST, COOK, 1, TODAY, END));
        assertFailure(FailureCode.MALFORMED_INPUT, manager.createRequest(OWNER, REQUEST, COOK, 0, TODAY, END));
        assertFailure(FailureCode.MALFORMED_INPUT, manager.createRequest(OWNER, REQUEST, COOK, 1, END, TODAY));
        assertFailure(FailureCode.MEMBER_NOT_FOUND, manager.createRequest(UNKNOWN, REQUEST, COOK, 1, TODAY, END));
        assertFailure(FailureCode.CATEGORY_NOT_FOUND,
            manager.createRequest(OWNER, REQUEST, new CategoryId("missing"), 1, TODAY, END));
        assertEquals(original, manager.findRequest(REQUEST).value().orElseThrow());
    }

    private RequestView create(MemberId owner, RequestId id, CategoryId category, LocalDate start, LocalDate end) {
        return manager.createRequest(owner, id, category, 30, start, end).value().orElseThrow();
    }

    private static void assertFailure(FailureCode code, Result<?> result) {
        assertEquals(code, result.failureCode().orElseThrow());
    }

    private final class Members implements MemberManagerPort {
        @Override
        public Result<MemberProfile> findById(MemberId id) {
            MemberStatus memberStatus = status.get(id);
            if (memberStatus == null) {
                return Result.failure(FailureCode.MEMBER_NOT_FOUND, "Member not found");
            }
            return Result.success(new MemberProfile(id, id.value(), id.value() + "@example.test",
                memberStatus, offers.get(id)));
        }

        @Override public Result<MemberProfile> findByCanonicalEmail(String email) { throw new UnsupportedOperationException(); }
        @Override public Result<Boolean> isEligible(MemberId id) { throw new UnsupportedOperationException(); }
        @Override public long memberCount() { throw new UnsupportedOperationException(); }
        @Override public Result<MemberProfile> offer(MemberId id, CategoryId category) { throw new UnsupportedOperationException(); }
        @Override public Result<MemberProfile> reinstate(MemberId id) { throw new UnsupportedOperationException(); }
    }

    private final class Categories implements CategoryCatalog {
        @Override
        public Result<CategoryView> find(CategoryId category) {
            CategoryView view = catalogue.get(new CategoryId(category.value().toLowerCase(java.util.Locale.ROOT)));
            return view == null ? Result.failure(FailureCode.CATEGORY_NOT_FOUND, "Category not found") : Result.success(view);
        }

        @Override public List<CategoryView> list() { return List.copyOf(catalogue.values()); }
    }

    private final class Clock implements SimulatedClock {
        @Override public LocalDate currentDate() { return now; }
        @Override public Result<LocalDate> advanceTo(LocalDate date) { throw new UnsupportedOperationException(); }
    }
}
