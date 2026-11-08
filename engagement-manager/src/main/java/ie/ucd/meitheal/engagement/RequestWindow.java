package ie.ucd.meitheal.engagement;

import java.time.LocalDate;
import java.util.Objects;

public record RequestWindow(LocalDate earliestDate, LocalDate latestDate) {

    public RequestWindow {
        earliestDate = Objects.requireNonNull(earliestDate, "earliestDate must not be null");
        latestDate = Objects.requireNonNull(latestDate, "latestDate must not be null");

        if (earliestDate.isAfter(latestDate)) {
            throw new IllegalArgumentException("earliestDate must not be after latestDate");
        }
    }

    public boolean contains(LocalDate date) {
        LocalDate nonNullDate = Objects.requireNonNull(date, "date must not be null");
        return !nonNullDate.isBefore(earliestDate) && !nonNullDate.isAfter(latestDate);
    }
}
