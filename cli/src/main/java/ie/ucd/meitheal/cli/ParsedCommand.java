package ie.ucd.meitheal.cli;

import java.util.List;
import java.util.Objects;

/**
 * The syntax-only result of parsing one command line.
 */
public record ParsedCommand(String name, List<String> arguments) {
    public ParsedCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arguments, "arguments");
        arguments = List.copyOf(arguments);
    }
}
