package ie.ucd.meitheal.app;

import ie.ucd.meitheal.ledger.LedgerManager;
import ie.ucd.meitheal.membermanager.MemberManager;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.port.InMemorySimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.model.TransactionType;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.ReportView;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementValuationTest {
    @Test
    void oneHourOfGardeningAndOneHourOfCookingPayTheSameSixtyCredits() {
        verifySettlement("gardening", 60, 3, 57, 180);
        verifySettlement("cooking", 60, 3, 57, 180);
    }

    @Test
    void settlementMayFinishExactlyAtTheOverdraftFloorIncludingTheLevy() {
        verifySettlement("carpentry", 286, 14, -180, 406);
    }

    @Test
    void failedSettlementPreservesFirstConfirmationAndBothAccountsUntilRetry() {
        var lock = new Object();
        var catalogue = StartupCatalogue.load(getClass().getResourceAsStream("/categories.csv"));
        var members = new MemberManager(lock, catalogue.list().stream()
                .map(c -> new ie.ucd.meitheal.membermanager.CategoryId(c.id().value())).toList());
        var today = LocalDate.of(2026, 9, 1);
        var clock = new InMemorySimulatedClock(today);
        var sequence = new AtomicLong();
        Supplier<TransactionId> ids = () -> new TransactionId("t" + sequence.incrementAndGet());
        var ledger = new LedgerManager(lock, ids, clock::currentDate);
        var failOnce = new AtomicBoolean(true);
        var service = new InMemoryRegistrationService(lock, members, ledger, clock, ids, catalogue,
                () -> { }, ignored -> { }, candidate -> {
                    if (failOnce.getAndSet(false)) throw new IllegalStateException("settlement interrupted");
                });
        var requester = service.register("Pat", "pat@example.org").value().orElseThrow().id();
        var helper = service.register("Sam", "sam@example.org").value().orElseThrow().id();
        var skill = new CategoryId("gardening");
        service.offerSkill(helper, skill).value().orElseThrow();
        var request = service.createRequest(requester, skill, 20, today, today).value().orElseThrow();
        var proposal = service.submitProposal(helper, request.id(), today).value().orElseThrow();
        var agreement = service.selectProposal(requester, proposal.id()).value().orElseThrow();
        var first = service.confirmEngagement(requester, agreement.id()).value().orElseThrow();
        var requesterHistory = service.memberLedgerHistory(requester).value().orElseThrow();
        var helperHistory = service.memberLedgerHistory(helper).value().orElseThrow();

        assertEquals(FailureCode.ATOMIC_OPERATION_FAILED,
                service.confirmEngagement(helper, agreement.id()).failureCode().orElseThrow());
        assertEquals(List.of(first), service.ownEngagementHistory(requester).value().orElseThrow());
        assertEquals(requesterHistory, service.memberLedgerHistory(requester).value().orElseThrow());
        assertEquals(helperHistory, service.memberLedgerHistory(helper).value().orElseThrow());
        assertEquals(new BalanceView(requester, 120, 21, 99), service.balance(requester).value().orElseThrow());
        assertEquals(new BalanceView(helper, 120, 0, 120), service.balance(helper).value().orElseThrow());
        assertEquals(new ReportView(2, 240, 0), service.report());

        service.confirmEngagement(helper, agreement.id()).value().orElseThrow();
        assertEquals(new BalanceView(requester, 99, 0, 99), service.balance(requester).value().orElseThrow());
        assertEquals(new BalanceView(helper, 140, 0, 140), service.balance(helper).value().orElseThrow());
        assertEquals(new ReportView(2, 239, 1), service.report());
        assertEquals(List.of(TransactionType.WELCOME_GRANT, TransactionType.SERVICE_PAYMENT, TransactionType.LEVY),
                service.memberLedgerHistory(requester).value().orElseThrow().stream().map(t -> t.type()).toList());
    }

    private void verifySettlement(String skill, int minutes, int levy, long requesterAfter, long helperAfter) {
        var service = InMemoryRegistrationService.create();
        var requester = service.register("Pat", "pat@example.org").value().orElseThrow().id();
        var helper = service.register("Sam", "sam@example.org").value().orElseThrow().id();
        var category = new CategoryId(skill);
        var today = service.currentDate();
        service.offerSkill(helper, category).value().orElseThrow();
        var request = service.createRequest(requester, category, minutes, today, today).value().orElseThrow();
        var proposal = service.submitProposal(helper, request.id(), today).value().orElseThrow();
        var agreement = service.selectProposal(requester, proposal.id()).value().orElseThrow();
        var reserved = new BalanceView(requester, 120, minutes + levy, requesterAfter);
        assertEquals(reserved, service.balance(requester).value().orElseThrow());
        service.confirmEngagement(requester, agreement.id()).value().orElseThrow();
        assertEquals(reserved, service.balance(requester).value().orElseThrow());
        service.confirmEngagement(helper, agreement.id()).value().orElseThrow();
        assertEquals(new BalanceView(requester, requesterAfter, 0, requesterAfter),
                service.balance(requester).value().orElseThrow());
        assertEquals(new BalanceView(helper, helperAfter, 0, helperAfter),
                service.balance(helper).value().orElseThrow());
        var entries = service.memberLedgerHistory(requester).value().orElseThrow();
        assertEquals(3, entries.size());
        assertEquals(TransactionType.SERVICE_PAYMENT, entries.get(1).type());
        assertEquals(minutes, entries.get(1).amount());
        assertEquals(TransactionType.LEVY, entries.get(2).type());
        assertEquals(levy, entries.get(2).amount());
        assertEquals(new ReportView(2, 240 - levy, levy), service.report());
    }
}
