package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.CommunityGrantView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.ReportView;
import ie.ucd.meitheal.shared.view.TransactionView;
import java.util.List;

/** Public caller boundary for community grants and financial history. */
public interface CommunityFinanceService extends SuspensionService {
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
}
