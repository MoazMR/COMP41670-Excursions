package ie.ucd.meitheal.shared;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.model.RequestStatus;
import ie.ucd.meitheal.shared.model.TransactionType;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.AccountReferenceView;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.ReportView;
import ie.ucd.meitheal.shared.view.RequestView;
import ie.ucd.meitheal.shared.view.TransactionView;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedContractTest {
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 9, 1);

    @Test
    void idsRejectNullAndBlankValues() {
        assertThrows(NullPointerException.class, () -> new MemberId(null));
        assertThrows(IllegalArgumentException.class, () -> new RequestId("  "));
    }

    @Test
    void requestAndProposalStatusesHaveSeparateFamilies() {
        assertEquals(Set.of(RequestStatus.OPEN, RequestStatus.CLOSED), Set.of(RequestStatus.values()));
        assertEquals(
                Set.of(ProposalStatus.PENDING, ProposalStatus.SELECTED, ProposalStatus.REJECTED,
                        ProposalStatus.WITHDRAWN, ProposalStatus.INVALIDATED),
                Set.of(ProposalStatus.values()));
    }

    @Test
    void failureCodesKeepDistinctCallerRejections() {
        assertTrue(Set.of(FailureCode.values()).containsAll(Set.of(
                FailureCode.DUPLICATE_EMAIL,
                FailureCode.DUPLICATE_PROPOSAL,
                FailureCode.SUSPENDED_MEMBER,
                FailureCode.INSUFFICIENT_AVAILABLE_CREDITS,
                FailureCode.INSUFFICIENT_COMMUNITY_FUND_CREDITS,
                FailureCode.NOT_PARTICIPANT,
                FailureCode.INVALID_STATE,
                FailureCode.CANCELLATION_CLOSED,
                FailureCode.ATOMIC_OPERATION_FAILED,
                FailureCode.MALFORMED_INPUT,
                FailureCode.MEMBER_NOT_FOUND,
                FailureCode.CATEGORY_NOT_FOUND,
                FailureCode.REQUEST_NOT_FOUND,
                FailureCode.PROPOSAL_NOT_FOUND,
                FailureCode.ENGAGEMENT_NOT_FOUND)));
        List<FailureCode> stableFailures = List.of(
                FailureCode.SUSPENDED_MEMBER,
                FailureCode.INSUFFICIENT_AVAILABLE_CREDITS,
                FailureCode.INSUFFICIENT_COMMUNITY_FUND_CREDITS,
                FailureCode.NOT_PARTICIPANT,
                FailureCode.INVALID_STATE,
                FailureCode.CANCELLATION_CLOSED,
                FailureCode.ATOMIC_OPERATION_FAILED);
        for (FailureCode code : stableFailures) {
            Result<String> rejected = Result.failure(code, "stable failure");
            assertTrue(rejected.isFailure());
            assertEquals(code, rejected.failureCode().orElseThrow());
        }
    }

    @Test
    void resultHasExclusiveOutcomeAndImmutableFailureSnapshot() {
        Result<String> success = Result.success("yes");
        Result<String> failure = Result.failure(FailureCode.BUSINESS_REJECTION, "display detail");

        assertTrue(success.isSuccess());
        assertFalse(success.isFailure());
        assertTrue(success.failureCode().isEmpty());
        assertEquals("yes", success.value().orElseThrow());
        assertTrue(failure.isFailure());
        assertFalse(failure.isSuccess());
        assertEquals(FailureCode.BUSINESS_REJECTION, failure.failureCode().orElseThrow());
        assertEquals("display detail", failure.detail().orElseThrow());
        assertTrue(failure.value().isEmpty());
    }

    @Test
    void balanceSupportsSignedValuesAndChecksArithmetic() {
        BalanceView negativeBalance = new BalanceView(new MemberId("member-1"), -60, 120, -180);
        BalanceView positiveBalance = new BalanceView(new MemberId("member-2"), 100, 20, 80);

        assertEquals(-180, negativeBalance.available());
        assertEquals(80, positiveBalance.available());
        assertThrows(IllegalArgumentException.class,
                () -> new BalanceView(new MemberId("member-3"), 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new BalanceView(new MemberId("member-4"), 0, -1, 1));
        assertThrows(ArithmeticException.class,
                () -> new BalanceView(new MemberId("member-5"), Long.MIN_VALUE, 1, 0));
    }

    @Test
    void reportsDeriveAndConserveCirculationWithCheckedArithmetic() {
        ReportView conservedReport = new ReportView(2, 230, 10);
        ReportView nonConservedReport = new ReportView(2, 229, 10);

        assertEquals(240, conservedReport.actualCirculation());
        assertEquals(240, conservedReport.expectedCirculation());
        assertTrue(conservedReport.conserved());
        assertEquals(239, nonConservedReport.actualCirculation());
        assertEquals(240, nonConservedReport.expectedCirculation());
        assertFalse(nonConservedReport.conserved());
        assertThrows(IllegalArgumentException.class, () -> new ReportView(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReportView(0, 0, -1));
        assertThrows(ArithmeticException.class,
                () -> new ReportView(0, Long.MAX_VALUE, 1).actualCirculation());
        assertThrows(ArithmeticException.class,
                () -> new ReportView(Long.MAX_VALUE, 0, 0).expectedCirculation());
    }

    @Test
    void memberSkillsAreTypedAndDefensivelyCopied() {
        CategoryId carpentry = new CategoryId("carpentry");
        CategoryId gardening = new CategoryId("gardening");
        Set<CategoryId> sourceSkills = new HashSet<>(Set.of(carpentry));
        MemberView member = new MemberView(
                new MemberId("member-1"), "M", "m@example.test", MemberStatus.ACTIVE, sourceSkills, -5);

        sourceSkills.add(gardening);
        assertEquals(Set.of(carpentry), member.skills());
        assertThrows(UnsupportedOperationException.class, () -> member.skills().add(gardening));
    }

    @Test
    void requestDurationAndWindowAreValidatedIndependently() {
        MemberId requesterId = new MemberId("requester-1");
        CategoryId categoryId = new CategoryId("category-1");

        assertThrows(IllegalArgumentException.class, () -> new RequestView(
                new RequestId("request-0"), requesterId, categoryId, 0,
                FIXED_DATE, FIXED_DATE, RequestStatus.OPEN));
        assertDoesNotThrow(() -> new RequestView(
                new RequestId("request-1"), requesterId, categoryId, 1,
                FIXED_DATE, FIXED_DATE, RequestStatus.OPEN));
        assertDoesNotThrow(() -> new RequestView(
                new RequestId("request-480"), requesterId, categoryId, 480,
                FIXED_DATE, FIXED_DATE.plusDays(1), RequestStatus.CLOSED));
        assertThrows(IllegalArgumentException.class, () -> new RequestView(
                new RequestId("request-481"), requesterId, categoryId, 481,
                FIXED_DATE, FIXED_DATE, RequestStatus.OPEN));
        assertThrows(IllegalArgumentException.class, () -> new RequestView(
                new RequestId("request-window"), requesterId, categoryId, 60,
                FIXED_DATE.plusDays(1), FIXED_DATE, RequestStatus.OPEN));
    }

    @Test
    void engagementDurationAndSnapshotAreValidatedIndependently() {
        MemberId requesterId = new MemberId("requester-1");
        MemberId helperId = new MemberId("helper-1");
        Set<MemberId> sourceConfirmations = new HashSet<>(Set.of(requesterId));

        assertThrows(IllegalArgumentException.class, () -> new EngagementView(
                new EngagementId("engagement-0"), new ProposalId("proposal-0"), new RequestId("request-0"),
                requesterId, helperId, 0, FIXED_DATE, EngagementStatus.AGREED,
                Set.of(), null, null, false));
        EngagementView oneMinute = new EngagementView(
                new EngagementId("engagement-1"), new ProposalId("proposal-1"), new RequestId("request-1"),
                requesterId, helperId, 1, FIXED_DATE, EngagementStatus.AGREED,
                sourceConfirmations, null, null, false);
        EngagementView maxDuration = new EngagementView(
                new EngagementId("engagement-480"), new ProposalId("proposal-480"), new RequestId("request-480"),
                requesterId, helperId, 480, FIXED_DATE.plusDays(1), EngagementStatus.COMPLETED,
                Set.of(requesterId, helperId), null, null, false);

        assertThrows(IllegalArgumentException.class, () -> new EngagementView(
                new EngagementId("engagement-481"), new ProposalId("proposal-481"), new RequestId("request-481"),
                requesterId, helperId, 481, FIXED_DATE, EngagementStatus.AGREED,
                Set.of(), null, null, false));
        sourceConfirmations.add(helperId);
        assertEquals(Set.of(requesterId), oneMinute.confirmationActors());
        assertEquals(FIXED_DATE.plusDays(1), maxDuration.scheduledDate());
        assertThrows(UnsupportedOperationException.class,
                () -> oneMinute.confirmationActors().add(helperId));
        assertThrows(IllegalArgumentException.class, () -> new EngagementView(
                new EngagementId("engagement-cancel"), new ProposalId("proposal-cancel"),
                new RequestId("request-cancel"), requesterId, helperId, 30, FIXED_DATE,
                EngagementStatus.CANCELLED, Set.of(), requesterId, null, false));
    }

    @Test
    void transactionLinkRulesCoverGrantPaymentAndLevyTypes() {
        AccountReferenceView memberAccount = new AccountReferenceView(
                AccountReferenceView.AccountType.MEMBER, new MemberId("member-1"));
        AccountReferenceView helperAccount = new AccountReferenceView(
                AccountReferenceView.AccountType.MEMBER, new MemberId("member-2"));
        AccountReferenceView fundAccount = new AccountReferenceView(AccountReferenceView.AccountType.FUND, null);
        AccountReferenceView systemAccount = new AccountReferenceView(AccountReferenceView.AccountType.SYSTEM, null);
        EngagementId engagementId = new EngagementId("engagement-1");

        assertDoesNotThrow(() -> new TransactionView(
                new TransactionId("welcome"), TransactionType.WELCOME_GRANT,
                systemAccount, memberAccount, 120, FIXED_DATE, null));
        assertDoesNotThrow(() -> new TransactionView(
                new TransactionId("community"), TransactionType.COMMUNITY_GRANT,
                fundAccount, memberAccount, 25, FIXED_DATE, null));
        assertDoesNotThrow(() -> new TransactionView(
                new TransactionId("payment"), TransactionType.SERVICE_PAYMENT,
                memberAccount, helperAccount, 10, FIXED_DATE, engagementId));
        assertDoesNotThrow(() -> new TransactionView(
                new TransactionId("levy"), TransactionType.LEVY,
                memberAccount, fundAccount, 1, FIXED_DATE, engagementId));
        assertThrows(IllegalArgumentException.class, () -> new TransactionView(
                new TransactionId("unlinked-payment"), TransactionType.SERVICE_PAYMENT,
                memberAccount, helperAccount, 10, FIXED_DATE, null));
        assertThrows(IllegalArgumentException.class, () -> new TransactionView(
                new TransactionId("linked-grant"), TransactionType.COMMUNITY_GRANT,
                fundAccount, memberAccount, 25, FIXED_DATE, engagementId));
        assertThrows(IllegalArgumentException.class, () -> new TransactionView(
                new TransactionId("zero"), TransactionType.WELCOME_GRANT,
                systemAccount, memberAccount, 0, FIXED_DATE, null));
    }
}
