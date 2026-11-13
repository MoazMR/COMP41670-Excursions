package ie.ucd.meitheal.app;

import ie.ucd.meitheal.cli.RegistrationConsole;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestConsoleIntegrationTest {
    @Test
    void helperOffersSkillAndProposesAfterAnotherMemberRequestsHelp() throws IOException {
        String session = String.join("\n",
                "1", "Mae", "mae@example.org", "0",
                "1", "Pat", "pat@example.org", "0",
                "2", "2", "100001", "3", "1", "0",
                "2", "2", "100002", "4", "1", "45", "2026-09-02", "2026-09-04", "0",
                "5", "0",
                "2", "2", "100001", "5", "1", "2026-09-05", "0",
                "1", "2026-09-04", "0", "0", "0", "");
        var output = new StringWriter();
        new RegistrationConsole(new StringReader(session), output, InMemoryRegistrationService.create()).run();

        String text = output.toString();
        int last = -1;
        for (String expected : new String[]{"Skill offer published: Gardening", "Request raised. Request ID: req-1",
                "No matching requests.", "Matching requests", "req-1 | gardening | 45 minutes",
                "ERROR BUSINESS_REJECTION", "Proposal submitted. Proposal ID: prop-1"}) {
            int position = text.indexOf(expected, last + 1);
            assertTrue(position > last, () -> "Missing or out-of-order output: " + expected + "\n" + text);
            last = position;
        }
    }
}
