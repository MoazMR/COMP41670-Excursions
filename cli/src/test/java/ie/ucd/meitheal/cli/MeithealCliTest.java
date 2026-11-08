package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class MeithealCliTest {
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
