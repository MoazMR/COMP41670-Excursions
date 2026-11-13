package ie.ucd.meitheal.app;

import ie.ucd.meitheal.cli.RegistrationConsole;
import ie.ucd.meitheal.ledger.LedgerManager;
import ie.ucd.meitheal.ledger.TransactionType;
import ie.ucd.meitheal.membermanager.MemberManager;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.port.InMemorySimulatedClock;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationConsoleIntegrationTest {
    private static final MemberId FIRST = new MemberId("100001");
    private static final String REGISTER = "1\nAlex Byrne\nalex@example.org\n0\n";

    @Test
    void failedWelcomeGrantLeavesNothingVisibleAndConsoleRetryPublishesOnce() throws IOException {
        Object monitor = new Object();
        var clock = new InMemorySimulatedClock(LocalDate.of(2026, 9, 9));
        var members = new MemberManager(monitor, Set.of());
        var sequence = new AtomicInteger();
        Supplier<TransactionId> transactionIds = () -> new TransactionId("registration-" + sequence.incrementAndGet());
        var ledger = new LedgerManager(monitor, transactionIds, clock::currentDate);
        var failOnce = new AtomicBoolean(true);
        var service = new InMemoryRegistrationService(monitor, members, ledger, clock, transactionIds,
                new ie.ucd.meitheal.membermanager.FixedCategoryCatalog(java.util.List.of()), () -> {
            assertTrue(Thread.holdsLock(monitor));
            assertTrue(members.findById(FIRST).isEmpty());
            assertTrue(ledger.history().isEmpty());
            if (failOnce.getAndSet(false)) {
                throw new IllegalStateException("injected before publication");
            }
        });

        String failure = run(service, REGISTER + "0\n");
        assertTrue(failure.contains("ATOMIC_OPERATION_FAILED"), failure);
        assertTrue(members.findById(FIRST).isEmpty());
        assertTrue(members.findByEmail("alex@example.org").isEmpty());
        assertTrue(ledger.history().isEmpty());
        assertEquals(0, ledger.balanceOf(FIRST));

        String success = run(service, REGISTER + "11\n2\n1\nALEX@example.org\n11\n1\n0\n0\n0\n");
        int registration = success.indexOf("Registration successful");
        int gate = success.indexOf("Login is required", registration);
        int header = success.indexOf("Alex Byrne | alex@example.org | 100001");
        assertTrue(registration >= 0 && registration < gate && gate < header, success);
        String receipt = success.substring(registration, gate);
        assertTrue(receipt.contains("Member ID: 100001"), receipt);
        assertTrue(receipt.contains("Current balance: 120"), receipt);
        String account = success.substring(header);
        assertTrue(account.contains("Ledger balance: 120"), account);
        assertTrue(account.contains("Held credits: 0"), account);
        assertTrue(account.contains("Available credits: 120"), account);
        assertEquals(1, members.getMembers().size());
        assertEquals(FIRST, members.findByEmail("alex@example.org").orElseThrow().id());
        assertEquals(1, ledger.history().size());
        var grant = ledger.history().getFirst();
        assertEquals(TransactionType.WELCOME_GRANT, grant.type());
        assertTrue(grant.debtor().isSystem());
        assertEquals(FIRST, grant.creditor().memberId());
        assertEquals(clock.currentDate(), grant.date());
        assertEquals(120, grant.amount());
        assertEquals(120, ledger.balanceOf(FIRST));
        assertEquals(0, service.balance(FIRST).value().orElseThrow().held());
        assertEquals(120, service.balance(FIRST).value().orElseThrow().available());
    }

    private String run(RegistrationService service, String input) throws IOException {
        var output = new StringWriter();
        new RegistrationConsole(new StringReader(input), output, service).run();
        return output.toString();
    }
}
