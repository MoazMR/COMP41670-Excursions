package ie.ucd.meitheal.shared.port;

import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import java.time.LocalDate;

/** In-memory forward-only implementation of the simulated domain clock. */
public final class InMemorySimulatedClock implements SimulatedClock {
    private static final LocalDate MINIMUM_START_DATE = LocalDate.of(2026, 9, 1);

    private LocalDate currentDate;

    public InMemorySimulatedClock(LocalDate initialDate) {
        if (initialDate == null) {
            throw new IllegalArgumentException("initialDate must not be null");
        }
        if (initialDate.isBefore(MINIMUM_START_DATE)) {
            throw new IllegalArgumentException("initialDate must be on or after 2026-09-01");
        }
        this.currentDate = initialDate;
    }

    @Override
    public synchronized LocalDate currentDate() {
        return currentDate;
    }

    @Override
    public synchronized Result<LocalDate> advanceTo(LocalDate date) {
        if (date == null) {
            return Result.failure(FailureCode.INVALID_INPUT, "date must not be null");
        }
        if (date.isBefore(currentDate)) {
            return Result.failure(FailureCode.BUSINESS_REJECTION, "date must not be before currentDate");
        }
        currentDate = date;
        return Result.success(currentDate);
    }
}
