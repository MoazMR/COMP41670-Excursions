package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.ucd.meitheal.app.InMemoryRegistrationService;
import ie.ucd.meitheal.app.RegistrationService;
import ie.ucd.meitheal.app.RequestService;
import ie.ucd.meitheal.app.SelectionService;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.RequestView;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SelectionConsoleTest {
    @Test
    void optionRequiresLoginAndOlderServiceShowsUnavailableFallback() throws IOException {
        var older = new OlderService();
        String signedOut = run(older, "6\n0\n");
        assertTrue(signedOut.contains("Login is required to select a proposal"), signedOut);
        assertFalse(signedOut.contains("This option is not available yet."), signedOut);

        String loggedIn = run(older, older.login() + "6\n0\n0\n");
        assertTrue(loggedIn.contains("This option is not available yet."), loggedIn);
        assertEquals(List.of(), older.calls);
    }

    @Test
    void successfulFlowPassesTrimmedRequestAndSelectedProposalToApi() throws IOException {
        var service = new Fixture();
        String output = run(service, service.login() + "6\n  request-7  \n1\n0\n0\n");
        assertTrue(output.contains("1 Proposal proposal-4 | Helper 100003 | 2026-12-01 | PENDING"), output);
        assertTrue(output.contains("Proposal selected. Engagement ID: engagement-2"), output);
        assertEquals(List.of(List.of("query", service.member.id(), new RequestId("request-7")),
                List.of("select", service.member.id(), new ProposalId("proposal-4"))), service.calls);
    }

    @Test
    void queryAndSelectionFailuresAndEmptyResultsPauseAndReturnToMenu() throws IOException {
        var queryFailure = new Fixture();
        queryFailure.queryFailure = true;
        String queryOutput = run(queryFailure, queryFailure.login() + "6\nrequest-7\n0\n0\n");
        assertTrue(queryOutput.contains("ERROR REQUEST_NOT_FOUND - missing request"), queryOutput);
        assertEquals(1, queryFailure.calls.size());

        var selectionFailure = new Fixture();
        selectionFailure.selectFailure = true;
        String selectionOutput = run(selectionFailure,
                selectionFailure.login() + "6\nrequest-7\n1\n0\n0\n");
        assertTrue(selectionOutput.contains("ERROR INVALID_STATE - proposal unavailable"), selectionOutput);
        assertEquals(2, selectionFailure.calls.size());

        var empty = new Fixture();
        empty.proposals = List.of();
        String emptyOutput = run(empty, empty.login() + "6\nrequest-7\n0\n0\n");
        assertTrue(emptyOutput.contains("No proposals found for this request."), emptyOutput);
        assertEquals(1, empty.calls.size());
    }

    @Test
    void blankRequestIdsRetryAndInvalidChoicesOrBackDoNotSelect() throws IOException {
        var service = new Fixture();
        String output = run(service, service.login() + "6\n   \n\u2003\nrequest-7\ninvalid\n0\n0\n0\n");
        assertTrue(output.contains("Enter a request ID, or 0 Back."), output);
        assertTrue(output.contains("Enter a whole number from 1 to 1, or 0 Back."), output);
        assertEquals(List.of(List.of("query", service.member.id(), new RequestId("request-7"))), service.calls);

        var backAtRequest = new Fixture();
        run(backAtRequest, backAtRequest.login() + "6\n0\n0\n");
        assertEquals(List.of(), backAtRequest.calls);
        var escapeAtRequest = new Fixture();
        run(escapeAtRequest, escapeAtRequest.login() + "6\n\u001b0\n");
        assertEquals(List.of(), escapeAtRequest.calls);
    }

    private static String run(RegistrationService service, String script) throws IOException {
        var output = new StringWriter();
        new RegistrationConsole(new StringReader(script), output, service).run();
        return output.toString();
    }

    private static class OlderService implements RequestService {
        final RegistrationService registration = InMemoryRegistrationService.create();
        final MemberView member = registration.register("Alex", "alex@example.org").value().orElseThrow();
        final List<List<?>> calls = new ArrayList<>();
        String login() { return "2\n2\n" + member.id().value() + "\n"; }
        @Override public List<CategoryView> categories() { return List.of(); }
        @Override public Result<MemberView> offerSkill(MemberId id, CategoryId category) { return Result.success(member); }
        @Override public Result<RequestView> createRequest(MemberId id, CategoryId category, int minutes,
                LocalDate start, LocalDate end) { return Result.failure(FailureCode.INVALID_STATE, "unused"); }
        @Override public Result<List<RequestView>> matchingRequests(MemberId id) { return Result.success(List.of()); }
        @Override public Result<ProposalView> submitProposal(MemberId id, RequestId request, LocalDate date) {
            return Result.failure(FailureCode.INVALID_STATE, "unused");
        }
        @Override public Result<MemberView> register(String name, String email) { return registration.register(name, email); }
        @Override public Result<MemberView> findMember(MemberId id) { return registration.findMember(id); }
        @Override public Result<MemberView> findMemberByEmail(String email) { return registration.findMemberByEmail(email); }
        @Override public Result<BalanceView> balance(MemberId id) { return registration.balance(id); }
    }

    private static final class Fixture extends OlderService implements SelectionService {
        final ProposalView proposal = new ProposalView(new ProposalId("proposal-4"), new RequestId("request-7"),
                new MemberId("100003"), LocalDate.parse("2026-12-01"), ProposalStatus.PENDING);
        List<ProposalView> proposals = List.of(proposal);
        boolean queryFailure;
        boolean selectFailure;
        @Override public Result<List<ProposalView>> proposalsForRequest(MemberId requesterId, RequestId requestId) {
            calls.add(List.of("query", requesterId, requestId));
            return queryFailure ? Result.failure(FailureCode.REQUEST_NOT_FOUND, "missing request")
                    : Result.success(proposals);
        }
        @Override public Result<EngagementView> selectProposal(MemberId requesterId, ProposalId proposalId) {
            calls.add(List.of("select", requesterId, proposalId));
            if (selectFailure) return Result.failure(FailureCode.INVALID_STATE, "proposal unavailable");
            return Result.success(new EngagementView(new EngagementId("engagement-2"), proposalId,
                    proposal.requestId(), requesterId, proposal.helperId(), 60, proposal.proposedOn(),
                    EngagementStatus.AGREED, Set.of(), null, null, false));
        }
    }
}
