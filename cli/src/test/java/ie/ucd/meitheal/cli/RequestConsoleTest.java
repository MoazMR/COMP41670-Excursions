package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.ucd.meitheal.app.InMemoryRegistrationService;
import ie.ucd.meitheal.app.RegistrationService;
import ie.ucd.meitheal.app.RequestService;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.ProposalId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.ProposalStatus;
import ie.ucd.meitheal.shared.model.RequestStatus;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.RequestView;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestConsoleTest {
    @Test
    void memberCanPublishRaiseAndProposeThroughTheNumberedMenu() throws IOException {
        var service = new RequestServiceFixture();
        String output = run(service, "3\n4\n5\n" + service.login()
                + "3\n2\n0\n4\n1\n90\n2026-11-23\n2026-11-24\n0\n"
                + "5\n1\n2026-11-24\n0\n0\n0\n");

        assertTrue(output.contains("Login is required to publish a skill offer"), output);
        assertTrue(output.contains("Login is required to raise a request"), output);
        assertTrue(output.contains("Login is required to find matches"), output);
        assertTrue(output.contains("Skill offer published: Tutoring"), output);
        assertTrue(output.contains("Request raised. Request ID: request-1"), output);
        assertTrue(output.contains("Request request-1 | gardening | 90 minutes | 2026-11-23 to 2026-11-24"), output);
        assertTrue(output.contains("Proposal submitted. Proposal ID: proposal-1"), output);
        assertTrue(output.contains("No matching requests."), output);
        assertEquals(List.of(
                List.of(service.member.id(), new CategoryId("tutoring")),
                List.of(service.member.id(), new CategoryId("gardening"), 90,
                        LocalDate.parse("2026-11-23"), LocalDate.parse("2026-11-24")),
                List.of(service.member.id()),
                List.of(service.member.id(), new RequestId("request-1"), LocalDate.parse("2026-11-24")),
                List.of(service.member.id())),
                service.calls);
    }

    @Test
    void invalidAndCancelledInputOnlySubmitsAfterACompleteChoice() throws IOException {
        var service = new RequestServiceFixture();
        String output = run(service, service.login()
                + "3\n0\n4\n1\n481\n90\n2026-11-23\n2026-11-\u001b\u001b\u001b0\n"
                + "5\n8\n1\n2026-02-30\npartial\u001b1\n2026-11-25\n0\n0\n0\n");

        assertTrue(output.contains("Enter a whole number from 1 to 480"), output);
        assertTrue(output.contains("Enter a whole number from 1 to 1"), output);
        assertTrue(output.contains("Enter a date"), output);
        assertTrue(output.contains("Latest date (YYYY-MM-DD): Earliest date (YYYY-MM-DD): 0 Back"), output);
        assertTrue(output.contains("ERROR INVALID_STATE - request no longer open"), output);
        assertEquals(List.of(List.of(service.member.id()), List.of(service.member.id()),
                List.of(service.member.id(), new RequestId("request-1"), LocalDate.parse("2026-11-25")),
                List.of(service.member.id())),
                service.calls);
    }

    @Test
    void emptyResultsReturnToTheMainMenu() throws IOException {
        var service = new RequestServiceFixture();
        service.categories = List.of();
        service.matches = List.of();
        String output = run(service, service.login() + "3\n0\n5\n0\n0\n");

        assertTrue(output.contains("No categories are available."), output);
        assertTrue(output.contains("No matching requests."), output);
        assertEquals(List.of(List.of(service.member.id())), service.calls);
    }

    private static String run(RequestService service, String script) throws IOException {
        var output = new StringWriter();
        new RegistrationConsole(new StringReader(script), output, service).run();
        return output.toString();
    }

    private static final class RequestServiceFixture implements RequestService {
        private final RegistrationService registration = InMemoryRegistrationService.create();
        private final MemberView member = registration.register("Alex", "alex@example.org").value().orElseThrow();
        private final RequestId requestId = new RequestId("request-1");
        private final RequestView request = new RequestView(requestId, new MemberId("100002"),
                new CategoryId("gardening"), 90, LocalDate.parse("2026-11-23"), LocalDate.parse("2026-11-24"),
                RequestStatus.OPEN);
        private final List<List<?>> calls = new ArrayList<>();
        private List<CategoryView> categories = List.of(new CategoryView(request.categoryId(), "Gardening"),
                new CategoryView(new CategoryId("tutoring"), "Tutoring"));
        private List<RequestView> matches = List.of(request);

        String login() {
            return "2\n2\n" + member.id().value() + "\n";
        }

        @Override
        public List<CategoryView> categories() {
            return categories;
        }

        @Override
        public Result<MemberView> offerSkill(MemberId memberId, CategoryId categoryId) {
            calls.add(List.of(memberId, categoryId));
            return Result.success(member);
        }

        @Override
        public Result<RequestView> createRequest(MemberId memberId, CategoryId categoryId,
                int durationMinutes, LocalDate windowStart, LocalDate windowEnd) {
            calls.add(List.of(memberId, categoryId, durationMinutes, windowStart, windowEnd));
            return Result.success(new RequestView(requestId, memberId, categoryId, durationMinutes, windowStart,
                    windowEnd, RequestStatus.OPEN));
        }

        @Override
        public Result<List<RequestView>> matchingRequests(MemberId helperId) {
            calls.add(List.of(helperId));
            return Result.success(matches);
        }

        @Override
        public Result<ProposalView> submitProposal(MemberId helperId, RequestId id, LocalDate proposedDate) {
            calls.add(List.of(helperId, id, proposedDate));
            if (proposedDate.isAfter(request.windowEnd())) {
                return Result.failure(FailureCode.INVALID_STATE, "request no longer open");
            }
            matches = List.of();
            return Result.success(new ProposalView(new ProposalId("proposal-1"), id, helperId,
                    proposedDate, ProposalStatus.PENDING));
        }

        @Override
        public Result<MemberView> register(String name, String email) {
            return registration.register(name, email);
        }

        @Override
        public Result<MemberView> findMember(MemberId memberId) {
            return registration.findMember(memberId);
        }

        @Override
        public Result<MemberView> findMemberByEmail(String email) {
            return registration.findMemberByEmail(email);
        }

        @Override
        public Result<BalanceView> balance(MemberId memberId) {
            return registration.balance(memberId);
        }
    }
}
