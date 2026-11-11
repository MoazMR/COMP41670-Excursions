package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommandParserCharacterisationTest {
    private final CommandParser parser = new CommandParser();

    @ParameterizedTest(name = "{0}")
    @MethodSource("observedSuccessfulInputs")
    void characterisesObservedSuccessfulInputs(
            String caseName, String input, String expectedName, List<String> expectedArguments) {
        ParsedCommand command = parser.parse(input);

        assertEquals(expectedName, command.name());
        assertEquals(expectedArguments, command.arguments());
    }

    private static Stream<Arguments> observedSuccessfulInputs() {
        return Stream.of(
                Arguments.of("plain command", "status", "status", List.of()),
                Arguments.of("leading and trailing whitespace", "  status  ", "status", List.of()),
                Arguments.of("multiple empty quoted arguments", "cmd \"\" \"\"", "cmd", List.of("", "")),
                Arguments.of("empty quoted command name", "\"\" arg", "", List.of("arg")),
                Arguments.of("adjacent quoted and unquoted segments", "\"ab\"cd", "abcd", List.of()),
                Arguments.of("adjacent quoted segments", "\"a\"\"b\"", "ab", List.of()),
                Arguments.of("spaces inside quotes", "cmd \"two words\"", "cmd", List.of("two words")),
                Arguments.of("tab-separated tokens", "cmd\tone\ttwo", "cmd", List.of("one", "two")),
                Arguments.of("actual newline as separator", "cmd\none", "cmd", List.of("one")),
                Arguments.of("EM SPACE as separator", "cmd\u2003one", "cmd", List.of("one")),
                Arguments.of("NO-BREAK SPACE retained", "cmd\u00a0one", "cmd\u00a0one", List.of()),
                Arguments.of("backslashes outside quotes", "cmd path\\to\\file",
                        "cmd", List.of("path\\to\\file")),
                Arguments.of("single quotes are retained", "cmd 'two words'",
                        "cmd", List.of("'two", "words'")),
                Arguments.of("escaped double quote", "cmd \"a\\\"b\"", "cmd", List.of("a\"b")),
                Arguments.of("escaped backslash", "cmd \"a\\\\b\"", "cmd", List.of("a\\b")),
                Arguments.of("trailing whitespace after quoted argument", "cmd \"value\"   ",
                        "cmd", List.of("value")),
                Arguments.of("adversarial case 1", "run", "run", List.of()),
                Arguments.of("adversarial case 2", "run \"a b\"", "run", List.of("a b")),
                Arguments.of("adversarial case 3", "run \"a\\\"b\"", "run", List.of("a\"b")),
                Arguments.of("adversarial case 4", "run \"a\\\\b\"", "run", List.of("a\\b")),
                Arguments.of("adversarial case 10", "run \"a\\\\\\\"\"tail",
                        "run", List.of("a\\\"tail")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("observedFailures")
    void characterisesObservedSyntaxFailures(String caseName, String input, String expectedMessage) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(input));

        assertEquals(expectedMessage, error.getMessage());
    }

    private static Stream<Arguments> observedFailures() {
        return Stream.of(
                Arguments.of("null input", null, "Command line must not be null."),
                Arguments.of("empty input", "", "Invalid command syntax: missing command name."),
                Arguments.of("whitespace-only input", "   \t  ",
                        "Invalid command syntax: missing command name."),
                Arguments.of("unsupported backslash-n escape", "cmd \"a\\nb\"",
                        "Invalid command syntax: invalid escape '\\n' in quoted argument."),
                Arguments.of("dangling escape", "cmd \"a\\",
                        "Invalid command syntax: dangling escape in quoted argument."),
                Arguments.of("unmatched double quote", "cmd \"a",
                        "Invalid command syntax: unmatched double quote."),
                Arguments.of("adversarial case 5", "run \"abc\\",
                        "Invalid command syntax: dangling escape in quoted argument."),
                Arguments.of("adversarial case 6", "run \"a\\q\"",
                        "Invalid command syntax: invalid escape '\\q' in quoted argument."),
                Arguments.of("adversarial case 7", "run \"a\\\"",
                        "Invalid command syntax: unmatched double quote."),
                Arguments.of("adversarial case 8", "run \"a\\\\",
                        "Invalid command syntax: unmatched double quote."),
                Arguments.of("adversarial case 9", "run \"a\\q",
                        "Invalid command syntax: invalid escape '\\q' in quoted argument."));
    }

    @Test
    void characterisesReuseOfSameParserAfterEveryObservedFailure() {
        for (Arguments failure : observedFailures().toList()) {
            String input = (String) failure.get()[1];
            String expectedMessage = (String) failure.get()[2];

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> parser.parse(input));
            assertEquals(expectedMessage, error.getMessage());

            ParsedCommand command = parser.parse("final ok");
            assertEquals("final", command.name());
            assertEquals(List.of("ok"), command.arguments());
        }
    }
}
