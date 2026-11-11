package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** In-memory append-only ledger for the one-time member welcome grant. */
public final class LedgerManager {
    private static final long WELCOME_GRANT_AMOUNT = 120L;

    private final Object lock;
    private final Supplier<TransactionId> transactionIdGenerator;
    private final Supplier<LocalDate> dateGenerator;
    private List<Transaction> transactions = new ArrayList<>();
    private Set<TransactionId> transactionIds = new HashSet<>();
    private boolean publicationCallbackActive;

    public LedgerManager(
            Supplier<TransactionId> transactionIdGenerator,
            Supplier<LocalDate> dateGenerator) {
        this(new Object(), transactionIdGenerator, dateGenerator);
    }

    public LedgerManager(
            Object lock,
            Supplier<TransactionId> transactionIdGenerator,
            Supplier<LocalDate> dateGenerator) {
        this.lock = Objects.requireNonNull(lock, "lock");
        this.transactionIdGenerator = Objects.requireNonNull(transactionIdGenerator, "transactionIdGenerator");
        this.dateGenerator = Objects.requireNonNull(dateGenerator, "dateGenerator");
    }

    public Transaction grantWelcome(MemberId memberId) {
        synchronized (lock) {
            ensureWritesAllowed();
            Objects.requireNonNull(memberId, "memberId");
            AccountReference creditor = AccountReference.member(memberId);
            ensureWelcomeGrantDoesNotExist(creditor);

            // Keep supplier evaluation here to preserve the legacy caller-visible ordering.
            publicationCallbackActive = true;
            try {
                TransactionId transactionId =
                        Objects.requireNonNull(transactionIdGenerator.get(), "generated transaction ID");
                LocalDate date = Objects.requireNonNull(dateGenerator.get(), "generated date");
                PreparedGrant prepared = prepareWelcomeGrant(transactionId, date, creditor);
                publish(prepared);
                return prepared.transaction();
            } finally {
                publicationCallbackActive = false;
            }
        }
    }

    public <T> T withWelcomeGrant(
            MemberId memberId,
            TransactionId transactionId,
            LocalDate date,
            Function<Transaction, T> beforePublish) {
        synchronized (lock) {
            ensureWritesAllowed();
            Objects.requireNonNull(memberId, "memberId");
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(beforePublish, "beforePublish");
            AccountReference creditor = AccountReference.member(memberId);
            ensureWelcomeGrantDoesNotExist(creditor);

            PreparedGrant prepared = prepareWelcomeGrant(transactionId, date, creditor);

            publicationCallbackActive = true;
            try {
                T result = beforePublish.apply(prepared.transaction());
                publish(prepared);
                return result;
            } finally {
                publicationCallbackActive = false;
            }
        }
    }

    private PreparedGrant prepareWelcomeGrant(
            TransactionId transactionId,
            LocalDate date,
            AccountReference creditor) {
        Transaction transaction = createWelcomeGrantTransaction(transactionId, date, creditor);
        List<Transaction> nextTransactions = new ArrayList<>(transactions);
        Set<TransactionId> nextTransactionIds = new HashSet<>(transactionIds);
        if (!nextTransactionIds.add(transaction.transactionId())) {
            throw new IllegalStateException("duplicate transaction ID: " + transaction.transactionId());
        }
        nextTransactions.add(transaction);
        return new PreparedGrant(transaction, nextTransactions, nextTransactionIds);
    }

    private void publish(PreparedGrant prepared) {
        transactions = prepared.transactions();
        transactionIds = prepared.transactionIds();
    }

    private record PreparedGrant(
            Transaction transaction,
            List<Transaction> transactions,
            Set<TransactionId> transactionIds) { }

    private Transaction createWelcomeGrantTransaction(
            TransactionId transactionId,
            LocalDate date,
            AccountReference creditor) {
        return new Transaction(
                transactionId,
                AccountReference.SYSTEM,
                creditor,
                WELCOME_GRANT_AMOUNT,
                TransactionType.WELCOME_GRANT,
                date);
    }

    private void ensureWelcomeGrantDoesNotExist(AccountReference creditor) {
        if (transactions.stream().anyMatch(transaction ->
                transaction.type() == TransactionType.WELCOME_GRANT
                        && transaction.creditor().equals(creditor))) {
            throw new IllegalStateException("welcome grant already exists for member");
        }
    }

    private void ensureWritesAllowed() {
        if (publicationCallbackActive) {
            throw new IllegalStateException("ledger writes are not allowed during publication callback");
        }
    }

    public long balance(AccountReference account) {
        synchronized (lock) {
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
    }

    public long balanceOf(MemberId memberId) {
        synchronized (lock) {
            return balance(AccountReference.member(Objects.requireNonNull(memberId, "memberId")));
        }
    }

    public List<Transaction> history() {
        synchronized (lock) {
            return List.copyOf(transactions);
        }
    }

    public List<Transaction> history(MemberId memberId) {
        synchronized (lock) {
            AccountReference member = AccountReference.member(Objects.requireNonNull(memberId, "memberId"));
            return transactions.stream()
                    .filter(transaction ->
                            transaction.debtor().equals(member)
                                    || transaction.creditor().equals(member))
                    .toList();
        }
    }

    public List<Transaction> transactions() {
        synchronized (lock) {
            return history();
        }
    }
}
