package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.ProposalView;
import java.util.List;

/**
 * Public proposal selection operations. Selection requires an active requester and helper, an
 * open request, and a pending proposal owned by that request. The requester must have enough
 * available credits to place the duration-based hold while remaining at or above the ledger
 * floor. Rejections and failed publication preserve observable state.
 */
public interface SelectionService extends RequestService {
    Result<List<ProposalView>> proposalsForRequest(MemberId requesterId,
            RequestId requestId);

    Result<EngagementView> selectProposal(MemberId requesterId, ProposalId proposalId);
}
