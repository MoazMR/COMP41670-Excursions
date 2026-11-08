package ie.ucd.meitheal.engagement;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestWindowTest {

    @Test
    void rejectsReversedWindow() {
        LocalDate earliest = LocalDate.of(2026, 1, 10);
        LocalDate latest = LocalDate.of(2026, 1, 9);

        assertThrows(IllegalArgumentException.class, () -> new RequestWindow(earliest, latest));
    }

    @Test
    void acceptsSameDayWindowAndContainsThatDate() {
        LocalDate date = LocalDate.of(2026, 1, 10);

        RequestWindow window = new RequestWindow(date, date);

        assertTrue(window.contains(date));
    }

    @Test
    void preservesBothEndpoints() {
        LocalDate earliest = LocalDate.of(2026, 1, 10);
        LocalDate latest = LocalDate.of(2026, 1, 20);

        RequestWindow window = new RequestWindow(earliest, latest);

        assertEquals(earliest, window.earliestDate());
        assertEquals(latest, window.latestDate());
    }

    @Test
    void includesEarliestInteriorAndLatestDates() {
        LocalDate earliest = LocalDate.of(2026, 1, 10);
        LocalDate interior = LocalDate.of(2026, 1, 15);
        LocalDate latest = LocalDate.of(2026, 1, 20);

        RequestWindow window = new RequestWindow(earliest, latest);

        assertTrue(window.contains(earliest));
        assertTrue(window.contains(interior));
        assertTrue(window.contains(latest));
    }

    @Test
    void excludesDatesImmediatelyBeforeAndAfterWindow() {
        LocalDate earliest = LocalDate.of(2026, 1, 10);
        LocalDate latest = LocalDate.of(2026, 1, 20);

        RequestWindow window = new RequestWindow(earliest, latest);

        assertFalse(window.contains(earliest.minusDays(1)));
        assertFalse(window.contains(latest.plusDays(1)));
    }

    @Test
    void rejectsNullEarliestDate() {
        LocalDate latest = LocalDate.of(2026, 1, 20);

        assertThrows(NullPointerException.class, () -> new RequestWindow(null, latest));
    }

    @Test
    void rejectsNullLatestDate() {
        LocalDate earliest = LocalDate.of(2026, 1, 10);

        assertThrows(NullPointerException.class, () -> new RequestWindow(earliest, null));
    }

    @Test
    void rejectsNullContainsArgument() {
        RequestWindow window = new RequestWindow(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20));

        assertThrows(NullPointerException.class, () -> window.contains(null));
    }
}
