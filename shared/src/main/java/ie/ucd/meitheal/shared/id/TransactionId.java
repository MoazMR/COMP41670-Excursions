package ie.ucd.meitheal.shared.id;

import java.util.Objects;

public record TransactionId(String value) {
    public TransactionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("value must not be blank");
    }
}
