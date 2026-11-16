package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.ucd.meitheal.app.CommunityFinanceService;
import ie.ucd.meitheal.app.InMemoryRegistrationService;
import ie.ucd.meitheal.app.RegistrationService;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.model.TransactionType;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.AccountReferenceView;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.CommunityGrantView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.ReportView;
import ie.ucd.meitheal.shared.view.RequestView;
import ie.ucd.meitheal.shared.view.TransactionView;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommunityFinanceConsoleTest {
    private static final MemberId MEMBER_ID = new MemberId("100001");
    private static final MemberId OTHER_ID = new MemberId("100002");

    @Test
    void grantHandlesApprovalRefusalMalformedNegativeOverflowAndBack() throws Exception {
        FinanceStub service = new FinanceStub();
        StringWriter output = run(service, "2\n2\n100001\n10\nabc\n-3\n9223372036854775808\n25\n0\n"
                + "10\n0\n10\n91\u001b10\n4\n0\n0\n");
        String text = output.toString();

        assertTrue(text.contains("Enter a positive whole number of credits, or 0 Back."));
        assertTrue(text.contains("Community grant approved: 25 credits on 2026-11-21."));
        assertTrue(text.contains("ERROR INSUFFICIENT_COMMUNITY_FUND_CREDITS - fund empty"));
        assertEquals(List.of(25L, 4L), service.grantAmounts);
        assertTrue(service.grantMemberIds.stream().allMatch(MEMBER_ID::equals));
    }

    @Test
    void historiesAndBalanceUseSelectedCallerAndRenderFields() throws Exception {
        FinanceStub service = new FinanceStub();
        StringWriter output = run(service, "2\n2\n100001\n11\n1\n0\n2\n0\n3\n0\n0\n0\n");
        String text = output.toString();

        assertTrue(text.contains("Ledger balance: 120 credits"));
        assertTrue(text.contains("2026-11-21 | COMMUNITY_GRANT | 25 credits"));
        assertTrue(text.contains("FUND -> MEMBER 100001 | Transaction tx-1"));
        assertTrue(text.contains("MEMBER 100001 -> MEMBER 100002 | Transaction tx-2 | Engagement eng-1"));
        assertTrue(text.contains("2026-11-22 | COMPLETED | Requester | Request req-1"));
        assertTrue(text.contains("Helper 100002"));
        assertEquals(List.of(MEMBER_ID, MEMBER_ID), service.historyMemberIds);
    }

    @Test
    void signedOutReportRendersTotals() throws Exception {
        FinanceStub service = new FinanceStub();
        StringWriter output = run(service, "10\n11\n12\n0\n0\n");
        String text = output.toString();

        assertTrue(text.contains("Members: 2"));
        assertTrue(text.contains("Total member balances: 220 credits"));
        assertTrue(text.contains("Community fund: 20 credits"));
        assertTrue(text.contains("Combined total: 240 credits"));
        assertTrue(text.contains("Login is required to apply for a community grant."));
        assertTrue(text.contains("Login is required to view balance and history."));
        assertEquals(1, service.reportCalls);
        assertTrue(service.grantAmounts.isEmpty());
        assertTrue(service.historyMemberIds.isEmpty());
    }

    @Test
    void emptyHistoryAndTypedFailuresAreShown() throws Exception {
        FinanceStub service = new FinanceStub();
        service.emptyHistories = true;
        service.historyFailure = true;
        StringWriter output = run(service, "2\n2\n100001\n11\n2\n0\n3\n0\n0\n");
        String text = output.toString();

        assertTrue(text.contains("ERROR NOT_FOUND - history unavailable"));
        assertTrue(text.contains("No engagement history found."));
    }

    private static StringWriter run(RegistrationService service, String script) throws Exception {
        StringWriter output = new StringWriter();
        new RegistrationConsole(new StringReader(script), output, service).run();
        return output;
    }

    private static final class FinanceStub implements CommunityFinanceService {
        private final InMemoryRegistrationService delegate = InMemoryRegistrationService.create();
        private final List<Long> grantAmounts = new ArrayList<>();
        private final List<MemberId> grantMemberIds = new ArrayList<>();
        private final List<MemberId> historyMemberIds = new ArrayList<>();
        private int reportCalls;
        private boolean emptyHistories;
        private boolean historyFailure;

        private FinanceStub() {
            delegate.register("Alex", "alex@example.org");
        }

        @Override public Result<MemberView> register(String name, String email) { return delegate.register(name, email); }
        @Override public Result<MemberView> findMember(MemberId id) { return delegate.findMember(id); }
        @Override public Result<MemberView> findMemberByEmail(String email) { return delegate.findMemberByEmail(email); }
        @Override public Result<BalanceView> balance(MemberId id) { return delegate.balance(id); }
        @Override public List<CategoryView> categories() { return delegate.categories(); }
        @Override public Result<MemberView> offerSkill(MemberId id, CategoryId category) { return delegate.offerSkill(id, category); }
        @Override public Result<RequestView> createRequest(MemberId id, CategoryId category, int minutes, LocalDate start, LocalDate end) { return delegate.createRequest(id, category, minutes, start, end); }
        @Override public Result<List<RequestView>> matchingRequests(MemberId id) { return delegate.matchingRequests(id); }
        @Override public Result<ProposalView> submitProposal(MemberId id, RequestId requestId, LocalDate date) { return delegate.submitProposal(id, requestId, date); }
        @Override public Result<List<ProposalView>> proposalsForRequest(MemberId id, RequestId requestId) { return delegate.proposalsForRequest(id, requestId); }
        @Override public Result<EngagementView> selectProposal(MemberId id, ProposalId proposalId) { return delegate.selectProposal(id, proposalId); }
        @Override public Result<EngagementView> confirmEngagement(MemberId id, EngagementId engagementId) { return delegate.confirmEngagement(id, engagementId); }
        @Override public Result<EngagementView> cancelEngagement(MemberId id, EngagementId engagementId) { return delegate.cancelEngagement(id, engagementId); }
        @Override public Result<MemberView> suspendMember(MemberId id) { return delegate.suspendMember(id); }
        @Override public Result<MemberView> reinstateMember(MemberId id) { return delegate.reinstateMember(id); }

        @Override
        public Result<CommunityGrantView> applyForCommunityGrant(MemberId id, long amount) {
            grantMemberIds.add(id);
            grantAmounts.add(amount);
            return amount == 4
                    ? Result.failure(FailureCode.INSUFFICIENT_COMMUNITY_FUND_CREDITS, "fund empty")
                    : Result.success(new CommunityGrantView(id, amount, LocalDate.of(2026, 11, 21)));
        }

        @Override
        public Result<List<TransactionView>> memberLedgerHistory(MemberId id) {
            historyMemberIds.add(id);
            if (historyFailure) return Result.failure(FailureCode.NOT_FOUND, "history unavailable");
            if (emptyHistories) return Result.success(List.of());
            return Result.success(List.of(new TransactionView(new TransactionId("tx-1"),
                    TransactionType.COMMUNITY_GRANT,
                    new AccountReferenceView(AccountReferenceView.AccountType.FUND, null),
                    new AccountReferenceView(AccountReferenceView.AccountType.MEMBER, id), 25,
                    LocalDate.of(2026, 11, 21), null),
                    new TransactionView(new TransactionId("tx-2"), TransactionType.SERVICE_PAYMENT,
                            new AccountReferenceView(AccountReferenceView.AccountType.MEMBER, id),
                            new AccountReferenceView(AccountReferenceView.AccountType.MEMBER, OTHER_ID),
                            60, LocalDate.of(2026, 11, 22), new EngagementId("eng-1"))));
        }

        @Override
        public Result<List<EngagementView>> ownEngagementHistory(MemberId id) {
            historyMemberIds.add(id);
            if (emptyHistories) return Result.success(List.of());
            return Result.success(List.of(new EngagementView(new EngagementId("eng-1"),
                    new ProposalId("prop-1"), new RequestId("req-1"), id, OTHER_ID, 60,
                    LocalDate.of(2026, 11, 22), EngagementStatus.COMPLETED, Set.of(id, OTHER_ID),
                    null, null, false)));
        }

        @Override
        public ReportView report() {
            reportCalls++;
            return new ReportView(2, 220, 20);
        }
    }
}
