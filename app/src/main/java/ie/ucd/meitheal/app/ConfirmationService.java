package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.EngagementView;

/** Public caller boundary for confirming agreed engagements. */
public interface ConfirmationService extends SelectionService {
    Result<EngagementView> confirmEngagement(MemberId participantId, EngagementId engagementId);
}
