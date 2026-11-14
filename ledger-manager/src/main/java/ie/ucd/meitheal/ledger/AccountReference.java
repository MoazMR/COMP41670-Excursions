package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.shared.id.MemberId;

import java.util.Objects;

/** Identifies the system account, a member account, or the community fund. */
public final class AccountReference {
    public static final AccountReference SYSTEM = new AccountReference(null);
    public static final AccountReference FUND = new AccountReference(null, true);

    private final MemberId memberId;
    private final boolean fund;

    private AccountReference(MemberId memberId) {
        this(memberId, false);
    }

    private AccountReference(MemberId memberId, boolean fund) {
        this.memberId = memberId;
        this.fund = fund;
    }

    public static AccountReference system() {
        return SYSTEM;
    }

    public static AccountReference fund() {
        return FUND;
    }

    public static AccountReference member(MemberId memberId) {
        return new AccountReference(Objects.requireNonNull(memberId, "memberId"));
    }

    public boolean isSystem() {
        return memberId == null && !fund;
    }

    public boolean isFund() {
        return fund;
    }

    public MemberId memberId() {
        if (isFund()) {
            throw new IllegalStateException("fund account has no member ID");
        }
        if (isSystem()) {
            throw new IllegalStateException("system account has no member ID");
        }
        return memberId;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof AccountReference reference
                && fund == reference.fund
                && Objects.equals(memberId, reference.memberId);
    }

    @Override
    public int hashCode() {
        return fund ? 1 : Objects.hashCode(memberId);
    }

    @Override
    public String toString() {
        return isSystem() ? "SYSTEM" : fund ? "FUND" : "MEMBER(" + memberId.value() + ")";
    }
}
