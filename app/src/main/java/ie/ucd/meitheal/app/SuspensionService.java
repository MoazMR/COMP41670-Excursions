package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.MemberView;

/** Public caller boundary for suspending and reinstating members. */
public interface SuspensionService extends CancellationService {
    Result<MemberView> suspendMember(MemberId memberId);

    Result<MemberView> reinstateMember(MemberId memberId);
}
