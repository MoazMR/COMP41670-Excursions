package ie.ucd.meitheal.engagement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestDurationTest {

    @Test
    void rejectsZeroMinutes() {
        assertThrows(IllegalArgumentException.class, () -> new RequestDuration(0));
    }

    @Test
    void rejectsValuesAboveMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new RequestDuration(481));
    }

    @Test
    void acceptsMinimumBoundaryAndPreservesValue() {
        RequestDuration requestDuration = new RequestDuration(1);

        assertEquals(1, requestDuration.minutes());
    }

    @Test
    void acceptsMaximumBoundaryAndPreservesValue() {
        RequestDuration requestDuration = new RequestDuration(480);

        assertEquals(480, requestDuration.minutes());
    }
}
