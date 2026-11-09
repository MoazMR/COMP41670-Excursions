package ie.ucd.meitheal.shared.port;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.id.TransactionId;

/** Creates identifiers of the corresponding domain types. */
public interface IdFactory {
    MemberId newMemberId();

    RequestId newRequestId();

    ProposalId newProposalId();

    EngagementId newEngagementId();

    TransactionId newTransactionId();
}
