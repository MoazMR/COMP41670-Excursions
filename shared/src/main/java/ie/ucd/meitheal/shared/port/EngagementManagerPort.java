package ie.ucd.meitheal.shared.port;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.RequestView;
import java.time.LocalDate;
import java.util.List;

/**
 * Engagement owner for requests, proposals, and engagements. Service-coordinated writes across
 * owners must preserve observable state on rejection or uncommitted technical failure; the latter
 * is reported as ATOMIC_OPERATION_FAILED and is retryable.
 */
public interface EngagementManagerPort {
    Result<RequestView> createRequest(MemberId requesterId, RequestId requestId,
            CategoryId categoryId, int durationMinutes, LocalDate windowStart, LocalDate windowEnd);

    Result<RequestView> findRequest(RequestId requestId);

    /** Read-only query returning an immutable candidate snapshot. */
    Result<List<RequestView>> matchingRequests(MemberId helperId);

    Result<ProposalView> submitProposal(MemberId helperId, RequestId requestId,
            ProposalId proposalId, LocalDate proposedOn);

    Result<ProposalView> rejectProposal(MemberId requesterId, ProposalId proposalId);

    Result<ProposalView> withdrawProposal(MemberId helperId, ProposalId proposalId);

    Result<ProposalView> findProposal(MemberId requesterId, ProposalId proposalId);

    Result<List<ProposalView>> proposalsForRequest(MemberId requesterId, RequestId requestId);

    /** Returns an immutable snapshot of proposals owned by the specified helper. */
    Result<List<ProposalView>> proposalsForHelper(MemberId helperId);

    Result<EngagementView> findEngagement(MemberId participantId, EngagementId engagementId);

    Result<List<EngagementView>> engagementsForMember(MemberId memberId);
}
