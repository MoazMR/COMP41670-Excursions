package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.result.FailureCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestServiceIntegrationTest {
    @Test
    void onlyEligibleHelpersCanProposeAndRejectedAttemptsLeaveIdsAndBalancesIntact() {
        RequestService service = InMemoryRegistrationService.create();
        var gardening = new CategoryId("gardening");
        var date = LocalDate.of(2026, 9, 2);
        var requester = service.register("Pat", "pat@example.org").value().orElseThrow().id();
        var helper = service.register("Mae", "mae@example.org").value().orElseThrow().id();
        assertEquals(List.of("Gardening", "Cooking", "Carpentry"),
                service.categories().stream().map(row -> row.name()).toList());
        assertTrue(service.createRequest(requester, gardening, 0, date, date).isFailure());
        var request = service.createRequest(requester, gardening, 45, date, date).value().orElseThrow();
        assertEquals("req-1", request.id().value());
        assertEquals(List.of(), service.matchingRequests(helper).value().orElseThrow());
        assertTrue(service.submitProposal(helper, request.id(), date).isFailure());
        service.offerSkill(requester, gardening).value().orElseThrow();
        assertTrue(service.submitProposal(requester, request.id(), date).isFailure());
        assertEquals(List.of(), service.matchingRequests(requester).value().orElseThrow());
        service.offerSkill(helper, gardening).value().orElseThrow();
        assertEquals(List.of(request), service.matchingRequests(helper).value().orElseThrow());
        assertTrue(service.submitProposal(helper, request.id(), date.plusDays(1)).isFailure());
        var proposal = service.submitProposal(helper, request.id(), date).value().orElseThrow();
        assertEquals("prop-1", proposal.id().value());
        assertEquals(request.id(), proposal.requestId());
        assertEquals(helper, proposal.helperId());
        assertEquals(ProposalStatus.PENDING, proposal.status());
        assertEquals(FailureCode.DUPLICATE_PROPOSAL,
                service.submitProposal(helper, request.id(), date).failureCode().orElseThrow());
        assertEquals(120, service.balance(requester).value().orElseThrow().available());
        assertEquals(120, service.balance(helper).value().orElseThrow().available());
    }

    @Test
    void rejectsMissingOrInvalidStartupCatalogue() {
        assertThrows(IllegalStateException.class, () -> StartupCatalogue.load(null));
        for (String csv : List.of("name,id\n", "id,name\nc,\n", "id,name\nc,Cooking\nc,Coffee\n")) {
            var input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
            assertThrows(IllegalStateException.class, () -> StartupCatalogue.load(input));
        }
    }
}
