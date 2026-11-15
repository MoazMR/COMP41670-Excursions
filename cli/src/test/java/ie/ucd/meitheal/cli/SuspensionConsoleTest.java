package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.*;

import ie.ucd.meitheal.app.InMemoryRegistrationService;
import ie.ucd.meitheal.app.RegistrationService;
import ie.ucd.meitheal.app.SuspensionService;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.RequestId;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SuspensionConsoleTest {
    @Test void signedOutIdAndEmailOperationsUseResolvedMemberOnce() throws IOException {
        var service = new Fixture();
        String idScript = "9\n100001\n1\n0\n0\n";
        String idOutput = run(service, idScript);
        ordered(idOutput, "Member ID or email (or 0 Back): ", "Current status: ACTIVE", "1 Suspend",
                "Member suspended.", "Current status: SUSPENDED", "0 Back", "9  Suspend or reinstate a member");
        assertEquals(1, service.mutations);
        assertEquals(List.of("id:100001", "suspend:100001"), service.calls);
        assertEquals(1, service.mutations);
        assertNoSelectedBanner(idOutput);

        service = new Fixture();
        service.setStatus(service.other.id(), "SUSPENDED");
        String emailScript = "9\n alex@example.org \n2\n0\n0\n";
        String emailOutput = run(service, emailScript);
        ordered(emailOutput, "Member ID or email (or 0 Back): ", "Current status: SUSPENDED", "2 Reinstate",
                "Member reinstated.", "Current status: ACTIVE", "0 Back", "9  Suspend or reinstate a member");
        assertEquals(List.of("email:alex@example.org", "reinstate:100002"), service.calls);
        assertEquals(1, service.mutations);
        assertNoSelectedBanner(emailOutput);
    }

    @Test void missingMemberAndPromptValidationAndNavigation() throws IOException {
        var service = new Fixture();
        String missing = run(service, "9\n999999\n0\n0\n");
        ordered(missing, "Member ID or email (or 0 Back): ", "ERROR MEMBER_NOT_FOUND", "Member does not exist", "0 Back");
        assertEquals(List.of("id:999999"), service.calls);
        assertEquals(0, service.mutations);

        service = new Fixture();
        String retryScript = "9\n\n   \n100001\nwat\n1\n0\n0\n";
        String retry = run(service, retryScript);
        ordered(retry, "Enter a member ID or email, or 0 Back.", "Current status: ACTIVE",
                "Choose 1, 2 or 0.", "Member suspended.", "Current status: SUSPENDED");
        assertEquals(List.of("id:100001", "suspend:100001"), service.calls);

        for (String script : List.of("9\n0\n0\n", "9\npartial\u001b0\n")) {
            service = new Fixture();
            String output = run(service, script);
            ordered(output, "Member ID or email (or 0 Back): ", "9  Suspend or reinstate a member");
            assertEquals(List.of(), service.calls, script);
            assertEquals(0, service.mutations, script);
        }
        for (String script : List.of("9\n100002\n0\n0\n", "9\n100002\n1\u001b0\n")) {
            service = new Fixture();
            String output = run(service, script);
            ordered(output, "Member ID or email (or 0 Back): ", "Current status: ACTIVE", "1 Suspend", "9  Suspend or reinstate a member");
            assertEquals(List.of("id:100002"), service.calls, script);
            assertEquals(0, service.mutations, script);
        }
    }

    @Test void invalidStateIsReportedForRepeatedSuspendAndReinstate() throws IOException {
        var suspended = new Fixture();
        suspended.setStatus(suspended.other.id(), "SUSPENDED");
        String suspend = run(suspended, "9\n100002\n1\n0\n0\n");
        ordered(suspend, "Current status: SUSPENDED", "ERROR INVALID_STATE",
                "Only an active member can be suspended", "0 Back");
        assertFalse(suspend.contains("Member suspended."));
        assertEquals(List.of("id:100002", "suspend:100002"), suspended.calls);
        assertEquals(0, suspended.mutations);

        var active = new Fixture();
        String reinstate = run(active, "9\n100001\n2\n0\n0\n");
        ordered(reinstate, "Current status: ACTIVE", "ERROR INVALID_STATE",
                "Only a suspended member can be reinstated", "0 Back");
        assertFalse(reinstate.contains("Member reinstated."));
        assertEquals(List.of("id:100001", "reinstate:100001"), active.calls);
        assertEquals(0, active.mutations);
    }

    @Test void selectedLoginIsPreservedForOtherMemberAndRefreshedForSameMember() throws IOException {
        var service = new Fixture();
        String otherScript = service.login(service.selected) + "9\n100002\n1\n0\n0\n";
        String other = run(service, otherScript);
        ordered(other, "Pat | pat@example.org | 100001", "Member suspended.", "Current status: SUSPENDED",
                "Pat | pat@example.org | 100001", "9  Suspend or reinstate a member");
        assertTrue(other.contains("Pat | pat@example.org | 100001"));
        assertFalse(other.contains("Updated Alex | alex-updated@example.org | 100002"));
        assertEquals(List.of("id:100001", "id:100002", "suspend:100002"), service.calls);

        service = new Fixture();
        String ownScript = service.login(service.selected) + "9\n100001\n1\n0\n0\n";
        String own = run(service, ownScript);
        ordered(own, "Pat | pat@example.org | 100001", "Member suspended.", "Current status: SUSPENDED",
                "Updated Pat | pat-updated@example.org | 100001");
        assertTrue(own.contains("Updated Pat | pat-updated@example.org | 100001"));
        int operationAt = own.indexOf("Member suspended.");
        assertFalse(own.indexOf("Pat | pat@example.org | 100001", operationAt) >= 0);
        assertEquals(List.of("id:100001", "id:100001", "suspend:100001"), service.calls);
    }

    @Test void resultEscapeAndOlderServiceFallbackUsePublicConstructor() throws IOException {
        var service = new Fixture();
        String script = "9\n100002\n1\n\u001b0\n";
        String escaped = run(service, script);
        ordered(escaped, "Member suspended.", "Current status: SUSPENDED", "0 Back",
                "9  Suspend or reinstate a member");
        assertEquals(List.of("id:100002", "suspend:100002"), service.calls);
        assertEquals(1, service.mutations);

        RegistrationService old = new RegistrationOnlyService(InMemoryRegistrationService.create());
        String fallback = run(old, "9\n0\n0\n");
        ordered(fallback, "This option is not available yet.", "0 Back", "9  Suspend or reinstate a member");
        assertNoSelectedBanner(fallback);
    }

    private static String run(RegistrationService service, String input) throws IOException {
        var writer = new StringWriter();
        new RegistrationConsole(new StringReader(input), writer, service).run();
        return writer.toString();
    }

    private static void assertNoSelectedBanner(String output) {
        assertFalse(output.contains(" | "), "Signed-out menu should not show a member banner: " + output);
    }

    private static void ordered(String text, String... parts) {
        int at = -1;
        for (String part : parts) {
            int next = text.indexOf(part, at + 1);
            assertTrue(next > at, "Missing in order: " + part + "\n" + text);
            at = next;
        }
    }

    private static final class Fixture implements SuspensionService {
        private static final String ALREADY_SUSPENDED = "Only an active member can be suspended";
        private static final String ALREADY_ACTIVE = "Only a suspended member can be reinstated";
        final RegistrationService base = InMemoryRegistrationService.create();
        final MemberView selected = base.register("Pat", "pat@example.org").value().orElseThrow();
        final MemberView other = base.register("Alex", "alex@example.org").value().orElseThrow();
        final List<String> calls = new ArrayList<>();
        final Map<MemberId, MemberView> changed = new HashMap<>();
        int mutations;

        String login(MemberView member) { return "2\n2\n" + member.id().value() + "\n"; }
        MemberView lookup(MemberId id) { return changed.getOrDefault(id, base.findMember(id).value().orElseThrow()); }
        void setStatus(MemberId id, String status) {
            MemberView old = lookup(id);
            changed.put(id, view(old, status.equals("SUSPENDED"), old.email(), old.name()));
        }

        @Override public Result<MemberView> findMember(MemberId id) {
            calls.add("id:" + id.value());
            MemberView member = changed.get(id);
            return member == null ? base.findMember(id) : Result.success(member);
        }

        @Override public Result<MemberView> findMemberByEmail(String email) {
            calls.add("email:" + email);
            for (MemberId id : List.of(selected.id(), other.id())) {
                MemberView member = lookup(id);
                if (member.email().equalsIgnoreCase(email)) return Result.success(member);
            }
            return Result.failure(FailureCode.MEMBER_NOT_FOUND, "No member for email " + email);
        }

        @Override public Result<MemberView> register(String name, String email) { return base.register(name, email); }
        @Override public Result<BalanceView> balance(MemberId id) { return base.balance(id); }

        @Override public List<CategoryView> categories() { return List.of(); }
        @Override public Result<MemberView> offerSkill(MemberId id, CategoryId categoryId) {
            return Result.failure(FailureCode.INVALID_STATE, "unused offer");
        }
        @Override public Result<RequestView> createRequest(MemberId id, CategoryId categoryId, int minutes,
                java.time.LocalDate start, java.time.LocalDate end) {
            return Result.failure(FailureCode.INVALID_STATE, "unused request");
        }
        @Override public Result<List<RequestView>> matchingRequests(MemberId id) {
            return Result.success(List.of());
        }
        @Override public Result<ie.ucd.meitheal.shared.view.ProposalView> submitProposal(MemberId id,
                RequestId requestId, java.time.LocalDate date) {
            return Result.failure(FailureCode.INVALID_STATE, "unused proposal");
        }
        @Override public Result<List<ProposalView>> proposalsForRequest(MemberId id, RequestId requestId) {
            return Result.success(List.of());
        }
        @Override public Result<EngagementView> selectProposal(MemberId id,
                ie.ucd.meitheal.shared.id.ProposalId proposalId) {
            return Result.failure(FailureCode.INVALID_STATE, "unused selection");
        }

        @Override public Result<MemberView> suspendMember(MemberId id) {
            calls.add("suspend:" + id.value());
            MemberView old = lookup(id);
            if (old.status().name().equals("SUSPENDED")) {
                return Result.failure(FailureCode.INVALID_STATE, ALREADY_SUSPENDED);
            }
            mutations++;
            MemberView updated = view(old, true, id.equals(other.id()) ? "alex-updated@example.org" : "pat-updated@example.org",
                    id.equals(other.id()) ? "Updated Alex" : "Updated Pat");
            changed.put(id, updated);
            return Result.success(updated);
        }

        @Override public Result<MemberView> reinstateMember(MemberId id) {
            calls.add("reinstate:" + id.value());
            MemberView old = lookup(id);
            if (old.status().name().equals("ACTIVE")) {
                return Result.failure(FailureCode.INVALID_STATE, ALREADY_ACTIVE);
            }
            mutations++;
            MemberView updated = view(old, false, id.equals(other.id()) ? "alex-updated@example.org" : "pat-updated@example.org",
                    id.equals(other.id()) ? "Updated Alex" : "Updated Pat");
            changed.put(id, updated);
            return Result.success(updated);
        }

        private MemberView view(MemberView old, boolean suspended, String email, String name) {
            return new MemberView(old.id(), name, email,
                    suspended ? ie.ucd.meitheal.shared.model.MemberStatus.SUSPENDED
                            : ie.ucd.meitheal.shared.model.MemberStatus.ACTIVE,
                    old.skills(), old.ledgerCredits());
        }

        @Override public Result<EngagementView> confirmEngagement(MemberId memberId, EngagementId engagementId) {
            return Result.failure(FailureCode.INVALID_STATE, "unused confirmation");
        }

        @Override public Result<EngagementView> cancelEngagement(MemberId memberId, EngagementId engagementId) {
            return Result.failure(FailureCode.INVALID_STATE, "unused cancellation");
        }
    }

    private static final class RegistrationOnlyService implements RegistrationService {
        private final RegistrationService delegate;

        RegistrationOnlyService(RegistrationService delegate) { this.delegate = delegate; }

        @Override public Result<MemberView> register(String name, String email) { return delegate.register(name, email); }
        @Override public Result<MemberView> findMember(MemberId id) { return delegate.findMember(id); }
        @Override public Result<MemberView> findMemberByEmail(String email) { return delegate.findMemberByEmail(email); }
        @Override public Result<BalanceView> balance(MemberId id) { return delegate.balance(id); }
    }
}
