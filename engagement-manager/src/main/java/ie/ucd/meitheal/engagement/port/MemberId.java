package ie.ucd.meitheal.engagement.port;

import java.util.Objects;
import java.util.UUID;

public record MemberId(UUID value) {
    public MemberId {
        value = Objects.requireNonNull(value, "value must not be null");
    }
}
