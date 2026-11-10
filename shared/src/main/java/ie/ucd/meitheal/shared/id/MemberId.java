package ie.ucd.meitheal.shared.id;

import java.util.Objects;

public record MemberId(String value) {
    public MemberId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("value must not be blank");
    }
}
