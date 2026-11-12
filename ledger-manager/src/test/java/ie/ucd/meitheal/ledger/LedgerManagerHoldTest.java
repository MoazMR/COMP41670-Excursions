package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

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

    private void assertMalformedHold(EngagementId engagementId, MemberId requester, MemberId helper, int duration) {
        assertEquals(FailureCode.MALFORMED_INPUT,
                ledger.withHold(engagementId, requester, helper, duration,
                        ignored -> fail("malformed hold reached publication")).failureCode().orElseThrow());
    }
}
