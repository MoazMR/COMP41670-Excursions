package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.model.TransactionType;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.AccountReferenceView;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.CommunityGrantView;
import ie.ucd.meitheal.shared.view.ReportView;
import ie.ucd.meitheal.shared.view.TransactionView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerManagerCommunityGrantTest {
    private static final LocalDate MAY_3 = LocalDate.of(2027, 5, 3);
    private static final MemberId ALICE = new MemberId("alice");
    private static final MemberId BOB = new MemberId("bob");
    private static final MemberId CAROL = new MemberId("carol");

    private final AtomicInteger nextId = new AtomicInteger();
    private LocalDate date = MAY_3.minusDays(1);
    private TransactionId forcedId;
    private final LedgerManager ledger = new LedgerManager(
            () -> forcedId == null ? freshId() : forcedId, () -> date);

    @Test
    void mayApprovalBlocksThroughJune3AndAllowsJune4WithoutChangingHolds() {
        fundFromSettlement();
        assertTrue(ledger.withHold(new EngagementId("reserve"), ALICE, CAROL, 19, Result::success).isSuccess());
        date = MAY_3;
        assertEquals(new CommunityGrantView(ALICE, 3, MAY_3),
                ledger.applyForCommunityGrant(ALICE, 3).value().orElseThrow());

        List<TransactionView> approvedHistory = ledger.memberLedgerHistory(ALICE);
        ReportView approvedReport = ledger.report();
        BalanceView approvedBalance = ledger.balanceView(ALICE);
        int idsIssued = nextId.get();
        assertEquals(new BalanceView(ALICE, -24, 19, -43), approvedBalance);
        assertEquals(new ReportView(3, 356, 4), approvedReport);
        assertTrue(approvedReport.conserved());

        date = LocalDate.of(2027, 6, 3);
        assertEquals(FailureCode.BUSINESS_REJECTION,
                ledger.applyForCommunityGrant(ALICE, 4).failureCode().orElseThrow());
        assertEquals(approvedHistory, ledger.memberLedgerHistory(ALICE));
        assertEquals(approvedReport, ledger.report());
        assertEquals(approvedBalance, ledger.balanceView(ALICE));
        assertEquals(idsIssued, nextId.get());

        date = date.plusDays(1);
        assertEquals(new CommunityGrantView(ALICE, 4, date),
                ledger.applyForCommunityGrant(ALICE, 4).value().orElseThrow());
        assertEquals(new BalanceView(ALICE, -20, 19, -39), ledger.balanceView(ALICE));
        assertEquals(new ReportView(3, 360, 0), ledger.report());
        assertEquals(4, approvedHistory.size());
        assertEquals(5, ledger.memberLedgerHistory(ALICE).size());
    }

    @Test
    void eligibilityUsesPostedBalanceAndRejectsInvalidOrUnfundedRequests() {
        assertEquals(new ReportView(0, 0, 0), ledger.report());
        MemberId zeroBalance = new MemberId("zero-balance");
        fundFromSettlement();
        assertTrue(ledger.withHold(new EngagementId("held-only"), zeroBalance, BOB, 19, Result::success).isSuccess());
        assertEquals(new BalanceView(zeroBalance, 0, 19, -19), ledger.balanceView(zeroBalance));
        var history = ledger.history();
        var report = ledger.report();

        assertEquals(FailureCode.BUSINESS_REJECTION,
                ledger.applyForCommunityGrant(zeroBalance, 1).failureCode().orElseThrow());
        assertEquals(FailureCode.INVALID_INPUT,
                ledger.applyForCommunityGrant(ALICE, 0).failureCode().orElseThrow());
        assertEquals(FailureCode.INVALID_INPUT,
                ledger.applyForCommunityGrant(ALICE, -1).failureCode().orElseThrow());
        assertEquals(FailureCode.INSUFFICIENT_COMMUNITY_FUND_CREDITS,
                ledger.applyForCommunityGrant(ALICE, 8).failureCode().orElseThrow());
        assertEquals(history, ledger.history());
        assertEquals(report, ledger.report());
        assertEquals(-27, ledger.balanceOf(ALICE));
    }

    @Test
    void duplicateTransactionIdPreservesApprovalEligibilityForSameDayRetry() {
        fundFromSettlement();
        var history = ledger.history();
        var report = ledger.report();
        var balance = ledger.balanceView(ALICE);
        forcedId = history.getFirst().id();

        assertEquals(FailureCode.ATOMIC_OPERATION_FAILED,
                ledger.applyForCommunityGrant(ALICE, 1).failureCode().orElseThrow());
        assertEquals(history, ledger.history());
        assertEquals(report, ledger.report());
        assertEquals(balance, ledger.balanceView(ALICE));

        forcedId = null;
        assertTrue(ledger.applyForCommunityGrant(ALICE, 1).isSuccess());
        assertEquals(history.size() + 1, ledger.history().size());
    }

    @Test
    void memberHistoryHasTypedAccountsAndIncludesCreditsAndDebitsInAppendOrder() {
        EngagementId engagement = fundFromSettlement();
        assertTrue(ledger.applyForCommunityGrant(ALICE, 1).isSuccess());

        var history = ledger.memberLedgerHistory(ALICE);
        assertEquals(List.of(TransactionType.WELCOME_GRANT, TransactionType.SERVICE_PAYMENT,
                        TransactionType.LEVY, TransactionType.COMMUNITY_GRANT),
                history.stream().map(TransactionView::type).toList());
        assertEquals(List.of(TransactionType.WELCOME_GRANT, TransactionType.SERVICE_PAYMENT),
                ledger.memberLedgerHistory(BOB).stream().map(TransactionView::type).toList());
        assertEquals(List.of(TransactionType.WELCOME_GRANT),
                ledger.memberLedgerHistory(CAROL).stream().map(TransactionView::type).toList());
        assertEquals(engagement, history.get(1).engagementId());
        assertEquals(engagement, history.get(2).engagementId());
        var grant = history.getLast();
        assertEquals(new AccountReferenceView(AccountReferenceView.AccountType.FUND, null), grant.from());
        assertEquals(new AccountReferenceView(AccountReferenceView.AccountType.MEMBER, ALICE), grant.to());
        assertEquals(1, grant.amount());
        assertThrows(UnsupportedOperationException.class, history::clear);
    }

    private EngagementId fundFromSettlement() {
        ledger.grantWelcome(ALICE);
        ledger.grantWelcome(BOB);
        ledger.grantWelcome(CAROL);
        EngagementId engagement = new EngagementId("settled");
        assertTrue(ledger.withHold(engagement, ALICE, BOB, 140, Result::success).isSuccess());
        assertTrue(ledger.withSettlement(engagement, date, this::freshId, () -> Result.success("settled")).isSuccess());
        return engagement;
    }

    private TransactionId freshId() {
        return new TransactionId("t" + nextId.incrementAndGet());
    }
}
