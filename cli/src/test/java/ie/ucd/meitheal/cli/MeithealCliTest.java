package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.ucd.meitheal.app.MeithealService;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.model.MemberStatus;
import java.time.LocalDate;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MeithealCliTest {
    @Test
    void typedCommandsRenderServiceValuesAndFailuresAndContinue() throws Exception {
        StrictService service = new StrictService();
        StringWriter output = new StringWriter();
        new MeithealCli(new StringReader("register \"Ada Lovelace\" ada@example.test\n"
                + "balance 100001\nbalance 100002\nbalance 100003\n"), output, service).run();

        assertEquals(1, service.registerCalls);
        assertEquals(3, service.balanceCalls);
        assertEquals("ada@example.test", service.registeredEmail);
        assertEquals("100003", service.lastBalanceId.value());
        assertEquals("""
                Member ID: 100001
                Name: Ada Lovelace
                Status: ACTIVE
                Ledger credits: 120
                Member ID: 100001
                Ledger balance: -60
                Held credits: 120
                Available credits: -180
                ERROR MEMBER_NOT_FOUND
                ERROR INVALID_INPUT - malformed identifier
                """, output.toString());
    }

    @Test
    void invalidArityAndBlankIdentifiersAreRejectedBeforeServiceCall() throws Exception {
        StrictService service = new StrictService();
        StringWriter output = new StringWriter();
        new MeithealCli(new StringReader("register only\nregister \"\" e@x.test\n"
                + "balance\nbalance \" \"\nhelp\n"), output, service).run();

        assertEquals(1, service.registerCalls);
        assertEquals(0, service.balanceCalls);
        assertTrue(output.toString().startsWith("ERROR Command 'register' expects 2 arguments but got 1."));
        assertTrue(output.toString().contains("ERROR INVALID_INPUT - malformed identifier"));
        assertTrue(output.toString().contains("ERROR Command 'balance' expects 1 arguments but got 0."));
        assertTrue(output.toString().contains("ERROR value must not be blank"));
        assertTrue(output.toString().contains("Supported commands:"));
    }

    @Test
    void legacyConstructorKeepsOriginalHelpAndTreatsTypedCommandsAsUnknown() throws Exception {
        StringWriter output = new StringWriter();
        new MeithealCli(new StringReader("help\nregister Ada a@example.test\nbalance 100001\n"),
                output).run();

        assertEquals("""
                Supported commands:
                  help - Show available commands.
                  exit - Exit the CLI.
                ERROR Unknown command 'register'. Type 'help' for available commands.
                ERROR Unknown command 'balance'. Type 'help' for available commands.
                """, output.toString());
    }

    private static final class StrictService implements MeithealService {
        int registerCalls;
        int balanceCalls;
        String registeredEmail;
        MemberId lastBalanceId;

        @Override public Result<MemberView> register(String name, String email) {
            registerCalls++;
            registeredEmail = email;
            if (name.isBlank() || email.isBlank()) {
                return Result.failure(FailureCode.INVALID_INPUT, "malformed identifier");
            }
            return Result.success(new MemberView(new MemberId("100001"), name, email,
                    MemberStatus.ACTIVE, Set.of(), 120));
        }
        @Override public Result<BalanceView> balance(MemberId id) {
            balanceCalls++;
            lastBalanceId = id;
            if (id.value().equals("100001")) return Result.success(new BalanceView(id, -60, 120, -180));
            if (id.value().equals("100002")) return Result.failure(FailureCode.MEMBER_NOT_FOUND, null);
            return Result.failure(FailureCode.INVALID_INPUT, "malformed identifier");
        }
        @Override public Result<MemberView> findMember(MemberId id) { throw new AssertionError(); }
        @Override public Result<MemberView> findMemberByEmail(String email) { throw new AssertionError(); }
        @Override public Result<MemberView> suspendMember(MemberId id) { throw new AssertionError(); }
        @Override public Result<MemberView> reinstateMember(MemberId id) { throw new AssertionError(); }
        @Override public Result<ie.ucd.meitheal.shared.view.CategoryView> findCategory(ie.ucd.meitheal.shared.id.CategoryId id) { throw new AssertionError(); }
        @Override public List<ie.ucd.meitheal.shared.view.CategoryView> categories() { throw new AssertionError(); }
        @Override public Result<MemberView> offerSkill(MemberId id, ie.ucd.meitheal.shared.id.CategoryId categoryId) { throw new AssertionError(); }
        @Override public Result<ie.ucd.meitheal.shared.view.RequestView> createRequest(MemberId id, ie.ucd.meitheal.shared.id.CategoryId categoryId, int duration, LocalDate start, LocalDate end) { throw new AssertionError(); }
        @Override public Result<ie.ucd.meitheal.shared.view.RequestView> findRequest(ie.ucd.meitheal.shared.id.RequestId id) { throw new AssertionError(); }
        @Override public Result<List<ie.ucd.meitheal.shared.view.RequestView>> matchingRequests(MemberId id) { throw new AssertionError(); }
        @Override public Result<ie.ucd.meitheal.shared.view.ProposalView> submitProposal(MemberId id, ie.ucd.meitheal.shared.id.RequestId requestId, LocalDate date) { throw new AssertionError(); }
        @Override public Result<List<ie.ucd.meitheal.shared.view.ProposalView>> proposalsForRequest(MemberId id, ie.ucd.meitheal.shared.id.RequestId requestId) { throw new AssertionError(); }
        @Override public Result<List<ie.ucd.meitheal.shared.view.ProposalView>> proposalsForHelper(MemberId id) { throw new AssertionError(); }
        @Override public Result<ie.ucd.meitheal.shared.view.ProposalView> rejectProposal(MemberId id, ie.ucd.meitheal.shared.id.ProposalId proposalId) { throw new AssertionError(); }
        @Override public Result<ie.ucd.meitheal.shared.view.ProposalView> withdrawProposal(MemberId id, ie.ucd.meitheal.shared.id.ProposalId proposalId) { throw new AssertionError(); }
        @Override public Result<ie.ucd.meitheal.shared.view.EngagementView> selectProposal(MemberId id, ie.ucd.meitheal.shared.id.ProposalId proposalId) { throw new AssertionError(); }
        @Override public Result<ie.ucd.meitheal.shared.view.EngagementView> confirmEngagement(MemberId id, ie.ucd.meitheal.shared.id.EngagementId engagementId) { throw new AssertionError(); }
        @Override public Result<ie.ucd.meitheal.shared.view.EngagementView> cancelEngagement(MemberId id, ie.ucd.meitheal.shared.id.EngagementId engagementId) { throw new AssertionError(); }
        @Override public Result<ie.ucd.meitheal.shared.view.CommunityGrantView> applyForCommunityGrant(MemberId id, long amount) { throw new AssertionError(); }
        @Override public Result<List<ie.ucd.meitheal.shared.view.TransactionView>> memberLedgerHistory(MemberId id) { throw new AssertionError(); }
        @Override public Result<List<ie.ucd.meitheal.shared.view.EngagementView>> ownEngagementHistory(MemberId id) { throw new AssertionError(); }
        @Override public ie.ucd.meitheal.shared.view.ReportView report() { throw new AssertionError(); }
        @Override public LocalDate currentDate() { throw new AssertionError(); }
        @Override public Result<LocalDate> advanceDate(LocalDate date) { throw new AssertionError(); }
    }
    @Test
    void scriptedErrorsAndBlankLinesContinueToValidCommands() throws Exception {
        StringWriter output = new StringWriter();

        new MeithealCli(new StringReader(
                "\nunknown\nhelp extra\nhelp \"unfinished\nhelp\n"), output).run();

        assertEquals("""
                ERROR Unknown command 'unknown'. Type 'help' for available commands.
                ERROR Command 'help' expects 0 arguments but got 1.
                ERROR Invalid command syntax: unmatched double quote.
                Supported commands:
                  help - Show available commands.
                  exit - Exit the CLI.
                """, output.toString());
    }

    @Test
    void wrongExitArgumentCountAllowsTheNextCommand() throws Exception {
        StringWriter output = new StringWriter();

        new MeithealCli(new StringReader("exit now\nhelp\nexit\n"), output).run();

        assertEquals("""
                ERROR Command 'exit' expects 0 arguments but got 1.
                Supported commands:
                  help - Show available commands.
                  exit - Exit the CLI.
                """, output.toString());
    }

    @Test
    void exitStopsProcessingSubsequentInput() throws Exception {
        StringWriter output = new StringWriter();

        new MeithealCli(new StringReader("help\nexit\nhelp\nunknown\n"), output).run();

        assertEquals("""
                Supported commands:
                  help - Show available commands.
                  exit - Exit the CLI.
                """, output.toString());
    }

    @Test
    void eofTerminatesWithoutOutput() throws Exception {
        StringWriter output = new StringWriter();

        new MeithealCli(new StringReader(""), output).run();

        assertEquals("", output.toString());
    }
}
