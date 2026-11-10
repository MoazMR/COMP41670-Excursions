package ie.ucd.meitheal.shared.view;

import ie.ucd.meitheal.shared.id.MemberId;
import java.time.LocalDate;
import java.util.Objects;

public record CommunityGrantView(MemberId recipient, long amount, LocalDate approvedOn) {
    public CommunityGrantView {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(approvedOn, "approvedOn");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
