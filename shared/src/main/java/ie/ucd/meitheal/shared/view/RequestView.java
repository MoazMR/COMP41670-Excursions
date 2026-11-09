package ie.ucd.meitheal.shared.view;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.id.RequestId;
import ie.ucd.meitheal.shared.model.RequestStatus;
import java.time.LocalDate;
import java.util.Objects;

public record RequestView(
        RequestId id,
        MemberId requesterId,
        CategoryId categoryId,
        int durationMinutes,
        LocalDate windowStart,
        LocalDate windowEnd,
        RequestStatus status) {

    public RequestView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(windowStart, "windowStart");
        Objects.requireNonNull(windowEnd, "windowEnd");
        Objects.requireNonNull(status, "status");
        if (durationMinutes < 1 || durationMinutes > 480) {
            throw new IllegalArgumentException("duration must be 1..480 minutes");
        }
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException("window end must not precede window start");
        }
    }
}
