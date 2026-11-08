package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.ledger.local.MemberId;

import java.util.Objects;

/** Identifies either the system account or a member account. */
public final class AccountReference {
    public static final AccountReference SYSTEM = new AccountReference(null);

    private final MemberId memberId;

    private AccountReference(MemberId memberId) {
        this.memberId = memberId;
    }

    public static AccountReference system() {
        return SYSTEM;
    }

    public static AccountReference member(MemberId memberId) {
        return new AccountReference(Objects.requireNonNull(memberId, "memberId"));
    }

    public boolean isSystem() {
        return memberId == null;
    }

    public MemberId memberId() {
        if (isSystem()) {
            throw new IllegalStateException("system account has no member ID");
        }
        return memberId;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof AccountReference reference
                && Objects.equals(memberId, reference.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(memberId);
    }

    @Override
    public String toString() {
        return isSystem() ? "SYSTEM" : "MEMBER(" + memberId.value() + ")";
    }
}
