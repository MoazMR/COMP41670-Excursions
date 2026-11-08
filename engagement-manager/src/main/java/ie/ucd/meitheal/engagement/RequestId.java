package ie.ucd.meitheal.engagement;

import java.util.Objects;
import java.util.UUID;

public record RequestId(UUID value) {
    public RequestId {
        value = Objects.requireNonNull(value, "value must not be null");
    }
}
