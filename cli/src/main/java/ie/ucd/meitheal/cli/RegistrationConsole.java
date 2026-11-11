package ie.ucd.meitheal.cli;

import ie.ucd.meitheal.app.RegistrationService;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.MemberView;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

/** Interactive registration and account console. */
public final class RegistrationConsole {
    private final Reader input;
    private final Writer output;
    private final RegistrationService service;
    private final boolean terminal;
    private MemberView selected;
    private boolean running = true;

    private static final String MENU = """
             1  Register
             2  Login
             3  Publish a skill offer
             4  Raise a request
             5  Find matches and manage proposals
             6  Select a proposal
             7  Confirm a session
             8  Cancel an engagement
             9  Suspend or reinstate a member
            10  Apply for a community grant
            11  View balance and history
            12  View report
            13  View or advance simulated date
             0  Exit""";

    public RegistrationConsole(Reader input, Writer output, RegistrationService service) {
        this(input, output, service, false);
    }

    RegistrationConsole(Reader input, Writer output, RegistrationService service, boolean terminal) {
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
        this.service = Objects.requireNonNull(service);
        this.terminal = terminal;
    }

    public void run() throws IOException {
        while (running) {
            mainMenu();
        }
    }

    private void mainMenu() throws IOException {
        if (selected != null) {
            line(selected.name() + " | " + selected.email() + " | " + selected.id().value());
        }
        line(MENU);
        String choice = readLine("> ");
        if (choice == null) return;
        switch (choice.trim()) {
            case "0" -> running = false;
            case "1" -> registration();
            case "2" -> login();
            case "11" -> {
                if (requireMember("view balance and history")) balanceMenu();
            }
            case "3", "4", "5", "6", "7", "8", "10" -> unavailable(choice, true);
            case "9", "12", "13" -> unavailable(choice, false);
            default -> line("Choose one of the listed options.");
        }
    }

    private void registration() throws IOException {
        clear();
        while (running) {
            String name = readLine("Name: ");
            if (name == null) return;
            String email = readLine("Email: ");
            if (email == null) continue;

            Result<MemberView> result = service.register(name, email);
            if (result.isFailure()) {
                failure(result);
            } else {
                MemberView member = result.value().orElseThrow();
                line("Registration successful\nName: " + member.name() + "   Email: " + member.email()
                        + "\nMember ID: " + member.id().value() + "   Current balance: "
                        + member.ledgerCredits() + " credits");
                pauseForBack();
            }
            return;
        }
    }

    private void login() throws IOException {
        clear();
        while (running) {
            line("1 Email address\n2 Member ID\n0 Back");
            String method = readLine("> ");
            if (method == null || method.trim().equals("0")) return;
            boolean byEmail = method.trim().equals("1");
            if (!byEmail && !method.trim().equals("2")) {
                line("Choose 1, 2 or 0.");
                continue;
            }
            clear();
            String value = readLine(byEmail ? "Email: " : "Member ID: ");
            if (value == null) continue;
            if (!byEmail && !value.trim().matches("[1-9][0-9]{5}")) {
                line("Enter a six-digit member ID.");
                continue;
            }
            Result<MemberView> result = byEmail ? service.findMemberByEmail(value)
                    : service.findMember(new MemberId(value.trim()));
            if (result.isSuccess()) {
                selected = result.value().orElseThrow();
                return;
            }
            if (result.failureCode().orElseThrow() == FailureCode.MEMBER_NOT_FOUND) {
                line(byEmail ? "No matching email address found." : "No matching member ID found.");
            } else {
                writeFailure(result);
            }
        }
    }

    private void balanceMenu() throws IOException {
        clear();
        while (running) {
            line("1 Balance\n2 Ledger history (unavailable)\n3 Engagement history (unavailable)\n0 Back");
            String choice = readLine("> ");
            if (choice == null || choice.trim().equals("0")) return;
            switch (choice.trim()) {
                case "1" -> showBalance();
                case "2", "3" -> unavailable(choice, false);
                default -> line("Choose 1, 2, 3 or 0.");
            }
        }
    }

    private void showBalance() throws IOException {
        clear();
        Result<BalanceView> result = service.balance(selected.id());
        if (result.isFailure()) {
            failure(result);
            return;
        }
        BalanceView balance = result.value().orElseThrow();
        line("Ledger balance: " + balance.ledgerBalance() + " credits\nHeld credits: " + balance.held()
                + "\nAvailable credits: " + balance.available());
        pauseForBack();
    }

    private boolean requireMember(String action) throws IOException {
        if (selected != null) return true;
        line("Login is required to " + action + ". Choose 2 Login.");
        return false;
    }

    private void unavailable(String option, boolean memberRequired) throws IOException {
        if (memberRequired && !requireMember("use option " + option)) return;
        clear();
        line("This option is not available yet.");
        pauseForBack();
    }

    private void pauseForBack() throws IOException {
        line("0 Back");
        while (running) {
            String choice = readLine("> ");
            if (choice == null || choice.trim().equals("0")) return;
            line("Choose 0 Back.");
        }
    }

    private void failure(Result<?> result) throws IOException {
        writeFailure(result);
        pauseForBack();
    }

    private void writeFailure(Result<?> result) throws IOException {
        line("ERROR " + result.failureCode().orElseThrow() + result.detail().map(s -> " - " + s).orElse(""));
    }

    // Escape cancels the current prompt. End of input also ends the outer menu loop.
    private String readLine(String prompt) throws IOException {
        output.write(prompt);
        output.flush();
        StringBuilder value = new StringBuilder();
        while (true) {
            int ch = input.read();
            if (ch < 0 || ch == 4) {
                running = false;
                return null;
            }
            if (ch == 27 || ch == '\n') {
                if (terminal) line("");
                return ch == 27 ? null : value.toString();
            }
            if (ch == '\r') continue;
            if (ch == 8 || ch == 127) {
                if (!value.isEmpty()) {
                    int end = value.length();
                    value.delete(end - Character.charCount(value.codePointBefore(end)), end);
                    if (terminal) output.write("\b \b");
                }
            } else {
                value.append((char) ch);
                if (terminal) output.write(ch);
            }
            if (terminal) output.flush();
        }
    }

    private void clear() throws IOException {
        if (terminal) {
            output.write("\u001b[2J\u001b[H");
            output.flush();
        }
    }

    private void line(String text) throws IOException {
        output.write(text + "\n");
        output.flush();
    }
}
