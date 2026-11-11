package ie.ucd.meitheal.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;
import ie.ucd.meitheal.app.MeithealService;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.BalanceView;
import ie.ucd.meitheal.shared.view.MemberView;

public final class MeithealCli {
    private final BufferedReader input;
    private final Writer output;
    private final CommandParser parser;
    private final MeithealService service;

    public MeithealCli(Reader input, Writer output) {
        this(input, output, null);
    }

    public MeithealCli(Reader input, Writer output, MeithealService service) {
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
        this.parser = new CommandParser();
        this.service = service;
    }

    public void run() throws IOException {
        String line;
        while ((line = input.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }

            try {
                ParsedCommand command = parser.parse(line);
                if ("exit".equals(command.name())) {
                    if (command.arguments().isEmpty()) {
                        return;
                    } else {
                        writeArgumentCountError(command, 0);
                    }
                } else if ("help".equals(command.name())) {
                    if (command.arguments().isEmpty()) {
                        writeHelp();
                    } else {
                        writeArgumentCountError(command, 0);
                    }
                } else if (service != null && "register".equals(command.name())) {
                    if (command.arguments().size() != 2) {
                        writeArgumentCountError(command, 2);
                    } else {
                        renderMember(service.register(command.arguments().get(0),
                                command.arguments().get(1)));
                    }
                } else if (service != null && "balance".equals(command.name())) {
                    if (command.arguments().size() != 1) {
                        writeArgumentCountError(command, 1);
                    } else {
                        renderBalance(service.balance(new MemberId(command.arguments().get(0))));
                    }
                } else {
                    writeError("Unknown command '" + command.name()
                            + "'. Type 'help' for available commands.");
                }
            } catch (IllegalArgumentException exception) {
                writeError(exception.getMessage());
            }
        }
    }

    private void writeArgumentCountError(ParsedCommand command, int expected) throws IOException {
        writeError("Command '" + command.name() + "' expects " + expected
                + " arguments but got " + command.arguments().size() + ".");
    }

    private void renderMember(Result<MemberView> result) throws IOException {
        if (result.isFailure()) {
            writeFailure(result);
            return;
        }
        MemberView member = result.value().orElseThrow();
        output.write("Member ID: " + member.id().value() + "\n");
        output.write("Name: " + member.name() + "\n");
        output.write("Status: " + member.status() + "\n");
        output.write("Ledger credits: " + member.ledgerCredits() + "\n");
        output.flush();
    }

    private void renderBalance(Result<BalanceView> result) throws IOException {
        if (result.isFailure()) {
            writeFailure(result);
            return;
        }
        BalanceView balance = result.value().orElseThrow();
        output.write("Member ID: " + balance.memberId().value() + "\n");
        output.write("Ledger balance: " + balance.ledgerBalance() + "\n");
        output.write("Held credits: " + balance.held() + "\n");
        output.write("Available credits: " + balance.available() + "\n");
        output.flush();
    }

    private void writeFailure(Result<?> result) throws IOException {
        String message = "ERROR " + result.failureCode().orElseThrow();
        if (result.detail().isPresent()) {
            message += " - " + result.detail().orElseThrow();
        }
        output.write(message + "\n");
        output.flush();
    }

    private void writeHelp() throws IOException {
        output.write("Supported commands:\n");
        output.write("  help - Show available commands.\n");
        output.write("  exit - Exit the CLI.\n");
        if (service != null) {
            output.write("  register \"<name>\" <email> - Register a member.\n");
            output.write("  balance <member-id> - Show a member balance.\n");
        }
        output.flush();
    }

    private void writeError(String message) throws IOException {
        output.write("ERROR " + message + "\n");
        output.flush();
    }
}
