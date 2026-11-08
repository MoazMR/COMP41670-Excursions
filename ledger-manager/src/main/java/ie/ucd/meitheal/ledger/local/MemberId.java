package ie.ucd.meitheal.ledger.local;

import java.util.Objects;

/**
 * Temporary ledger-local member identifier.
 *
 * <p>This type is an isolated lab boundary and is intended to be replaced by
 * the shared member contract when the feature is integrated.</p>
 */
public record MemberId(String value) {
    public MemberId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
