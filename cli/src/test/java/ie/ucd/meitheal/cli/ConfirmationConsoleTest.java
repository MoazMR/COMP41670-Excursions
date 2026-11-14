package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class ConfirmationConsoleTest {
    private static final String WAITING = "Confirmation recorded. Waiting for the other participant.";
    private static final String SETTLED = "Session completed and settled.";

    @Test
    void requiresLoginBeforeCapabilityAndShowsUnavailableForOlderService() throws IOException {
        var older = new OlderService();
        String signedOut = run(older, "7\n0\n");
        assertTrue(signedOut.contains("Login is required to confirm a session"), signedOut);
        assertFalse(signedOut.contains("Engagement ID (or 0 Back): "), signedOut);
        assertEquals(List.of(), older.calls);

        String loggedIn = run(older, older.login(older.first) + "7\n0\n0\n");
        assertOrdered(loggedIn, "This option is not available yet.", "0 Back", older.first.name() + " |",
                " 7  Confirm a session");
        assertFalse(loggedIn.contains("Engagement ID (or 0 Back): "), loggedIn);
        assertEquals(List.of(), older.calls);
    }

    @Test
    void trimsIdsRetriesBlanksAndBackOrEscapeDoNotCallService() throws IOException {
        var service = new Fixture();
        service.results.add(Result.success(service.view(EngagementStatus.AGREED)));
        String output = run(service, service.login(service.first)
                + "7\n\n   \n  engagement-9  \n0\n0\n0\n");
        assertOrdered(output, " 7  Confirm a session", "Engagement ID (or 0 Back): ",
                "Enter an engagement ID, or 0 Back.", "Engagement ID (or 0 Back): ", WAITING,
                "0 Back", service.first.name() + " |", " 7  Confirm a session");
        assertEquals(List.of(new Call(service.first.id(), new EngagementId("engagement-9"))), service.calls);

        var back = new Fixture();
        run(back, back.login(back.first) + "7\npartially-entered\u001b0\n");
        assertEquals(List.of(), back.calls);

        var zero = new Fixture();
        run(zero, zero.login(zero.first) + "7\n0\n0\n");
        assertEquals(List.of(), zero.calls);
    }

    @Test
    void twoMembersConfirmSameEngagementAndSeeStatusSpecificOutcome() throws IOException {
        var service = new Fixture();
        service.results.add(Result.success(service.view(EngagementStatus.AGREED)));
        service.results.add(Result.success(service.view(EngagementStatus.COMPLETED)));
        String output = run(service, service.login(service.first) + "7\nengagement-9\n0\n"
                + service.login(service.second) + "7\nengagement-9\n\u001b0\n");

        int waiting = output.indexOf(WAITING);
        int waitingBack = output.indexOf("0 Back", waiting);
        int completed = output.indexOf(SETTLED, waitingBack);
        assertTrue(waiting >= 0 && waitingBack > waiting && completed > waitingBack, output);
        assertEquals(1, occurrences(output, SETTLED), output);
        assertFalse(output.substring(waiting, waitingBack).contains("settled"), output);
        assertOrdered(output.substring(completed), SETTLED, "0 Back", service.second.name() + " |",
                " 7  Confirm a session");
        assertEquals(List.of(new Call(service.first.id(), new EngagementId("engagement-9")),
                new Call(service.second.id(), new EngagementId("engagement-9"))), service.calls);
    }

    @Test
    void failuresShowCodeAndDetailWithoutSuccessClaimsAndReturnToMenu() throws IOException {
        List<FailureCode> codes = List.of(FailureCode.ENGAGEMENT_NOT_FOUND, FailureCode.NOT_PARTICIPANT,
                FailureCode.BUSINESS_REJECTION, FailureCode.DUPLICATE, FailureCode.INVALID_STATE,
                FailureCode.ATOMIC_OPERATION_FAILED);
        for (FailureCode code : codes) {
            var service = new Fixture();
            String detail = "detail for " + code;
            service.results.add(Result.failure(code, detail));
            String output = run(service, service.login(service.first) + "7\nengagement-9\n0\n0\n");
            assertOrdered(output, "Engagement ID (or 0 Back): ", "ERROR " + code + " - " + detail,
                    "0 Back", service.first.name() + " |", " 7  Confirm a session");
            assertFalse(output.contains(WAITING), output);
            assertFalse(output.contains("completed"), output);
            assertFalse(output.contains("settled"), output);
            assertEquals(List.of(new Call(service.first.id(), new EngagementId("engagement-9"))), service.calls);
        }
    }

    private static void assertOrdered(String text, String... snippets) {
        int position = -1;
        for (String snippet : snippets) {
            int next = text.indexOf(snippet, position + 1);
            assertTrue(next > position, "Expected '" + snippet + "' after position " + position + " in:\n" + text);
            position = next;
        }
    }

    private static int occurrences(String text, String snippet) {
        int count = 0;
        int position = 0;
        while ((position = text.indexOf(snippet, position)) >= 0) {
            count++;
            position += snippet.length();
        }
        return count;
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

    private static final class Fixture extends OlderService implements ConfirmationService {
        final List<Result<EngagementView>> results = new ArrayList<>();
        int nextResult;

        EngagementView view(EngagementStatus status) {
            return new EngagementView(new EngagementId("engagement-9"), new ProposalId("proposal-3"),
                    new RequestId("request-2"), first.id(), second.id(), 60, LocalDate.parse("2026-12-01"),
                    status, status == EngagementStatus.AGREED ? Set.of(first.id())
                            : Set.of(first.id(), second.id()), null, null, false);
        }

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
            calls.add(new Call(memberId, engagementId));
            return results.get(nextResult++);
        }
    }
}
