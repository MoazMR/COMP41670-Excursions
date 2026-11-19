package ie.ucd.meitheal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.ucd.meitheal.app.InMemoryRegistrationService;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SimulatedDateConsoleTest {
    @Test
    void signedOutUserCanReadAdvanceAndRepeatDateAndSeeBackwardRefusal() throws IOException {
        var service = InMemoryRegistrationService.create();
        String text = run(service, "13\n1\n2026-10-05\n0\n1\n2026-10-05\n0\n1\n2026-10-04\n0\n0\n0\n");
        assertTrue(text.contains("Current simulated date: 2026-09-01"));
        assertEquals(2, text.split("Simulated date set to 2026-10-05", -1).length - 1);
        assertTrue(text.contains("ERROR BUSINESS_REJECTION"));
        assertTrue(text.indexOf("Current simulated date: 2026-10-05", text.indexOf("ERROR BUSINESS_REJECTION")) > 0);
        assertFalse(text.contains("Login is required"));
        assertEquals(LocalDate.of(2026, 10, 5), service.currentDate());
    }

    @Test
    void malformedDateRepromptsAndBackReturnsOneLevelWithoutAdvancing() throws IOException {
        var service = InMemoryRegistrationService.create();
        String text = run(service, "13\n1\n2026-02-30\n0\n0\n12\n0\n0\n");
        assertTrue(text.contains("Enter a date in YYYY-MM-DD format, or 0 Back."));
        assertEquals(2, text.split("Current simulated date: 2026-09-01", -1).length - 1);
        assertTrue(text.contains("Community report"));
        assertFalse(text.contains("Simulated date set to"));
        assertEquals(LocalDate.of(2026, 9, 1), service.currentDate());
    }

    private String run(InMemoryRegistrationService service, String input) throws IOException {
        var output = new StringWriter();
        new RegistrationConsole(new StringReader(input), output, service).run();
        return output.toString();
    }
}
