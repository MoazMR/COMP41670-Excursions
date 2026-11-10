package ie.ucd.meitheal.shared.view;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import java.time.LocalDate;
import java.util.Objects;

public record ProposalView(
        ProposalId id,
        RequestId requestId,
        MemberId helperId,
        LocalDate proposedOn,
        ProposalStatus status) {

    public ProposalView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(helperId, "helperId");
        Objects.requireNonNull(proposedOn, "proposedOn");
        Objects.requireNonNull(status, "status");
    }
}
