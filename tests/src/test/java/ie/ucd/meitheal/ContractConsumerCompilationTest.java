package ie.ucd.meitheal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.ucd.meitheal.app.MeithealService;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.MemberStatus;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.CommunityGrantView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.RequestView;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Compile-time consumer coverage for the public service and its transitive shared API. */
class ContractConsumerCompilationTest {
    @Test
    void publicServiceExposesCallerOwnedIdsAndProposalDates() throws NoSuchMethodException {
        Method register = MeithealService.class.getMethod("register", String.class, String.class);
        Method createRequest = MeithealService.class.getMethod("createRequest", MemberId.class,
                CategoryId.class, int.class, LocalDate.class, LocalDate.class);
        Method submitProposal = MeithealService.class.getMethod("submitProposal", MemberId.class,
                RequestId.class, LocalDate.class);
        Method selectProposal = MeithealService.class.getMethod("selectProposal", MemberId.class,
                ProposalId.class);

        assertEquals(Result.class, register.getReturnType());
        assertEquals(Result.class, createRequest.getReturnType());
        assertEquals(Result.class, submitProposal.getReturnType());
        assertEquals(Result.class, selectProposal.getReturnType());
        assertEquals(MemberView.class, resultType(register));
        assertEquals(RequestView.class, resultType(createRequest));
        assertEquals(ProposalView.class, resultType(submitProposal));
        assertEquals(EngagementView.class, resultType(selectProposal));
    }

    private Class<?> resultType(Method method) {
        ParameterizedType result = (ParameterizedType) method.getGenericReturnType();
        return (Class<?>) result.getActualTypeArguments()[0];
    }

    @Test
    void rejectedResultsAndViewsHaveStableCallerSemantics() {
        Result<MemberView> rejected = Result.failure(FailureCode.MALFORMED_INPUT, "invalid email");
        assertTrue(rejected.isFailure());
        assertEquals(FailureCode.MALFORMED_INPUT, rejected.failureCode().orElseThrow());

        MemberView view = new MemberView(new MemberId("member-1"), "Ada", "ada@example.test",
                MemberStatus.ACTIVE, Set.of(), 120);
        assertThrows(UnsupportedOperationException.class,
                () -> view.skills().add(new CategoryId("gardening")));
    }

    @Test
    void publishedFailureCodesRemainDistinctConsumerResults() {
        Result<MemberView> suspendedMember =
                Result.failure(FailureCode.SUSPENDED_MEMBER, "suspended");
        Result<EngagementView> insufficientAvailableCredits =
                Result.failure(FailureCode.INSUFFICIENT_AVAILABLE_CREDITS, "hold floor");
        Result<CommunityGrantView> insufficientCommunityFundCredits =
                Result.failure(FailureCode.INSUFFICIENT_COMMUNITY_FUND_CREDITS, "fund balance");
        Result<EngagementView> notParticipant =
                Result.failure(FailureCode.NOT_PARTICIPANT, "outside engagement");
        Result<EngagementView> invalidState =
                Result.failure(FailureCode.INVALID_STATE, "invalid transition");
        Result<EngagementView> cancellationClosed =
                Result.failure(FailureCode.CANCELLATION_CLOSED, "date boundary");

        assertEquals(FailureCode.SUSPENDED_MEMBER, suspendedMember.failureCode().orElseThrow());
        assertEquals(FailureCode.INSUFFICIENT_AVAILABLE_CREDITS,
                insufficientAvailableCredits.failureCode().orElseThrow());
        assertEquals(FailureCode.INSUFFICIENT_COMMUNITY_FUND_CREDITS,
                insufficientCommunityFundCredits.failureCode().orElseThrow());
        assertEquals(FailureCode.NOT_PARTICIPANT, notParticipant.failureCode().orElseThrow());
        assertEquals(FailureCode.INVALID_STATE, invalidState.failureCode().orElseThrow());
        assertEquals(FailureCode.CANCELLATION_CLOSED, cancellationClosed.failureCode().orElseThrow());
        assertTrue(Set.of(FailureCode.values()).containsAll(Set.of(
                suspendedMember.failureCode().orElseThrow(),
                insufficientAvailableCredits.failureCode().orElseThrow(),
                insufficientCommunityFundCredits.failureCode().orElseThrow(),
                notParticipant.failureCode().orElseThrow(),
                invalidState.failureCode().orElseThrow(),
                cancellationClosed.failureCode().orElseThrow())));
    }
}
