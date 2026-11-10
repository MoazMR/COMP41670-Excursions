package ie.ucd.meitheal.shared.id;

import java.util.Objects;

public record ProposalId(String value) {
    public ProposalId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("value must not be blank");
    }
}
