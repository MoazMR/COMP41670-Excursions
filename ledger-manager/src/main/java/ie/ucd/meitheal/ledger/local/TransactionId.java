package ie.ucd.meitheal.ledger.local;

import java.util.Objects;

/**
 * Temporary ledger-local transaction identifier.
 *
 * <p>This type is an isolated lab boundary and is intended to be replaced by
 * the shared transaction contract when the feature is integrated.</p>
 */
public record TransactionId(String value) {
    public TransactionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
