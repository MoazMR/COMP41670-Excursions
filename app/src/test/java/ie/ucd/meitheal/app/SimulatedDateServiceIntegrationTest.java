package ie.ucd.meitheal.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.view.BalanceView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulatedDateServiceIntegrationTest {
    @Test
    void forwardSameAndBackwardDatesPreserveExistingBusinessState() {
        var service = InMemoryRegistrationService.create();
        var requester = service.register("Pat", "pat@example.org").value().orElseThrow().id();
        var helper = service.register("Jo", "jo@example.org").value().orElseThrow().id();
        var gardening = new CategoryId("gardening");
        LocalDate scheduled = LocalDate.of(2026, 9, 2);
        service.offerSkill(helper, gardening).value().orElseThrow();
        var request = service.createRequest(requester, gardening, 60, scheduled, scheduled).value().orElseThrow();
        var proposal = service.submitProposal(helper, request.id(), scheduled).value().orElseThrow();
        var agreement = service.selectProposal(requester, proposal.id()).value().orElseThrow();
        assertEquals(EngagementStatus.AGREED, agreement.status());

        var requesterBalance = new BalanceView(requester, 120, 63, 57);
        var helperBalance = new BalanceView(helper, 120, 0, 120);
        var requesterLedger = service.memberLedgerHistory(requester).value().orElseThrow();
        var helperLedger = service.memberLedgerHistory(helper).value().orElseThrow();
        var report = service.report();
        LocalDate start = scheduled.minusDays(1);
        LocalDate later = scheduled.plusDays(1);
        assertEquals(start, service.currentDate());

        for (LocalDate requested : List.of(later, later, start)) {
            var result = service.advanceDate(requested);
            if (requested.equals(start)) {
                assertEquals(FailureCode.BUSINESS_REJECTION, result.failureCode().orElseThrow());
            } else {
                assertEquals(later, result.value().orElseThrow());
            }
            assertEquals(later, service.currentDate());
            assertEquals(requesterBalance, service.balance(requester).value().orElseThrow());
            assertEquals(helperBalance, service.balance(helper).value().orElseThrow());
            assertEquals(requesterLedger, service.memberLedgerHistory(requester).value().orElseThrow());
            assertEquals(helperLedger, service.memberLedgerHistory(helper).value().orElseThrow());
            assertEquals(List.of(agreement), service.ownEngagementHistory(requester).value().orElseThrow());
            assertEquals(List.of(agreement), service.ownEngagementHistory(helper).value().orElseThrow());
            assertEquals(report, service.report());
        }

        var newMember = service.register("Sam", "sam@example.org").value().orElseThrow().id();
        assertEquals(later, service.memberLedgerHistory(newMember).value().orElseThrow().getFirst().date());
    }
}
