package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.ledger.local.MemberId;
import ie.ucd.meitheal.ledger.local.TransactionId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** In-memory append-only ledger for the one-time member welcome grant. */
public final class LedgerManager {
    private static final long WELCOME_GRANT_AMOUNT = 120L;

    private final Supplier<TransactionId> transactionIdGenerator;
    private final Supplier<LocalDate> dateGenerator;
    private final List<Transaction> transactions = new ArrayList<>();
    private final Set<TransactionId> transactionIds = new HashSet<>();

    public LedgerManager() {
        this(TransactionIdGenerator::random, LocalDate::now);
    }

    public LedgerManager(
            Supplier<TransactionId> transactionIdGenerator,
            Supplier<LocalDate> dateGenerator) {
        this.transactionIdGenerator = Objects.requireNonNull(transactionIdGenerator, "transactionIdGenerator");
        this.dateGenerator = Objects.requireNonNull(dateGenerator, "dateGenerator");
    }

    public synchronized Transaction grantWelcome(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId");
        AccountReference creditor = AccountReference.member(memberId);
        if (transactions.stream().anyMatch(transaction ->
                transaction.type() == TransactionType.WELCOME_GRANT
                        && transaction.creditor().equals(creditor))) {
            throw new IllegalStateException("welcome grant already exists for member");
        }

        Transaction transaction = new Transaction(
                Objects.requireNonNull(transactionIdGenerator.get(), "generated transaction ID"),
                AccountReference.SYSTEM,
                creditor,
                WELCOME_GRANT_AMOUNT,
                TransactionType.WELCOME_GRANT,
                Objects.requireNonNull(dateGenerator.get(), "generated date"));
        append(transaction);
        return transaction;
    }

    private synchronized void append(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (!transactionIds.add(transaction.transactionId())) {
            throw new IllegalStateException("duplicate transaction ID: " + transaction.transactionId());
        }
        transactions.add(transaction);
    }

    public synchronized long balance(AccountReference account) {
        Objects.requireNonNull(account, "account");
        return transactions.stream()
                .mapToLong(transaction -> {
                    if (transaction.creditor().equals(account)) {
                        return transaction.amount();
                    }
                    if (transaction.debtor().equals(account)) {
                        return -transaction.amount();
                    }
                    return 0L;
                })
                .sum();
    }

    public synchronized long balanceOf(MemberId memberId) {
        return balance(AccountReference.member(Objects.requireNonNull(memberId, "memberId")));
    }

    public synchronized List<Transaction> history() {
        return List.copyOf(transactions);
    }

    public synchronized List<Transaction> history(MemberId memberId) {
        AccountReference member = AccountReference.member(Objects.requireNonNull(memberId, "memberId"));
        return transactions.stream()
                .filter(transaction ->
                        transaction.debtor().equals(member)
                                || transaction.creditor().equals(member))
                .toList();
    }

    public synchronized List<Transaction> transactions() {
        return history();
    }

    private static final class TransactionIdGenerator {
        private TransactionIdGenerator() {
        }

        private static TransactionId random() {
            return new TransactionId(java.util.UUID.randomUUID().toString());
        }
    }
}
