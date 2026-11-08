package ie.ucd.meitheal.membermanager;

import java.util.Objects;
import java.util.UUID;

public record MemberId(UUID value) {
    public MemberId {
        Objects.requireNonNull(value, "value");
    }

    public static MemberId create() {
        return new MemberId(UUID.randomUUID());
    }
}
