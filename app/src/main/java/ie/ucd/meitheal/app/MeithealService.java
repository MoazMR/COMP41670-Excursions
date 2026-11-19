package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.RequestView;

/**
 * Public caller boundary for membership, requests, engagements, and the community ledger.
 * Missing members and records are reported with the corresponding *_NOT_FOUND failure code;
 * malformed values are rejected without changing state. A repeated action that is no longer
 * valid in the current domain state returns a stable rejection such as DUPLICATE or INVALID_STATE.
 * Cross-owner operations preserve all affected observable state on rejection or an uncommitted
 * technical failure; the latter is reported as ATOMIC_OPERATION_FAILED and may be retried.
 */
public interface MeithealService extends ProposalManagementService, CommunityFinanceService, SimulatedDateService {

    /**
     * Unknown members fail with MEMBER_NOT_FOUND; repeating the current status is rejected.
     * Suspension blocks the affected member from new requests, skill offers, proposals, and
     * engagements. Selection requires both parties to be active. Suspension invalidates pending
     * proposals involving the suspended member.
     * It preserves agreed work: participants may still confirm or cancel it under ordinary rules
     * and receive credits for completed work. A failed suspension preserves observable membership
     * and proposal state.
     */
    Result<MemberView> suspendMember(MemberId memberId);

    /** Unknown members fail with MEMBER_NOT_FOUND; repeating the current status is rejected. */
    Result<MemberView> reinstateMember(MemberId memberId);

    Result<CategoryView> findCategory(CategoryId categoryId);

    Result<RequestView> findRequest(RequestId requestId);

    /**
     * Either suspended participant fails with SUSPENDED_MEMBER; a hold below the -180
     * available-credit floor fails with INSUFFICIENT_AVAILABLE_CREDITS.
     */
    Result<EngagementView> selectProposal(MemberId requesterId, ProposalId proposalId);

    /**
     * A nonparticipant fails with NOT_PARTICIPANT; a disallowed engagement status fails with
     * INVALID_STATE. A repeat confirmation by the same participant is rejected as DUPLICATE.
     */
    Result<EngagementView> confirmEngagement(MemberId participantId, EngagementId engagementId);

    /**
     * A nonparticipant fails with NOT_PARTICIPANT; a disallowed engagement status fails with
     * INVALID_STATE, and a closed cancellation date fails with CANCELLATION_CLOSED.
     */
    Result<EngagementView> cancelEngagement(MemberId participantId, EngagementId engagementId);
}
