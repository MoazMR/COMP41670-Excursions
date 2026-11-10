package ie.ucd.meitheal.shared.view;

import ie.ucd.meitheal.shared.id.MemberId;

public record BalanceView(MemberId memberId, long ledgerBalance, long held, long available) {
    public BalanceView {
        if (memberId == null) {
            throw new NullPointerException("memberId");
        }
        if (held < 0) {
            throw new IllegalArgumentException("held must be non-negative");
        }
        if (Math.subtractExact(ledgerBalance, held) != available) {
            throw new IllegalArgumentException("available does not match ledger minus held");
        }
    }
}
