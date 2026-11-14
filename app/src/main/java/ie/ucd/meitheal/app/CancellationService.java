package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.EngagementView;

/** Public caller boundary for cancelling an engagement. */
public interface CancellationService extends ConfirmationService {
    Result<EngagementView> cancelEngagement(MemberId participantId, EngagementId engagementId);
}
