package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** In-memory append-only ledger with separate engagement credit holds. */
public final class LedgerManager {
    private static final long WELCOME_GRANT_AMOUNT = 120L;
    private static final long AVAILABLE_CREDIT_FLOOR = -180L;

    private final Object lock;
    private final Supplier<TransactionId> transactionIdGenerator;
    private final Supplier<LocalDate> dateGenerator;
    private List<Transaction> transactions = new ArrayList<>();
    private Set<TransactionId> transactionIds = new HashSet<>();
    private Map<EngagementId, Hold> holds = new HashMap<>();
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

    /**
     * Offers the candidate balance to the callback under the common lock.
     * The hold is published after the callback returns a successful result.
     */
    public <T> Result<T> withHold(
            EngagementId engagementId,
            MemberId requesterId,
            MemberId helperId,
            int durationMinutes,
            Function<BalanceView, Result<T>> beforePublish) {
        synchronized (lock) {
            ensureWritesAllowed();
            Objects.requireNonNull(beforePublish, "beforePublish");
            if (engagementId == null || requesterId == null || helperId == null || requesterId.equals(helperId)
                    || durationMinutes < 1 || durationMinutes > 480) {
                return Result.failure(FailureCode.MALFORMED_INPUT,
                        "valid engagement, distinct participants, and duration are required");
            }
            if (holds.containsKey(engagementId)) {
                return Result.failure(FailureCode.DUPLICATE, "hold already exists for engagement");
            }

            long amount = durationMinutes + durationMinutes * 5L / 100L;
            BalanceView current = balanceView(requesterId);
            long held = Math.addExact(current.held(), amount);
            long available = Math.subtractExact(current.ledgerBalance(), held);
            if (available < AVAILABLE_CREDIT_FLOOR) {
                return Result.failure(FailureCode.INSUFFICIENT_AVAILABLE_CREDITS,
                        "hold would exceed available-credit floor");
            }
            BalanceView candidate = new BalanceView(requesterId, current.ledgerBalance(), held, available);
            Map<EngagementId, Hold> nextHolds = new HashMap<>(holds);
            nextHolds.put(engagementId, new Hold(requesterId, helperId, durationMinutes, amount));

            publicationCallbackActive = true;
            try {
                Result<T> result = Objects.requireNonNull(beforePublish.apply(candidate), "publication result");
                if (result.isSuccess()) {
                    holds = nextHolds;
                }
                return result;
            } finally {
                publicationCallbackActive = false;
            }
        }
    }

    private record Hold(MemberId requesterId, MemberId helperId, int durationMinutes, long amount) { }

    public BalanceView balanceView(MemberId memberId) {
        synchronized (lock) {
            long ledgerBalance = balanceOf(memberId);
            long held = 0L;
            for (Hold hold : holds.values()) {
                if (hold.requesterId().equals(memberId)) {
                    held = Math.addExact(held, hold.amount());
                }
            }
            return new BalanceView(memberId, ledgerBalance, held, Math.subtractExact(ledgerBalance, held));
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
