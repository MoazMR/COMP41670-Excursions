package ie.ucd.meitheal.engagement.port;

import java.util.Objects;

public record CategoryId(String value) {
    public CategoryId {
        value = Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
