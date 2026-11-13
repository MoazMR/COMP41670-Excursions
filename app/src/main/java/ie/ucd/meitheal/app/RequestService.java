package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.RequestView;
import java.time.LocalDate;
import java.util.List;

/** Member actions available for offering skills, requesting help and proposing a date. */
public interface RequestService extends RegistrationService {
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

    /** Read-only matching query; result list is an immutable candidate snapshot. */
    Result<List<RequestView>> matchingRequests(MemberId helperId);

    /**
     * A suspended member fails with SUSPENDED_MEMBER when submitting a proposal.
     */
    Result<ProposalView> submitProposal(MemberId helperId, RequestId requestId, LocalDate proposedDate);
}
