package ie.ucd.meitheal.ledger;

import ie.ucd.meitheal.ledger.local.MemberId;
import ie.ucd.meitheal.ledger.local.TransactionId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerManagerTest {
    private static final MemberId ALICE = new MemberId("alice");
    private static final MemberId BOB = new MemberId("bob");
    private static final LocalDate GRANT_DATE = LocalDate.of(2026, 11, 8);

    @Test
    void firstWelcomeGrantCreditsExactly120() {
        LedgerManager ledger = ledger("grant-1");

        ledger.grantWelcome(ALICE);

        assertEquals(120L, ledger.balanceOf(ALICE));
    }

    @Test
    void welcomeGrantRecordsSystemCorrectMemberAndType() {
        Transaction transaction = ledger("grant-1").grantWelcome(ALICE);

        assertEquals(new TransactionId("grant-1"), transaction.transactionId());
        assertEquals(AccountReference.SYSTEM, transaction.debtor());
        assertEquals(AccountReference.member(ALICE), transaction.creditor());
        assertEquals(TransactionType.WELCOME_GRANT, transaction.type());
        assertEquals(120L, transaction.amount());
    }

    @Test
    void memberCannotReceiveSecondWelcomeGrant() {
        LedgerManager ledger = ledger("grant-1", "grant-2");
        ledger.grantWelcome(ALICE);

        assertThrows(IllegalStateException.class, () -> ledger.grantWelcome(ALICE));
    }

    @Test
    void duplicateWelcomeGrantLeavesBalanceAndHistoryUnchanged() {
        LedgerManager ledger = ledger("grant-1", "grant-2");
        ledger.grantWelcome(ALICE);
        List<Transaction> before = ledger.history();
        long balanceBefore = ledger.balanceOf(ALICE);

        assertThrows(IllegalStateException.class, () -> ledger.grantWelcome(ALICE));

        assertEquals(balanceBefore, ledger.balanceOf(ALICE));
        assertEquals(before, ledger.history());
    }

    @Test
    void injectedTransactionIdAndDateAppearExactlyInTransaction() {
        TransactionId id = new TransactionId("injected-id");
        LocalDate date = LocalDate.of(2030, 1, 2);
        LedgerManager ledger = new LedgerManager(() -> id, () -> date);

        Transaction transaction = ledger.grantWelcome(ALICE);

        assertEquals(id, transaction.transactionId());
        assertEquals(date, transaction.date());
    }

    @Test
    void noPublicZeroArgumentConstructor() {
        assertThrows(NoSuchMethodException.class, () -> LedgerManager.class.getConstructor());
    }

    @Test
    void duplicateGeneratedTransactionIdIsRejectedWithoutPartialState() {
        LedgerManager ledger = ledger("same-id", "same-id");
        ledger.grantWelcome(ALICE);
        List<Transaction> before = ledger.history();

        assertThrows(IllegalStateException.class, () -> ledger.grantWelcome(BOB));

        assertEquals(before, ledger.history());
        assertEquals(120L, ledger.balanceOf(ALICE));
        assertEquals(0L, ledger.balanceOf(BOB));
    }

    @Test
    void balanceEqualsFoldOfApplicableTransactions() {
        LedgerManager ledger = ledger("alice-grant", "bob-grant");
        ledger.grantWelcome(ALICE);
        ledger.grantWelcome(BOB);

        assertEquals(120L, ledger.balanceOf(ALICE));
        assertEquals(120L, ledger.balanceOf(BOB));
        assertEquals(-240L, ledger.balance(AccountReference.SYSTEM));
    }

    @Test
    void globalHistoryIsImmutableSnapshot() {
        LedgerManager ledger = ledger("first", "second");
        ledger.grantWelcome(ALICE);
        List<Transaction> snapshot = ledger.history();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());

        ledger.grantWelcome(BOB);
        assertEquals(1, snapshot.size());
        assertEquals(2, ledger.history().size());
    }

    @Test
    void memberSpecificHistoryContractAndImmutableSnapshot() throws ReflectiveOperationException {
        Method historyMethod = memberHistoryMethod();
        LedgerManager ledger = ledger("alice", "bob");
        ledger.grantWelcome(ALICE);

        @SuppressWarnings("unchecked")
        List<Transaction> history = (List<Transaction>) historyMethod.invoke(ledger, ALICE);

        assertThrows(UnsupportedOperationException.class, () -> history.clear());
        assertEquals(1, history.size());
    }

    @Test
    void memberHistoryDoesNotExposeAnotherMembersTransactions() throws ReflectiveOperationException {
        Method historyMethod = memberHistoryMethod();
        LedgerManager ledger = ledger("alice", "bob");
        ledger.grantWelcome(ALICE);
        ledger.grantWelcome(BOB);

        @SuppressWarnings("unchecked")
        List<Transaction> alicesHistory =
                (List<Transaction>) historyMethod.invoke(ledger, ALICE);

        assertEquals(Set.of(ALICE), alicesHistory.stream()
                .map(transaction -> transaction.creditor().memberId())
                .collect(Collectors.toSet()));
    }

    @Test
    void nullAndBlankTypedIdsRejectedWithoutLedgerMutation() {
        LedgerManager ledger = ledger("unused");

        assertThrows(NullPointerException.class, () -> ledger.grantWelcome(null));
        assertThrows(NullPointerException.class, () -> new MemberId(null));
        assertThrows(IllegalArgumentException.class, () -> new MemberId(" \t"));
        assertThrows(NullPointerException.class, () -> new TransactionId(null));
        assertThrows(IllegalArgumentException.class, () -> new TransactionId(" \n"));

        assertTrue(ledger.history().isEmpty());
    }

    @Test
    void noPublicMutationPathCanBypassWelcomeGrantRule() {
        boolean unrestrictedAppend = java.util.Arrays.stream(LedgerManager.class.getMethods())
                .anyMatch(method -> method.getName().equals("append")
                        && Modifier.isPublic(method.getModifiers())
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == Transaction.class);

        assertFalse(unrestrictedAppend,
                "LedgerManager must not expose public unrestricted transaction append");
    }

    private static LedgerManager ledger(String... ids) {
        int[] index = {0};
        return new LedgerManager(
                () -> new TransactionId(ids[index[0]++]),
                () -> GRANT_DATE);
    }

    private static Method memberHistoryMethod() {
        Method method = null;
        try {
            method = LedgerManager.class.getMethod("history", MemberId.class);
        } catch (NoSuchMethodException ignored) {
            // The assertion below reports the missing public contract clearly.
        }
        assertNotNull(method, "LedgerManager must expose public member-specific history");
        assertEquals(List.class, method.getReturnType());
        return method;
    }
}
