package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.CommunityGrantView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.ReportView;
import ie.ucd.meitheal.shared.view.RequestView;
import ie.ucd.meitheal.shared.view.TransactionView;
import java.time.LocalDate;
import java.util.List;

/**
 * Public caller boundary for membership, requests, engagements, and the community ledger.
 * Missing members and records are reported with the corresponding *_NOT_FOUND failure code;
 * malformed values are rejected without changing state. A repeated action that is no longer
 * valid in the current domain state returns a stable rejection such as DUPLICATE or INVALID_STATE.
 * Cross-owner operations preserve all affected observable state on rejection or an uncommitted
 * technical failure; the latter is reported as ATOMIC_OPERATION_FAILED and may be retried.
 */
public interface MeithealService extends RegistrationService {

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

    /** Returns an immutable catalogue snapshot. */
    List<CategoryView> categories();

    /**
     * A suspended member fails with SUSPENDED_MEMBER when publishing a skill offer.
     */
    Result<MemberView> offerSkill(MemberId memberId, CategoryId categoryId);

    /**
     * A suspended member fails with SUSPENDED_MEMBER when creating a request.
     */
    Result<RequestView> createRequest(MemberId requesterId, CategoryId categoryId,
            int durationMinutes, LocalDate windowStart, LocalDate windowEnd);

    Result<RequestView> findRequest(RequestId requestId);

    /** Read-only matching query; result list is an immutable candidate snapshot. */
    Result<List<RequestView>> matchingRequests(MemberId helperId);

    /**
     * A suspended member fails with SUSPENDED_MEMBER when submitting a proposal.
     */
    Result<ProposalView> submitProposal(MemberId helperId, RequestId requestId, LocalDate proposedDate);

    Result<List<ProposalView>> proposalsForRequest(MemberId requesterId, RequestId requestId);

    /** Returns an immutable snapshot of proposals owned by the helper. */
    Result<List<ProposalView>> proposalsForHelper(MemberId helperId);

    Result<ProposalView> rejectProposal(MemberId requesterId, ProposalId proposalId);

    Result<ProposalView> withdrawProposal(MemberId helperId, ProposalId proposalId);

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

    /**
     * A grant exceeding available community fund credits fails with
     * INSUFFICIENT_COMMUNITY_FUND_CREDITS.
     */
    Result<CommunityGrantView> applyForCommunityGrant(MemberId memberId, long amount);

    /** Returns immutable ledger history for a member. */
    Result<List<TransactionView>> memberLedgerHistory(MemberId memberId);

    /** Returns immutable engagement history visible to the specified participant. */
    Result<List<EngagementView>> ownEngagementHistory(MemberId memberId);

    ReportView report();

    LocalDate currentDate();

    /**
     * Changing the simulated date performs no business action. Same-day advance succeeds unchanged;
     * backward advance fails without changing the date.
     */
    Result<LocalDate> advanceDate(LocalDate date);
}
