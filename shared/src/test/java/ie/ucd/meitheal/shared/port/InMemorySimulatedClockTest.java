package ie.ucd.meitheal.shared.port;

import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySimulatedClockTest {
    private static final LocalDate FLOOR = LocalDate.of(2026, 9, 1);

    @Test
    void constructorSetsInitialDateAndRejectsNullOrDatesBeforeFloor() {
        assertEquals(FLOOR, new InMemorySimulatedClock(FLOOR).currentDate());
        assertEquals(FLOOR.plusDays(1), new InMemorySimulatedClock(FLOOR.plusDays(1)).currentDate());
        assertThrows(IllegalArgumentException.class, () -> new InMemorySimulatedClock(null));
        assertThrows(IllegalArgumentException.class,
                () -> new InMemorySimulatedClock(FLOOR.minusDays(1)));
    }

    @Test
    void forwardAndSameDayAdvancesSucceed() {
        InMemorySimulatedClock clock = new InMemorySimulatedClock(FLOOR);
        LocalDate later = FLOOR.plusDays(4);

        Result<LocalDate> forward = clock.advanceTo(later);
        assertTrue(forward.isSuccess());
        assertEquals(later, forward.value().orElseThrow());
        assertEquals(later, clock.currentDate());

        Result<LocalDate> sameDay = clock.advanceTo(later);
        assertTrue(sameDay.isSuccess());
        assertEquals(later, sameDay.value().orElseThrow());
        assertEquals(later, clock.currentDate());
    }

    @Test
    void nullAndBackwardAdvancesAreRejectedWithoutChangingDate() {
        InMemorySimulatedClock clock = new InMemorySimulatedClock(FLOOR);
        LocalDate advanced = FLOOR.plusDays(3);
        assertTrue(clock.advanceTo(advanced).isSuccess());

        Result<LocalDate> nullResult = clock.advanceTo(null);
        assertTrue(nullResult.isFailure());
        assertEquals(FailureCode.INVALID_INPUT, nullResult.failureCode().orElseThrow());
        assertEquals(advanced, clock.currentDate());

        Result<LocalDate> backward = clock.advanceTo(FLOOR.plusDays(2));
        assertTrue(backward.isFailure());
        assertEquals(FailureCode.BUSINESS_REJECTION, backward.failureCode().orElseThrow());
        assertEquals(advanced, clock.currentDate());
    }
}
