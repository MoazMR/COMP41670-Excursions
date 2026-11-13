package ie.ucd.meitheal.engagement;

import ie.ucd.meitheal.engagement.port.CategoryId;
import ie.ucd.meitheal.engagement.port.MemberId;

import java.util.Objects;

public record Request(
    RequestId id,
    MemberId requesterId,
    CategoryId categoryId,
    RequestDuration duration,
    RequestWindow window,
    RequestStatus status
) {
    public Request {
        id = Objects.requireNonNull(id, "id must not be null");
        requesterId = Objects.requireNonNull(requesterId, "requesterId must not be null");
        categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
        duration = Objects.requireNonNull(duration, "duration must not be null");
        window = Objects.requireNonNull(window, "window must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
    }
}
