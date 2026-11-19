package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.ucd.meitheal.app.InMemoryRegistrationService;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.view.ProposalView;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProposalManagementConsoleTest {
    private final InMemoryRegistrationService service = InMemoryRegistrationService.create();
    private final MemberId requester = member("Pat");
    private final MemberId helper = member("Alex");
    private final MemberId stranger = member("Bea");
    private final LocalDate date = LocalDate.parse("2026-09-01");
    private final RequestId request = service.createRequest(requester, service.categories().getFirst().id(),
            30, date, date).value().orElseThrow().id();

    @Test
    void helperSubmitsUsingTheOriginalNumberedMatchThenWithdrawsAndSeesTheFinalStatus() throws IOException {
        offerSkill(helper);
        String submitted = menu(helper, "5\n1\n2026-09-01\n0\n0\n");
        assertTrue(submitted.contains("Proposal submitted. Proposal ID: prop-1"), submitted);

        String withdrawn = menu(helper, "5\nh\n1\n0\n");
        assertTrue(withdrawn.contains("My proposals"), withdrawn);
        assertTrue(withdrawn.contains("Proposal prop-1 | Request req-1 | Helper 100002"), withdrawn);
        assertTrue(withdrawn.contains("Proposal prop-1 | WITHDRAWN"), withdrawn);
        var after = proposals();
        assertEquals(ProposalStatus.WITHDRAWN, after.getFirst().status());

        String repeat = menu(helper, "5\nH\n1\n0\n");
        assertTrue(repeat.contains("ERROR INVALID_STATE"), repeat);
        assertEquals(after, proposals());
    }

    @Test
    void requesterInspectsAndRejectsOnlyTheirProposalsAndRefusedActionsPreserveThem() throws IOException {
        propose(helper);
        var before = proposals();
        String foreign = menu(stranger, "5\nR\nreq-1\n0\n5\nH\n0\n");
        assertTrue(foreign.contains("ERROR NOT_PARTICIPANT"), foreign);
        assertTrue(foreign.contains("You have no proposals."), foreign);
        assertFalse(foreign.contains("Proposal prop-1"), foreign);
        assertEquals(before, proposals());

        String view = menu(requester, "5\nR\nreq-1\n0\n");
        assertTrue(view.contains("Proposals for request req-1"), view);
        assertEquals(before, proposals());
        String rejected = menu(requester, "5\nr\nreq-1\n1\n0\n");
        assertTrue(rejected.contains("Proposal prop-1 | REJECTED"), rejected);
        var after = proposals();
        assertEquals(ProposalStatus.REJECTED, after.getFirst().status());
        assertTrue(menu(requester, "5\nR\nreq-1\n1\n0\n").contains("ERROR INVALID_STATE"));
        assertEquals(after, proposals());
    }

    @Test
    void optionSixStillSelectsAndManagementBackAndEscapeLeaveSelectedProposalAlone() throws IOException {
        propose(helper);
        assertTrue(menu(requester, "6\nreq-1\n1\n0\n").contains("Proposal selected. Engagement ID: eng-1"));
        var selected = proposals();
        assertEquals(ProposalStatus.SELECTED, selected.getFirst().status());
        assertTrue(menu(requester, "5\nR\nreq-1\n1\n0\n").contains("ERROR INVALID_STATE"));
        assertTrue(menu(helper, "5\nH\n1\n0\n").contains("ERROR INVALID_STATE"));
        menu(requester, "5\nR\npartial\u001b5\nR\n0\n5\nH\n0\n");
        assertEquals(selected, proposals());
    }

    @Test
    void suspendedMembersCanStillInspectTheirProposalHistoryWhenMatchingIsRefused() throws IOException {
        propose(helper);
        service.suspendMember(helper).value().orElseThrow();
        var afterSuspension = proposals();
        assertEquals(ProposalStatus.INVALIDATED, afterSuspension.getFirst().status());
        String own = menu(helper, "5\nH\n0\n");
        assertTrue(own.contains("ERROR SUSPENDED_MEMBER"), own);
        assertTrue(own.contains("My proposals"), own);
        assertTrue(own.contains("| INVALIDATED"), own);

        service.suspendMember(requester).value().orElseThrow();
        String incoming = menu(requester, "5\nR\nreq-1\n0\n");
        assertTrue(incoming.contains("ERROR SUSPENDED_MEMBER"), incoming);
        assertTrue(incoming.contains("Proposals for request req-1"), incoming);
        assertTrue(incoming.contains("| INVALIDATED"), incoming);
        assertEquals(afterSuspension, proposals());
    }

    private String menu(MemberId actor, String actions) throws IOException {
        var output = new StringWriter();
        new RegistrationConsole(new StringReader("2\n2\n" + actor.value() + "\n" + actions + "0\n"),
                output, service).run();
        return output.toString();
    }

    private MemberId member(String name) {
        return service.register(name, name.toLowerCase(java.util.Locale.ROOT) + "@example.org")
                .value().orElseThrow().id();
    }

    private void offerSkill(MemberId actor) {
        service.offerSkill(actor, service.categories().getFirst().id()).value().orElseThrow();
    }

    private void propose(MemberId actor) {
        offerSkill(actor);
        service.submitProposal(actor, request, date).value().orElseThrow();
    }

    private List<ProposalView> proposals() {
        return service.proposalsForRequest(requester, request).value().orElseThrow();
    }
}
