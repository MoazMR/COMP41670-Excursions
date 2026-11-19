package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.ProposalView;
import java.util.List;

/** Queries and pending proposal actions available to helpers and request owners. */
public interface ProposalManagementService extends RequestService {
    Result<List<ProposalView>> proposalsForHelper(MemberId helperId);

    Result<List<ProposalView>> proposalsForRequest(MemberId requesterId, RequestId requestId);

    Result<ProposalView> withdrawProposal(MemberId helperId, ProposalId proposalId);

    Result<ProposalView> rejectProposal(MemberId requesterId, ProposalId proposalId);
}
