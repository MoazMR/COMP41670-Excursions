package ie.ucd.meitheal.shared.id;

import java.util.Objects;

public record CategoryId(String value) {
    public CategoryId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("value must not be blank");
    }
}
