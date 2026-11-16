package ie.ucd.meitheal.ledger;

public enum TransactionType {
    WELCOME_GRANT,
    SERVICE_PAYMENT,
    LEVY,
    COMMUNITY_GRANT;

    ie.ucd.meitheal.shared.model.TransactionType toSharedType() {
        return ie.ucd.meitheal.shared.model.TransactionType.valueOf(name());
    }
}
