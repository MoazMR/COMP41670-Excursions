package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LedgerManagerHoldTest {
    private static final MemberId ALICE = new MemberId("alice");
    private static final MemberId BOB = new MemberId("bob");
    private static final MemberId CAROL = new MemberId("carol");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    private final Object lock = new Object();
    private final AtomicInteger issuedIds = new AtomicInteger();
    private final LedgerManager ledger =
            new LedgerManager(lock, () -> new TransactionId("t" + issuedIds.incrementAndGet()), () -> DATE);

    @Test
    void multipleHoldsReachTheFloorWithoutPostingTransactions() {
        ledger.grantWelcome(ALICE);
        var originalHistory = ledger.history();
        var before = ledger.balanceView(ALICE);
        assertEquals(new BalanceView(ALICE, 120, 0, 120), before);

        assertEquals(new BalanceView(ALICE, 120, 105, 15),
                ledger.withHold(new EngagementId("first"), ALICE, BOB, 100, Result::success)
                        .value().orElseThrow());
        assertEquals(new BalanceView(ALICE, 120, 300, -180),
                ledger.withHold(new EngagementId("second"), ALICE, CAROL, 186, candidate -> {
                    assertTrue(Thread.holdsLock(lock));
                    assertEquals(new BalanceView(ALICE, 120, 105, 15), ledger.balanceView(ALICE));
                    return Result.success(candidate);
                }).value().orElseThrow());

        assertEquals(FailureCode.INSUFFICIENT_AVAILABLE_CREDITS,
                ledger.withHold(new EngagementId("third"), ALICE, CAROL, 1,
                        ignored -> fail("hold below the floor reached publication")).failureCode().orElseThrow());
        assertEquals(FailureCode.DUPLICATE,
                ledger.withHold(new EngagementId("first"), BOB, ALICE, 1,
                        ignored -> fail("duplicate hold reached publication")).failureCode().orElseThrow());
        assertEquals(new BalanceView(ALICE, 120, 300, -180), ledger.balanceView(ALICE));
        assertEquals(new BalanceView(BOB, 0, 0, 0), ledger.balanceView(BOB));
        assertEquals(new BalanceView(CAROL, 0, 0, 0), ledger.balanceView(CAROL));
        assertEquals(new BalanceView(ALICE, 120, 0, 120), before);
        assertEquals(originalHistory, ledger.history());
        assertEquals(-120, ledger.balance(AccountReference.SYSTEM));
        assertEquals(1, issuedIds.get());
    }

    @Test
    void malformedHoldsAreRejectedBeforePublication() {
        EngagementId engagementId = new EngagementId("offer");
        assertMalformedHold(null, ALICE, BOB, 1);
        assertMalformedHold(engagementId, null, BOB, 1);
        assertMalformedHold(engagementId, ALICE, null, 1);
        assertMalformedHold(engagementId, ALICE, new MemberId("alice"), 1);
        assertMalformedHold(engagementId, ALICE, BOB, 0);
        assertMalformedHold(engagementId, ALICE, BOB, 481);
        assertEquals(new BalanceView(ALICE, 0, 0, 0), ledger.balanceView(ALICE));
        assertTrue(ledger.history().isEmpty());
    }

    @Test
    void callbackFailuresPreserveTheHoldKeyForRetry() {
        EngagementId engagementId = new EngagementId("retry");
        Result<BalanceView> failure = Result.failure(FailureCode.ATOMIC_OPERATION_FAILED, "owner declined");
        assertSame(failure, ledger.withHold(engagementId, ALICE, BOB, 19, ignored -> failure));
        IllegalStateException problem = new IllegalStateException("publication failed");
        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> ledger.withHold(engagementId, ALICE, BOB, 19, ignored -> { throw problem; })));
        assertEquals(new BalanceView(ALICE, 0, 0, 0), ledger.balanceView(ALICE));
        assertEquals(new BalanceView(ALICE, 0, 19, -19),
                ledger.withHold(engagementId, ALICE, BOB, 19, Result::success).value().orElseThrow());
        ledger.grantWelcome(ALICE);
        assertEquals(new BalanceView(ALICE, 120, 19, 101), ledger.balanceView(ALICE));
    }

    @Test
    void ledgerWritesCannotReenterEitherPublicationCallback() {
        EngagementId engagementId = new EngagementId("guarded");
        ledger.withWelcomeGrant(ALICE, new TransactionId("welcome"), DATE, transaction -> {
            assertThrows(IllegalStateException.class,
                    () -> ledger.withHold(engagementId, ALICE, BOB, 20, Result::success));
            return transaction;
        });
        assertTrue(ledger.withHold(engagementId, ALICE, BOB, 20, candidate -> {
            assertThrows(IllegalStateException.class, () -> ledger.grantWelcome(BOB));
            assertThrows(IllegalStateException.class,
                    () -> ledger.withHold(new EngagementId("nested"), ALICE, BOB, 1, Result::success));
            return Result.success(candidate);
        }).isSuccess());
        assertEquals(new BalanceView(ALICE, 120, 21, 99), ledger.balanceView(ALICE));
        assertEquals(new BalanceView(BOB, 0, 0, 0), ledger.balanceView(BOB));
        assertEquals(1, ledger.history().size());
        assertEquals(0, issuedIds.get());
    }

    @Test
    void settlementPostsDirectEngagementLinkedEntriesAndKeepsOtherHolds() {
        AtomicInteger settlementIds = new AtomicInteger();
        EngagementId settled = new EngagementId("settled");
        EngagementId surviving = new EngagementId("surviving");
        ledger.grantWelcome(ALICE);
        ledger.withHold(settled, ALICE, BOB, 19, ignored -> Result.success("ok"));
        ledger.withHold(surviving, ALICE, CAROL, 20, ignored -> Result.success("ok"));
        List<Transaction> before = ledger.history();

        assertTrue(ledger.withSettlement(settled, DATE,
                () -> new TransactionId("settlement-19-" + settlementIds.incrementAndGet()), () -> {
            assertEquals(before, ledger.history());
            assertEquals(120, ledger.balanceOf(ALICE));
            assertEquals(0, ledger.balanceOf(BOB));
            assertEquals(-120, ledger.balance(AccountReference.SYSTEM));
            return Result.success("ok");
        }).isSuccess());

        List<Transaction> entries = ledger.history().subList(before.size(), ledger.history().size());
        assertEquals(1, entries.size());
        Transaction payment = entries.get(0);
        assertEquals(new TransactionId("settlement-19-1"), payment.transactionId());
        assertEquals(DATE, payment.date());
        assertEquals(settled, payment.engagementId());
        assertEquals(AccountReference.member(ALICE), payment.debtor());
        assertEquals(AccountReference.member(BOB), payment.creditor());
        assertEquals(TransactionType.SERVICE_PAYMENT, payment.type());
        assertEquals(19, payment.amount());
        assertEquals(new BalanceView(ALICE, 101, 21, 80), ledger.balanceView(ALICE));
        assertEquals(19, ledger.balanceOf(BOB));
        assertEquals(-120, ledger.balance(AccountReference.SYSTEM));

        EngagementId twenty = new EngagementId("twenty");
        ledger.withHold(twenty, ALICE, CAROL, 20, ignored -> Result.success("ok"));
        LocalDate suppliedDate = DATE.plusDays(7);
        assertTrue(ledger.withSettlement(twenty, suppliedDate,
                () -> new TransactionId("settlement-20-" + settlementIds.incrementAndGet()), () -> Result.success("ok"))
                .isSuccess());
        List<Transaction> twentyEntries = ledger.history().subList(entries.size() + before.size(), ledger.history().size());
        assertEquals(2, twentyEntries.size());
        assertEquals(List.of(new TransactionId("settlement-20-2"), new TransactionId("settlement-20-3")),
                twentyEntries.stream().map(Transaction::transactionId).toList());
        assertEquals(List.of(TransactionType.SERVICE_PAYMENT, TransactionType.LEVY),
                twentyEntries.stream().map(Transaction::type).toList());
        assertEquals(List.of(20L, 1L), twentyEntries.stream().map(Transaction::amount).toList());
        assertEquals(List.of(suppliedDate, suppliedDate), twentyEntries.stream().map(Transaction::date).toList());
        assertTrue(twentyEntries.stream().allMatch(entry -> entry.engagementId().equals(twenty)));
        assertEquals(AccountReference.member(ALICE), twentyEntries.get(0).debtor());
        assertEquals(AccountReference.member(CAROL), twentyEntries.get(0).creditor());
        assertEquals(AccountReference.member(ALICE), twentyEntries.get(1).debtor());
        assertEquals(AccountReference.FUND, twentyEntries.get(1).creditor());
        assertEquals(80, ledger.balanceOf(ALICE));
        assertEquals(20, ledger.balanceOf(CAROL));
        assertEquals(1, ledger.balance(AccountReference.FUND));
        List<Transaction> fullHistory = ledger.history();
        assertEquals(-120, ledger.balance(AccountReference.SYSTEM));
        assertEquals(new BalanceView(ALICE, 80, 21, 59), ledger.balanceView(ALICE));
        assertEquals(new BalanceView(CAROL, 20, 0, 20), ledger.balanceView(CAROL));
        assertEquals(fullHistory, ledger.history(ALICE));
        assertEquals(List.of(payment), ledger.history(BOB));
        assertEquals(List.of(twentyEntries.get(0)), ledger.history(CAROL));
        assertTrue(ledger.history(new MemberId("fund")).isEmpty());
        assertEquals(FailureCode.DUPLICATE, ledger.withHold(settled, ALICE, BOB, 19, ignored ->
                fail("consumed engagement reached publication")).failureCode().orElseThrow());
        assertEquals(FailureCode.DUPLICATE, ledger.withHold(surviving, ALICE, BOB, 19, ignored ->
                fail("surviving hold was replaced")).failureCode().orElseThrow());
    }

    @Test
    void missingHoldAndSettlementFailuresPreserveStateAndAllowRetry() {
        EngagementId missing = new EngagementId("missing");
        AtomicInteger ids = new AtomicInteger();
        AtomicReference<Boolean> callbackCalled = new AtomicReference<>(false);
        List<Transaction> initialHistory = ledger.history();
        BalanceView initialAlice = ledger.balanceView(ALICE);
        assertEquals(FailureCode.INVALID_STATE,
                ledger.withSettlement(missing, DATE, () -> {
                    ids.incrementAndGet();
                    return new TransactionId("unused");
                }, () -> {
                    callbackCalled.set(true);
                    return Result.success("bad");
                }).failureCode().orElseThrow());
        assertEquals(0, ids.get());
        assertEquals(false, callbackCalled.get());
        assertEquals(initialHistory, ledger.history());
        assertEquals(initialAlice, ledger.balanceView(ALICE));

        EngagementId engagementId = new EngagementId("retry-settlement");
        ledger.grantWelcome(ALICE);
        ledger.withHold(engagementId, ALICE, BOB, 20, Result::success);
        List<Transaction> heldHistory = ledger.history();
        BalanceView heldAlice = ledger.balanceView(ALICE);
        BalanceView heldBob = ledger.balanceView(BOB);
        Result<String> refusal = Result.failure(FailureCode.ATOMIC_OPERATION_FAILED, "refused");
        AtomicReference<Boolean> refusedCallback = new AtomicReference<>(false);
        assertSame(refusal, ledger.withSettlement(engagementId, DATE,
                () -> new TransactionId("refused-" + ids.incrementAndGet()), () -> {
                    refusedCallback.set(true);
                    assertEquals(heldHistory, ledger.history());
                    assertEquals(heldAlice, ledger.balanceView(ALICE));
                    return refusal;
                }));
        assertEquals(true, refusedCallback.get());
        assertEquals(2, ids.get());
        assertEquals(heldHistory, ledger.history());
        assertEquals(heldAlice, ledger.balanceView(ALICE));
        assertEquals(heldBob, ledger.balanceView(BOB));

        IllegalStateException problem = new IllegalStateException("callback failed");
        AtomicReference<Boolean> thrownCallback = new AtomicReference<>(false);
        assertSame(problem, assertThrows(IllegalStateException.class, () -> ledger.withSettlement(
                engagementId, DATE, () -> new TransactionId("thrown-" + ids.incrementAndGet()), () -> {
                    thrownCallback.set(true);
                    assertEquals(heldHistory, ledger.history());
                    assertEquals(heldAlice, ledger.balanceView(ALICE));
                    throw problem;
                })));
        assertEquals(true, thrownCallback.get());
        assertEquals(4, ids.get());
        assertEquals(heldHistory, ledger.history());
        assertEquals(heldAlice, ledger.balanceView(ALICE));
        assertEquals(heldBob, ledger.balanceView(BOB));

        AtomicInteger retryIds = new AtomicInteger();
        assertTrue(ledger.withSettlement(engagementId, DATE,
                () -> new TransactionId("retry-" + retryIds.incrementAndGet()), () -> Result.success("ok")).isSuccess());
        assertEquals(2, retryIds.get());
        assertEquals(FailureCode.INVALID_STATE, ledger.withSettlement(engagementId, DATE,
                () -> fail("consumed hold requested an ID"), () -> fail("consumed hold reached publication")).failureCode().orElseThrow());
        assertEquals(FailureCode.DUPLICATE, ledger.withHold(engagementId, ALICE, BOB, 20, ignored ->
                fail("settled engagement was reusable")).failureCode().orElseThrow());
    }

    @Test
    void settlementRejectsDuplicateIdsBeforeCallbackAndBlocksReentrantWrites() {
        EngagementId duplicateId = new EngagementId("duplicate-id-settlement");
        ledger.grantWelcome(ALICE);
        ledger.withHold(duplicateId, ALICE, BOB, 20, ignored -> Result.success("ok"));
        List<Transaction> before = ledger.history();
        BalanceView aliceBefore = ledger.balanceView(ALICE);
        AtomicInteger suppliedIds = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> ledger.withSettlement(duplicateId, DATE,
                () -> {
                    suppliedIds.incrementAndGet();
                    return new TransactionId("settle-a");
                }, () -> {
                    callbacks.incrementAndGet();
                    return Result.success("unexpected");
                }));
        assertEquals(2, suppliedIds.get());
        assertEquals(0, callbacks.get());
        assertEquals(before, ledger.history());
        assertEquals(aliceBefore, ledger.balanceView(ALICE));

        EngagementId guarded = new EngagementId("guarded-settlement");
        ledger.withHold(guarded, ALICE, CAROL, 19, ignored -> Result.success("ok"));
        AtomicInteger ids = new AtomicInteger();
        assertTrue(ledger.withSettlement(guarded, DATE, () -> {
            assertThrows(IllegalStateException.class,
                    () -> ledger.withHold(new EngagementId("from-id-supplier"), ALICE, BOB, 1, ignored -> Result.success("ok")));
            return new TransactionId("guarded-" + ids.incrementAndGet());
        }, () -> {
            assertThrows(IllegalStateException.class,
                    () -> ledger.withSettlement(guarded, DATE, () -> new TransactionId("nested-id"), () -> Result.success("ok")));
            return Result.success("ok");
        }).isSuccess());
        assertEquals(1, ids.get());
    }

    private void assertMalformedHold(EngagementId engagementId, MemberId requester, MemberId helper, int duration) {
        assertEquals(FailureCode.MALFORMED_INPUT,
                ledger.withHold(engagementId, requester, helper, duration,
                        ignored -> fail("malformed hold reached publication")).failureCode().orElseThrow());
    }
}
