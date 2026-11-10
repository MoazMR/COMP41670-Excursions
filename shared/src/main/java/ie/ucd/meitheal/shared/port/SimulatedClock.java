package ie.ucd.meitheal.shared.port;

import ie.ucd.meitheal.shared.result.Result;
import java.time.LocalDate;

/** Domain date source with a forward-only advance contract. */
public interface SimulatedClock {
    LocalDate currentDate();

    /** Same-day advances succeed unchanged; backward advances fail without changing the date. */
    Result<LocalDate> advanceTo(LocalDate date);
}
