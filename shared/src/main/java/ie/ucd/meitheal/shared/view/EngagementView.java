package ie.ucd.meitheal.shared.view;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

public record EngagementView(
        EngagementId id,
        ProposalId proposalId,
        RequestId requestId,
        MemberId requesterId,
        MemberId helperId,
        int durationMinutes,
        LocalDate scheduledDate,
        EngagementStatus status,
        Set<MemberId> confirmationActors,
        MemberId cancellationActor,
        LocalDate cancellationDate,
        boolean lateCancellation) {

    public EngagementView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(helperId, "helperId");
        Objects.requireNonNull(scheduledDate, "scheduledDate");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(confirmationActors, "confirmationActors");
        if (durationMinutes < 1 || durationMinutes > 480) {
            throw new IllegalArgumentException("duration must be 1..480 minutes");
        }
        confirmationActors = Set.copyOf(confirmationActors);
        if (!confirmationActors.stream().allMatch(actor -> actor.equals(requesterId) || actor.equals(helperId))) {
            throw new IllegalArgumentException("confirmation actors must be engagement participants");
        }
        boolean hasCancellationDetails = cancellationActor != null && cancellationDate != null;
        if ((cancellationActor == null) != (cancellationDate == null)) {
            throw new IllegalArgumentException("cancellation actor and date must be supplied together");
        }
        if (lateCancellation && !hasCancellationDetails) {
            throw new IllegalArgumentException("late cancellation requires cancellation details");
        }
    }
}
