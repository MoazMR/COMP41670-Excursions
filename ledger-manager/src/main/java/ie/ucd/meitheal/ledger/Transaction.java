package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.id.EngagementId;

import java.time.LocalDate;
import java.util.Objects;

/** Immutable ledger transaction. Amounts are whole credits. */
public record Transaction(
        TransactionId transactionId,
        AccountReference debtor,
        AccountReference creditor,
        long amount,
        TransactionType type,
        LocalDate date,
        EngagementId engagementId) {

    public Transaction(
            TransactionId transactionId,
            AccountReference debtor,
            AccountReference creditor,
            long amount,
            TransactionType type,
            LocalDate date) {
        this(transactionId, debtor, creditor, amount, type, date, null);
    }

    public Transaction {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(creditor, "creditor");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(date, "date");
        if ((type == TransactionType.SERVICE_PAYMENT || type == TransactionType.LEVY)
                != (engagementId != null)) {
            throw new IllegalArgumentException("settlement entries require an engagement ID");
        }
    }

    public TransactionId id() {
        return transactionId;
    }
}
