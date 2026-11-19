package ie.ucd.meitheal.app;

import ie.ucd.meitheal.shared.result.Result;
import java.time.LocalDate;

/** Read and advance the shared simulated date. */
public interface SimulatedDateService {
    LocalDate currentDate();

    /**
     * Changing the simulated date performs no business action. Same-day advance succeeds unchanged;
     * backward advance fails without changing the date.
     */
    Result<LocalDate> advanceDate(LocalDate date);
}
