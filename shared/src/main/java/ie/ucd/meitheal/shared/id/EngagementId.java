package ie.ucd.meitheal.shared.id;

import java.util.Objects;

public record EngagementId(String value) {
    public EngagementId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("value must not be blank");
    }
}
