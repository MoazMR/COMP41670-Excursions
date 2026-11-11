package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.ucd.meitheal.app.InMemoryRegistrationService;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class RegistrationConsoleTest {
    @Test
    void escapeAtMainReturnsToMenuAndDoesNotExit() throws Exception {
        StringWriter output = new StringWriter();
        new RegistrationConsole(new StringReader("11\n\u001b0\n"), output, InMemoryRegistrationService.create()).run();

        assertTrue(output.toString().contains("Login is required to view balance and history"));
        assertTrue(output.toString().split(" 1  Register", -1).length >= 3);
    }

    @Test
    void eofAtLoginMethodsTerminatesInsteadOfRetrying() throws Exception {
        StringWriter output = new StringWriter();
        new RegistrationConsole(new StringReader("2\n"), output, InMemoryRegistrationService.create()).run();

        assertTrue(output.toString().contains("1 Email address"));
        assertFalse(output.toString().contains("Name: "));
    }

    @Test
    void escapeFromEmailReturnsToNamePromptThenRegisters() throws Exception {
        StringWriter output = new StringWriter();
        String script = "1\nOld name\npartial@\u001bNew name\nnew@example.org\n0\n0\n";
        new RegistrationConsole(new StringReader(script), output, InMemoryRegistrationService.create()).run();

        assertTrue(output.toString().contains("Name: Email: Name: Email:"));
        assertTrue(output.toString().contains("Name: New name   Email: new@example.org"));
    }

    @Test
    void registrationAndMissedLookupsPreserveSelectedMember() throws Exception {
        StringWriter output = new StringWriter();
        String script = "1\nAlex\nalex@example.org\n0\n2\n2\n100001\n"
                + "1\nRobin\nrobin@example.org\n0\n"
                + "2\n2\n999999\n1\nmissing@example.org\n0\n"
                + "11\n1\n0\n0\n0\n";
        new RegistrationConsole(new StringReader(script), output, InMemoryRegistrationService.create()).run();

        String text = output.toString();
        assertTrue(text.contains("Member ID: 100002"));
        assertTrue(text.contains("No matching member ID found."));
        assertTrue(text.contains("No matching email address found."));
        int lookupMiss = text.indexOf("No matching email address found.");
        assertTrue(text.indexOf("Alex | alex@example.org | 100001", lookupMiss) > lookupMiss);
        assertFalse(text.contains("Robin | robin@example.org | 100002"));
        assertFalse(text.contains("\u001b[2J"));
    }
}
