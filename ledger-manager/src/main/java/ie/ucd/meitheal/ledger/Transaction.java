package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.ledger.local.TransactionId;

import java.time.LocalDate;
import java.util.Objects;

/** Immutable ledger transaction. Amounts are whole credits. */
public record Transaction(
        TransactionId transactionId,
        AccountReference debtor,
        AccountReference creditor,
        long amount,
        TransactionType type,
        LocalDate date) {

    public Transaction {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(creditor, "creditor");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(date, "date");
    }

    public TransactionId id() {
        return transactionId;
    }
}
