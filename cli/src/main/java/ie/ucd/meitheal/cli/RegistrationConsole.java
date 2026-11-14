package ie.ucd.meitheal.cli;

import ie.ucd.meitheal.app.ConfirmationService;
import ie.ucd.meitheal.app.RegistrationService;
import ie.ucd.meitheal.app.RequestService;
import ie.ucd.meitheal.app.SelectionService;
import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.model.EngagementStatus;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.CategoryView;
import ie.ucd.meitheal.shared.view.EngagementView;
import ie.ucd.meitheal.shared.view.MemberView;
import ie.ucd.meitheal.shared.view.ProposalView;
import ie.ucd.meitheal.shared.view.RequestView;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
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
            case "3", "4", "5" -> requestAction(choice.trim());
            case "6" -> selectProposal();
            case "11" -> {
                if (requireMember("view balance and history")) balanceMenu();
            }
            case "7" -> confirmEngagement();
            case "8", "10" -> unavailable(choice, true);
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

    private void requestAction(String choice) throws IOException {
        String action = switch (choice) {
            case "3" -> "publish a skill offer";
            case "4" -> "raise a request";
            case "5" -> "find matches and manage proposals";
            default -> throw new IllegalArgumentException("Unknown request action: " + choice);
        };
        if (!requireMember(action)) return;
        if (!(service instanceof RequestService requests)) {
            unavailable(choice, false);
            return;
        }
        clear();
        switch (choice) {
            case "3" -> publishSkill(requests);
            case "4" -> raiseRequest(requests);
            case "5" -> findMatches(requests);
        }
    }

    private void selectProposal() throws IOException {
        if (!requireMember("select a proposal")) return;
        if (!(service instanceof SelectionService selections)) {
            unavailable("6", false);
            return;
        }
        clear();
        while (running) {
            String rawRequestId = readLine("Request ID (or 0 Back): ");
            if (rawRequestId == null) return;
            String requestId = rawRequestId.strip();
            if (requestId.equals("0")) return;
            if (requestId.isEmpty()) {
                line("Enter a request ID, or 0 Back.");
                continue;
            }
            Result<List<ProposalView>> result = selections.proposalsForRequest(selected.id(),
                    new RequestId(requestId));
            if (result.isFailure()) {
                failure(result);
                return;
            }
            List<ProposalView> proposals = result.value().orElseThrow();
            if (proposals.isEmpty()) {
                line("No proposals found for this request.");
                pauseForBack();
                return;
            }
            line("Proposals for request " + requestId);
            for (int index = 0; index < proposals.size(); index++) {
                ProposalView proposal = proposals.get(index);
                line((index + 1) + " Proposal " + proposal.id().value() + " | Helper "
                        + proposal.helperId().value() + " | " + proposal.proposedOn() + " | " + proposal.status());
            }
            ProposalView proposal = choose(proposals, "Proposal number to select: ");
            if (proposal == null) continue;
            Result<EngagementView> selectedProposal = selections.selectProposal(selected.id(), proposal.id());
            if (selectedProposal.isFailure()) {
                failure(selectedProposal);
                return;
            }
            line("Proposal selected. Engagement ID: " + selectedProposal.value().orElseThrow().id().value());
            pauseForBack();
            return;
        }
    }

    private void confirmEngagement() throws IOException {
        if (!requireMember("confirm a session")) return;
        if (!(service instanceof ConfirmationService confirmations)) {
            unavailable("7", false);
            return;
        }
        clear();
        while (running) {
            String rawEngagementId = readLine("Engagement ID (or 0 Back): ");
            if (rawEngagementId == null) return;
            String engagementId = rawEngagementId.strip();
            if (engagementId.equals("0")) return;
            if (engagementId.isEmpty()) {
                line("Enter an engagement ID, or 0 Back.");
                continue;
            }
            Result<EngagementView> result = confirmations.confirmEngagement(selected.id(),
                    new EngagementId(engagementId));
            if (result.isFailure()) {
                failure(result);
                return;
            }
            EngagementStatus status = result.value().orElseThrow().status();
            if (status == EngagementStatus.AGREED) {
                line("Confirmation recorded. Waiting for the other participant.");
            } else if (status == EngagementStatus.COMPLETED) {
                line("Session completed and settled.");
            } else {
                line("Engagement status: " + status);
            }
            pauseForBack();
            return;
        }
    }

    private CategoryView chooseCategory(RequestService requests) throws IOException {
        List<CategoryView> categories = requests.categories();
        if (categories.isEmpty()) {
            line("No categories are available.");
            pauseForBack();
            return null;
        }
        for (int index = 0; index < categories.size(); index++) {
            CategoryView category = categories.get(index);
            line((index + 1) + " " + category.name() + " (" + category.id().value() + ")");
        }
        return choose(categories, "Category number: ");
    }

    private void publishSkill(RequestService requests) throws IOException {
        CategoryView category = chooseCategory(requests);
        if (category == null) return;
        Result<MemberView> result = requests.offerSkill(selected.id(), category.id());
        if (result.isFailure()) {
            failure(result);
            return;
        }
        line("Skill offer published: " + category.name());
        pauseForBack();
    }

    private void raiseRequest(RequestService requests) throws IOException {
        CategoryView category = chooseCategory(requests);
        while (category != null && running) {
            line("0 Back");
            Integer minutes = readNumber("Duration in minutes: ", 480);
            if (minutes != null && readRequestDates(requests, category, minutes)) return;
            if (running && minutes == null) {
                clear();
                category = chooseCategory(requests);
            }
        }
    }

    private boolean readRequestDates(RequestService requests, CategoryView category, int minutes) throws IOException {
        while (running) {
            LocalDate earliest = readDate("Earliest date (YYYY-MM-DD): ");
            if (earliest == null) return false;
            LocalDate latest = readDate("Latest date (YYYY-MM-DD): ");
            if (latest != null) {
                submitRequest(requests, category, minutes, earliest, latest);
                return true;
            }
        }
        return false;
    }

    private void submitRequest(RequestService requests, CategoryView category, int minutes,
            LocalDate earliest, LocalDate latest) throws IOException {
        Result<RequestView> result = requests.createRequest(selected.id(), category.id(), minutes, earliest, latest);
        if (result.isFailure()) {
            failure(result);
            return;
        }
        line("Request raised. Request ID: " + result.value().orElseThrow().id().value());
        pauseForBack();
    }

    private void findMatches(RequestService requests) throws IOException {
        while (running) {
            Result<List<RequestView>> result = requests.matchingRequests(selected.id());
            if (result.isFailure()) {
                failure(result);
                return;
            }
            List<RequestView> matches = result.value().orElseThrow();
            if (matches.isEmpty()) {
                line("No matching requests.");
                pauseForBack();
                return;
            }
            line("Matching requests");
            for (int index = 0; index < matches.size(); index++) {
                RequestView request = matches.get(index);
                line((index + 1) + " Request " + request.id().value() + " | " + request.categoryId().value()
                        + " | " + request.durationMinutes() + " minutes | " + request.windowStart()
                        + " to " + request.windowEnd());
            }
            RequestView match = choose(matches, "Request number to propose for: ");
            if (match == null) return;
            clear();
            line("Request " + match.id().value() + " | " + match.windowStart() + " to " + match.windowEnd());
            line("0 Back");
            LocalDate date = readDate("Proposed date (YYYY-MM-DD): ");
            if (date != null) {
                Result<ProposalView> proposal = requests.submitProposal(selected.id(), match.id(), date);
                if (proposal.isFailure()) {
                    failure(proposal);
                } else {
                    line("Proposal submitted. Proposal ID: " + proposal.value().orElseThrow().id().value());
                    pauseForBack();
                }
            }
            clear();
        }
    }

    private <T> T choose(List<T> choices, String prompt) throws IOException {
        line("0 Back");
        Integer number = readNumber(prompt, choices.size());
        return number == null ? null : choices.get(number - 1);
    }

    private Integer readNumber(String prompt, int maximum) throws IOException {
        while (running) {
            String value = readLine(prompt);
            if (value == null || value.trim().equals("0")) return null;
            try {
                int number = Integer.parseInt(value.trim());
                if (number >= 1 && number <= maximum) return number;
            } catch (NumberFormatException ignored) {
                // Print the valid range and ask for the same value again.
            }
            line("Enter a whole number from 1 to " + maximum + ", or 0 Back.");
        }
        return null;
    }

    private LocalDate readDate(String prompt) throws IOException {
        while (running) {
            String value = readLine(prompt);
            if (value == null || value.trim().equals("0")) return null;
            try {
                return LocalDate.parse(value.trim());
            } catch (DateTimeParseException ignored) {
                line("Enter a date in YYYY-MM-DD format, or 0 Back.");
            }
        }
        return null;
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
