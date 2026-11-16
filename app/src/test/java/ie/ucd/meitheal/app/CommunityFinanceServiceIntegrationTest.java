package ie.ucd.meitheal.app;

import ie.ucd.meitheal.ledger.LedgerManager;
import ie.ucd.meitheal.membermanager.MemberManager;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.model.TransactionType;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.port.InMemorySimulatedClock;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.CommunityGrantView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.ReportView;
import ie.ucd.meitheal.shared.view.RequestView;
import ie.ucd.meitheal.shared.view.TransactionView;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityFinanceServiceIntegrationTest {
    private static final LocalDate MAY_3 = LocalDate.of(2027, 5, 3);
    private static final LocalDate JUNE_3 = LocalDate.of(2027, 6, 3);
    private static final LocalDate JUNE_4 = LocalDate.of(2027, 6, 4);
    private static final CategoryId GARDENING = new CategoryId("gardening");

    @Test
    void approvesOnMayThirdAndJuneFourthWithoutChangingStateOnJuneThird() {
        Fixture fixture = new Fixture();
        CommunityFinanceService service = fixture.service;
        assertReport(service, 0, 0, 0);

        MemberId requester = fixture.register("requester");
        MemberId helper = fixture.register("helper");
        assertReport(service, 2, 240, 0);
        EngagementView agreement = fixture.agree(requester, helper, 180);
        fixture.complete(agreement);
        assertEquals(-69, service.balance(requester).value().orElseThrow().ledgerBalance());
        assertReport(service, 2, 231, 9);

        List<TransactionView> historyBeforeGrant = service.memberLedgerHistory(requester).value().orElseThrow();
        assertEquals(new CommunityGrantView(requester, 2, MAY_3),
                service.applyForCommunityGrant(requester, 2).value().orElseThrow());
        List<TransactionView> mayHistory = service.memberLedgerHistory(requester).value().orElseThrow();
        assertEquals(historyBeforeGrant.size() + 1, mayHistory.size());
        assertEquals(historyBeforeGrant, mayHistory.subList(0, historyBeforeGrant.size()));
        assertGrantTransaction(mayHistory.getLast(), 2, MAY_3);
        assertThrows(UnsupportedOperationException.class, mayHistory::clear);
        assertReport(service, 2, 233, 7);

        ReportView beforeRefusal = service.report();
        BalanceView balanceBeforeRefusal = service.balance(requester).value().orElseThrow();
        List<TransactionView> helperHistory = service.memberLedgerHistory(helper).value().orElseThrow();
        assertEquals(-67, balanceBeforeRefusal.ledgerBalance());
        assertEquals(JUNE_3, fixture.clock.advanceTo(JUNE_3).value().orElseThrow());
        assertEquals(FailureCode.BUSINESS_REJECTION,
                service.applyForCommunityGrant(requester, 3).failureCode().orElseThrow());
        assertEquals(beforeRefusal, service.report());
        assertEquals(balanceBeforeRefusal, service.balance(requester).value().orElseThrow());
        assertEquals(mayHistory, service.memberLedgerHistory(requester).value().orElseThrow());
        assertEquals(helperHistory, service.memberLedgerHistory(helper).value().orElseThrow());

        assertEquals(JUNE_4, fixture.clock.advanceTo(JUNE_4).value().orElseThrow());
        assertEquals(new CommunityGrantView(requester, 3, JUNE_4),
                service.applyForCommunityGrant(requester, 3).value().orElseThrow());
        List<TransactionView> juneHistory = service.memberLedgerHistory(requester).value().orElseThrow();
        assertEquals(historyBeforeGrant.size() + 1, mayHistory.size());
        assertEquals(mayHistory.size() + 1, juneHistory.size());
        assertEquals(mayHistory, juneHistory.subList(0, mayHistory.size()));
        assertGrantTransaction(juneHistory.getLast(), 3, JUNE_4);
        assertEquals(-64, service.balance(requester).value().orElseThrow().ledgerBalance());
        assertReport(service, 2, 236, 4);
    }

    @Test
    void memberQueriesRejectMissingMembersAndReturnParticipantSnapshots() {
        Fixture fixture = new Fixture();
        CommunityFinanceService service = fixture.service;
        MemberId requester = fixture.register("requester");
        MemberId helper = fixture.register("helper");
        MemberId otherRequester = fixture.register("other-requester");
        MemberId otherHelper = fixture.register("other-helper");
        MemberId unknown = new MemberId("unknown");
        for (MemberId missing : new MemberId[] { unknown, null }) {
            assertEquals(FailureCode.MEMBER_NOT_FOUND,
                    service.applyForCommunityGrant(missing, 1).failureCode().orElseThrow());
            assertEquals(FailureCode.MEMBER_NOT_FOUND,
                    service.memberLedgerHistory(missing).failureCode().orElseThrow());
            assertEquals(FailureCode.MEMBER_NOT_FOUND,
                    service.ownEngagementHistory(missing).failureCode().orElseThrow());
        }
        assertEquals(List.of(), service.ownEngagementHistory(requester).value().orElseThrow());

        EngagementView agreement = fixture.agree(requester, helper, 180);
        EngagementView unrelated = fixture.agree(otherRequester, otherHelper, 60);
        fixture.complete(unrelated);
        List<EngagementView> history = service.ownEngagementHistory(requester).value().orElseThrow();
        assertEquals(List.of(agreement), history);
        assertEquals(history, service.ownEngagementHistory(helper).value().orElseThrow());
        assertThrows(UnsupportedOperationException.class, history::clear);

        BalanceView held = service.balance(requester).value().orElseThrow();
        assertEquals(120, held.ledgerBalance());
        assertEquals(-69, held.available());
        ReportView funded = service.report();
        assertEquals(3, funded.fundBalance());
        List<TransactionView> ledgerHistory = service.memberLedgerHistory(requester).value().orElseThrow();
        assertEquals(FailureCode.BUSINESS_REJECTION,
                service.applyForCommunityGrant(requester, 1).failureCode().orElseThrow());
        assertEquals(held, service.balance(requester).value().orElseThrow());
        assertEquals(funded, service.report());
        assertEquals(ledgerHistory, service.memberLedgerHistory(requester).value().orElseThrow());

        EngagementView completed = fixture.complete(agreement);
        assertEquals(EngagementStatus.COMPLETED, completed.status());
        assertEquals(List.of(agreement), history);
        assertEquals(List.of(completed), service.ownEngagementHistory(requester).value().orElseThrow());
        assertEquals(1, service.ownEngagementHistory(otherHelper).value().orElseThrow().size());
        assertEquals(unrelated.id(),
                service.ownEngagementHistory(otherHelper).value().orElseThrow().getFirst().id());
    }

    private static void assertReport(CommunityFinanceService service, long members, long memberTotal, long fund) {
        ReportView report = service.report();
        assertEquals(new ReportView(members, memberTotal, fund), report);
        assertEquals(members * 120, report.actualCirculation());
        assertTrue(report.conserved());
    }

    private static void assertGrantTransaction(TransactionView transaction, long amount, LocalDate date) {
        assertEquals(TransactionType.COMMUNITY_GRANT, transaction.type());
        assertEquals(amount, transaction.amount());
        assertEquals(date, transaction.date());
    }

    private static final class Fixture {
        final InMemorySimulatedClock clock = new InMemorySimulatedClock(MAY_3);
        final CommunityFinanceService service;

        Fixture() {
            Object lock = new Object();
            AtomicLong sequence = new AtomicLong();
            Supplier<TransactionId> transactionIds = () -> new TransactionId("finance-" + sequence.incrementAndGet());
            CategoryCatalog catalogue = StartupCatalogue.load(
                    InMemoryRegistrationService.class.getResourceAsStream("/categories.csv"));
            MemberManager members = new MemberManager(lock, catalogue.list().stream()
                    .map(category -> new ie.ucd.meitheal.membermanager.CategoryId(category.id().value())).toList());
            LedgerManager ledger = new LedgerManager(lock, transactionIds, clock::currentDate);
            service = new InMemoryRegistrationService(lock, members, ledger, clock, transactionIds, catalogue,
                    () -> { });
        }

        MemberId register(String name) {
            return service.register(name, name + "@example.org").value().orElseThrow().id();
        }

        EngagementView agree(MemberId requester, MemberId helper, int minutes) {
            LocalDate date = clock.currentDate();
            service.offerSkill(helper, GARDENING).value().orElseThrow();
            RequestView request = service.createRequest(requester, GARDENING, minutes, date, date).value().orElseThrow();
            var proposal = service.submitProposal(helper, request.id(), date).value().orElseThrow();
            return service.selectProposal(requester, proposal.id()).value().orElseThrow();
        }

        EngagementView complete(EngagementView agreement) {
            service.confirmEngagement(agreement.requesterId(), agreement.id()).value().orElseThrow();
            return service.confirmEngagement(agreement.helperId(), agreement.id()).value().orElseThrow();
        }
    }
}
