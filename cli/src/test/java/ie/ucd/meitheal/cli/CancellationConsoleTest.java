package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.ucd.meitheal.app.CancellationService;
import ie.ucd.meitheal.app.ConfirmationService;
import ie.ucd.meitheal.app.InMemoryRegistrationService;
import ie.ucd.meitheal.app.RegistrationService;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.EngagementStatus;
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

class CancellationConsoleTest {
    private static final String EARLY = "Engagement cancelled early. Hold released.";
    private static final String LATE = "Engagement cancelled late. Hold released.";

    @Test
    void requiresLoginAndCancellationCapability() throws IOException {
        var service = new Fixture();
        String signedOut = run(service, "8\n0\n");
        assertTrue(signedOut.contains("Login is required to cancel an engagement"), signedOut);
        assertFalse(signedOut.contains("Engagement ID (or 0 Back): "), signedOut);
        assertEquals(List.of(), service.calls);

        var confirmationOnly = new ConfirmationOnlyService();
        String loggedIn = run(confirmationOnly, confirmationOnly.login(confirmationOnly.first) + "8\n0\n0\n");
        assertOrdered(loggedIn, "This option is not available yet.", "0 Back", confirmationOnly.first.name() + " |",
                " 8  Cancel an engagement");
        assertFalse(loggedIn.contains("Engagement ID (or 0 Back): "), loggedIn);
        assertEquals(List.of(), confirmationOnly.calls);
    }

    @Test
    void trimsIdsRetriesBlanksAndBackOrEscapeDoNotCallService() throws IOException {
        var service = new Fixture();
        service.results.add(Result.success(service.view(EngagementStatus.CANCELLED, false, service.first.id())));
        String output = run(service, service.login(service.first) + "8\n\n   \n  cancel-9  \n0\n0\n");
        assertOrdered(output, "Engagement ID (or 0 Back): ", "Enter an engagement ID, or 0 Back.",
                "Engagement ID (or 0 Back): ", EARLY, "0 Back", service.first.name() + " |",
                " 8  Cancel an engagement");
        assertEquals(List.of(new Call(service.first.id(), new EngagementId("cancel-9"))), service.calls);

        var zero = new Fixture();
        String zeroOutput = run(zero, zero.login(zero.first) + "8\n0\n0\n");
        assertOrdered(zeroOutput, "Engagement ID (or 0 Back): ", "0 Back", zero.first.name() + " |",
                " 8  Cancel an engagement");
        assertEquals(List.of(), zero.calls);

        var escape = new Fixture();
        String escapeOutput = run(escape, escape.login(escape.first) + "8\npartial\u001b0\n");
        assertOrdered(escapeOutput, "Engagement ID (or 0 Back): ", "0 Back", escape.first.name() + " |",
                " 8  Cancel an engagement");
        assertEquals(List.of(), escape.calls);
    }

    @Test
    void switchesMembersAndReportsEarlyAndLateCancellation() throws IOException {
        var service = new Fixture();
        service.results.add(Result.success(service.view(EngagementStatus.CANCELLED, false, service.first.id())));
        service.results.add(Result.success(service.view(EngagementStatus.CANCELLED, true, service.second.id())));
        String output = run(service, service.login(service.first) + "8\nearly-id\n0\n"
                + service.login(service.second) + "8\nlate-id\n\u001b0\n");
        assertOrdered(output, EARLY, "0 Back", service.first.name() + " |", " 8  Cancel an engagement",
                LATE, "0 Back", service.second.name() + " |", " 8  Cancel an engagement");
        assertEquals(List.of(new Call(service.first.id(), new EngagementId("early-id")),
                new Call(service.second.id(), new EngagementId("late-id"))), service.calls);
    }

    @Test
    void failuresAndOtherSuccessStatusMakeNoCancellationClaims() throws IOException {
        List<FailureCode> codes = List.of(FailureCode.ENGAGEMENT_NOT_FOUND, FailureCode.NOT_PARTICIPANT,
                FailureCode.INVALID_STATE, FailureCode.CANCELLATION_CLOSED, FailureCode.ATOMIC_OPERATION_FAILED);
        for (FailureCode code : codes) {
            var service = new Fixture();
            String detail = "detail for " + code;
            service.results.add(Result.failure(code, detail));
            String output = run(service, service.login(service.first) + "8\nengagement-x\n0\n0\n");
            assertOrdered(output, "Engagement ID (or 0 Back): ", "ERROR " + code + " - " + detail,
                    "0 Back", service.first.name() + " |", " 8  Cancel an engagement");
            assertFalse(output.contains("cancelled"), output);
            assertFalse(output.contains("Hold released"), output);
            assertEquals(List.of(new Call(service.first.id(), new EngagementId("engagement-x"))), service.calls);
        }

        var service = new Fixture();
        service.results.add(Result.success(service.view(EngagementStatus.AGREED, false, null)));
        String output = run(service, service.login(service.first) + "8\nengagement-y\n0\n0\n");
        assertOrdered(output, "Engagement status: AGREED", "0 Back", service.first.name() + " |",
                " 8  Cancel an engagement");
        assertFalse(output.contains("cancelled"), output);
        assertFalse(output.contains("Hold released"), output);
    }

    private static void assertOrdered(String text, String... snippets) {
        int position = -1;
        for (String snippet : snippets) {
            int next = text.indexOf(snippet, position + 1);
            assertTrue(next > position, "Expected '" + snippet + "' after position " + position + " in:\n" + text);
            position = next;
        }
    }

    private static String run(RegistrationService service, String script) throws IOException {
        var output = new StringWriter();
        new RegistrationConsole(new StringReader(script), output, service).run();
        return output.toString();
    }

    private record Call(MemberId memberId, EngagementId engagementId) { }

    private static class OlderService implements RegistrationService {
        final RegistrationService registration = InMemoryRegistrationService.create();
        final MemberView first = registration.register("Alex", "alex@example.org").value().orElseThrow();
        final MemberView second = registration.register("Robin", "robin@example.org").value().orElseThrow();
        final List<Call> calls = new ArrayList<>();

        String login(MemberView member) { return "2\n2\n" + member.id().value() + "\n"; }
        @Override public Result<MemberView> register(String name, String email) { return registration.register(name, email); }
        @Override public Result<MemberView> findMember(MemberId id) { return registration.findMember(id); }
        @Override public Result<MemberView> findMemberByEmail(String email) { return registration.findMemberByEmail(email); }
        @Override public Result<BalanceView> balance(MemberId id) { return registration.balance(id); }
    }

    private static class ConfirmationOnlyService extends OlderService implements ConfirmationService {
        @Override public List<CategoryView> categories() { return List.of(); }
        @Override public Result<MemberView> offerSkill(MemberId id, CategoryId category) {
            return Result.failure(FailureCode.INVALID_STATE, "unused");
        }
        @Override public Result<RequestView> createRequest(MemberId id, CategoryId category, int minutes,
                LocalDate start, LocalDate end) { return Result.failure(FailureCode.INVALID_STATE, "unused"); }
        @Override public Result<List<RequestView>> matchingRequests(MemberId id) { return Result.success(List.of()); }
        @Override public Result<ProposalView> submitProposal(MemberId id, RequestId request, LocalDate date) {
            return Result.failure(FailureCode.INVALID_STATE, "unused");
        }
        @Override public Result<List<ProposalView>> proposalsForRequest(MemberId id, RequestId request) {
            return Result.success(List.of());
        }
        @Override public Result<EngagementView> selectProposal(MemberId id, ProposalId proposal) {
            return Result.failure(FailureCode.INVALID_STATE, "unused");
        }
        @Override public Result<EngagementView> confirmEngagement(MemberId memberId, EngagementId engagementId) {
            return Result.failure(FailureCode.INVALID_STATE, "unused");
        }
    }

    private static final class Fixture extends ConfirmationOnlyService implements CancellationService {
        final List<Result<EngagementView>> results = new ArrayList<>();
        private int nextResult;

        EngagementView view(EngagementStatus status, boolean late, MemberId actor) {
            return new EngagementView(new EngagementId("engagement-9"), new ProposalId("proposal-3"),
                    new RequestId("request-2"), first.id(), second.id(), 60, LocalDate.parse("2026-12-01"),
                    status, Set.of(first.id()), actor, actor == null ? null : LocalDate.parse("2026-11-21"), late);
        }

        @Override public Result<EngagementView> cancelEngagement(MemberId memberId, EngagementId engagementId) {
            calls.add(new Call(memberId, engagementId));
            return results.get(nextResult++);
        }
    }
}
