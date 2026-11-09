package ie.ucd.meitheal.shared.port;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.TransactionView;
import java.util.List;

/**
 * Ledger owner for balances and append-only transaction history. Service-coordinated writes
 * across owners must preserve observable state on rejection or uncommitted technical failure;
 * the latter is reported as ATOMIC_OPERATION_FAILED and is retryable.
 */
public interface LedgerManagerPort {
    Result<BalanceView> balance(MemberId memberId);

    /** Returns an immutable snapshot of the member's transaction history. */
    Result<List<TransactionView>> history(MemberId memberId);

    /** Returns ledger-owned credit totals; membership supplies the member count. */
    LedgerCreditTotals reportTotals();

    record LedgerCreditTotals(long memberBalanceTotal, long fundBalance) {
        public LedgerCreditTotals {
            if (fundBalance < 0) {
                throw new IllegalArgumentException("fund balance must be non-negative");
            }
        }
    }
}
