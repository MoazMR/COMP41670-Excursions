package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.result.FailureCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProposalManagementIntegrationTest {
    @Test
    void ownerActionsPreserveOtherProposalsAndSelectionHolds() {
        var application = InMemoryRegistrationService.create();
        ProposalManagementService proposals = application;
        var owner = proposals.register("Pat", "pat@example.org").value().orElseThrow().id();
        var alex = proposals.register("Alex", "alex@example.org").value().orElseThrow().id();
        var bea = proposals.register("Bea", "bea@example.org").value().orElseThrow().id();
        var category = new CategoryId("gardening");
        var date = LocalDate.of(2026, 9, 2);
        for (var helper : List.of(alex, bea)) {
            proposals.offerSkill(helper, category).value().orElseThrow();
        }
        var first = proposals.createRequest(owner, category, 20, date, date).value().orElseThrow().id();
        var second = proposals.createRequest(owner, category, 20, date, date).value().orElseThrow().id();
        var a = proposals.submitProposal(alex, first, date).value().orElseThrow();
        var b = proposals.submitProposal(bea, first, date).value().orElseThrow();
        var c = proposals.submitProposal(alex, second, date).value().orElseThrow();

        assertEquals(FailureCode.NOT_PARTICIPANT, proposals.withdrawProposal(bea, a.id()).failureCode().orElseThrow());
        assertEquals(FailureCode.NOT_PARTICIPANT, proposals.rejectProposal(bea, a.id()).failureCode().orElseThrow());
        assertEquals(List.of(a, b), proposals.proposalsForRequest(owner, first).value().orElseThrow());
        assertEquals(List.of(a, c), proposals.proposalsForHelper(alex).value().orElseThrow());

        var withdrawn = proposals.withdrawProposal(alex, a.id()).value().orElseThrow();
        var rejected = proposals.rejectProposal(owner, b.id()).value().orElseThrow();
        assertEquals(ProposalStatus.WITHDRAWN, withdrawn.status());
        assertEquals(ProposalStatus.REJECTED, rejected.status());
        application.selectProposal(owner, c.id()).value().orElseThrow();
        var selected = proposals.proposalsForRequest(owner, second).value().orElseThrow().getFirst();
        assertEquals(ProposalStatus.SELECTED, selected.status());
        var heldBalance = application.balance(owner).value().orElseThrow();
        assertEquals(21, heldBalance.held());
        assertEquals(FailureCode.INVALID_STATE, proposals.rejectProposal(owner, c.id()).failureCode().orElseThrow());
        assertEquals(FailureCode.INVALID_STATE, proposals.withdrawProposal(alex, c.id()).failureCode().orElseThrow());
        assertEquals(FailureCode.INVALID_STATE, proposals.withdrawProposal(alex, a.id()).failureCode().orElseThrow());
        assertEquals(FailureCode.INVALID_STATE, proposals.rejectProposal(owner, b.id()).failureCode().orElseThrow());
        assertEquals(List.of(withdrawn, rejected), proposals.proposalsForRequest(owner, first).value().orElseThrow());
        assertEquals(List.of(withdrawn, selected), proposals.proposalsForHelper(alex).value().orElseThrow());
        assertEquals(heldBalance, application.balance(owner).value().orElseThrow());
    }
}
