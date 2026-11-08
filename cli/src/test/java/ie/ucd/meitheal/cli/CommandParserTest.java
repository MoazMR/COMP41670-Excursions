package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandParserTest {
    private final CommandParser parser = new CommandParser();

    @Test
    void parsesWhitespaceAndPreservesArgumentContent() {
        ParsedCommand command = parser.parse("  register   \"Rita Requester\"  Rita@Example.COM  ");

        assertEquals("register", command.name());
        assertEquals(List.of("Rita Requester", "Rita@Example.COM"), command.arguments());
    }

    @Test
    void parsesEmptyAndEscapedQuotedArguments() {
        ParsedCommand command = parser.parse("command \"\" \"say \\\"hi\\\" and \\\\path\"");

        assertEquals(List.of("", "say \"hi\" and \\path"), command.arguments());
    }

    @Test
    void parsedArgumentsCannotBeMutated() {
        ParsedCommand command = parser.parse("help");

        assertThrows(UnsupportedOperationException.class,
                () -> command.arguments().add("unexpected"));
    }

    @Test
    void rejectsUnmatchedQuotes() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("register \"unfinished"));

        assertEquals("Invalid command syntax: unmatched double quote.", error.getMessage());
    }

    @Test
    void rejectsInvalidAndDanglingEscapes() {
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("command \"bad\\n\""));
        IllegalArgumentException dangling = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("command \"bad\\"));

        assertEquals("Invalid command syntax: invalid escape '\\n' in quoted argument.",
                invalid.getMessage());
        assertEquals("Invalid command syntax: dangling escape in quoted argument.",
                dangling.getMessage());
    }
}
