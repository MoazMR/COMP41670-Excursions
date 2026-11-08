package ie.ucd.meitheal.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

public final class MeithealCli {
    private final BufferedReader input;
    private final Writer output;
    private final CommandParser parser;

    public MeithealCli(Reader input, Writer output) {
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
        this.parser = new CommandParser();
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
                        writeArgumentCountError(command);
                    }
                } else if ("help".equals(command.name())) {
                    if (command.arguments().isEmpty()) {
                        writeHelp();
                    } else {
                        writeArgumentCountError(command);
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

    private void writeArgumentCountError(ParsedCommand command) throws IOException {
        writeError("Command '" + command.name() + "' expects 0 arguments but got "
                + command.arguments().size() + ".");
    }

    private void writeHelp() throws IOException {
        output.write("Supported commands:\n");
        output.write("  help - Show available commands.\n");
        output.write("  exit - Exit the CLI.\n");
        output.flush();
    }

    private void writeError(String message) throws IOException {
        output.write("ERROR " + message + "\n");
        output.flush();
    }
}
