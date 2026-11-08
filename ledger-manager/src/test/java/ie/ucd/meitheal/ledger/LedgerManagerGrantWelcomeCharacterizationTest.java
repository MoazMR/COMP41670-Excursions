package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.ledger.local.MemberId;
import ie.ucd.meitheal.ledger.local.TransactionId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerManagerGrantWelcomeCharacterizationTest {
    private static final MemberId ALICE = new MemberId("alice");
    private static final LocalDate GRANT_DATE = LocalDate.of(2026, 11, 8);

    @Test
    void successfulGrantInvokesTransactionIdSupplierBeforeDateSupplierExactlyOnce() {
        List<String> invocations = new ArrayList<>();
        LedgerManager ledger = new LedgerManager(
                () -> {
                    invocations.add("transaction-id");
                    return new TransactionId("grant-1");
                },
                () -> {
                    invocations.add("date");
                    return GRANT_DATE;
                });

        ledger.grantWelcome(ALICE);

        assertEquals(List.of("transaction-id", "date"), invocations);
    }

    @Test
    void duplicateGrantRejectsBeforeInvokingSuppliersAgainAndPreservesState() {
        AtomicInteger transactionIdInvocations = new AtomicInteger();
        AtomicInteger dateInvocations = new AtomicInteger();
        LedgerManager ledger = new LedgerManager(
                () -> {
                    transactionIdInvocations.incrementAndGet();
                    return new TransactionId("grant-1");
                },
                () -> {
                    dateInvocations.incrementAndGet();
                    return GRANT_DATE;
                });
        ledger.grantWelcome(ALICE);
        List<Transaction> historyBefore = ledger.history();
        long balanceBefore = ledger.balanceOf(ALICE);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> ledger.grantWelcome(ALICE));

        assertEquals("welcome grant already exists for member", exception.getMessage());
        assertEquals(1, transactionIdInvocations.get());
        assertEquals(1, dateInvocations.get());
        assertEquals(balanceBefore, ledger.balanceOf(ALICE));
        assertEquals(historyBefore, ledger.history());
    }

    @Test
    void nullTransactionIdRejectsWithoutInvokingDateSupplierOrChangingState() {
        AtomicInteger dateInvocations = new AtomicInteger();
        LedgerManager ledger = new LedgerManager(
                () -> null,
                () -> {
                    dateInvocations.incrementAndGet();
                    return GRANT_DATE;
                });

        NullPointerException exception =
                assertThrows(NullPointerException.class, () -> ledger.grantWelcome(ALICE));

        assertEquals("generated transaction ID", exception.getMessage());
        assertEquals(0, dateInvocations.get());
        assertEquals(0L, ledger.balanceOf(ALICE));
        assertEquals(List.of(), ledger.history());
    }

    @Test
    void nullDateRejectsAfterOneIdInvocationWithoutChangingState() {
        AtomicInteger transactionIdInvocations = new AtomicInteger();
        LedgerManager ledger = new LedgerManager(
                () -> {
                    transactionIdInvocations.incrementAndGet();
                    return new TransactionId("grant-1");
                },
                () -> null);

        NullPointerException exception =
                assertThrows(NullPointerException.class, () -> ledger.grantWelcome(ALICE));

        assertEquals("generated date", exception.getMessage());
        assertEquals(1, transactionIdInvocations.get());
        assertEquals(0L, ledger.balanceOf(ALICE));
        assertEquals(List.of(), ledger.history());
    }
}
