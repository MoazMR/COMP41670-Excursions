package ie.ucd.meitheal.shared.view;

import ie.ucd.meitheal.shared.id.EngagementId;
import ie.ucd.meitheal.shared.id.TransactionId;
import ie.ucd.meitheal.shared.model.TransactionType;
import java.time.LocalDate;
import java.util.Objects;

public record TransactionView(
        TransactionId id,
        TransactionType type,
        AccountReferenceView from,
        AccountReferenceView to,
        long amount,
        LocalDate date,
        EngagementId engagementId) {

    public TransactionView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(date, "date");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        boolean requiresEngagement = type == TransactionType.SERVICE_PAYMENT || type == TransactionType.LEVY;
        if (requiresEngagement != (engagementId != null)) {
            throw new IllegalArgumentException("engagement link does not match transaction type");
        }
    }
}
