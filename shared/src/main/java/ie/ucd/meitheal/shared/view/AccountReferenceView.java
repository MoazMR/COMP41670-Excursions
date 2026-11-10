package ie.ucd.meitheal.shared.view;

import ie.ucd.meitheal.shared.id.MemberId;

public record AccountReferenceView(AccountType type, MemberId memberId) {
    public enum AccountType {
        MEMBER,
        FUND,
        SYSTEM
    }

    public AccountReferenceView {
        if (type == null) {
            throw new NullPointerException("type");
        }
        if ((type == AccountType.MEMBER) != (memberId != null)) {
            throw new IllegalArgumentException("member reference required only for member account");
        }
    }
}
