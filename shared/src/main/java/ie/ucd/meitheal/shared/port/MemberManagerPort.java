package ie.ucd.meitheal.shared.port;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.model.MemberStatus;
import java.util.Objects;
import java.util.Set;

/**
 * Membership owner for member records, canonical email lookup, offers, and reinstatement.
 * Service-coordinated writes across owners must preserve observable state on rejection or
 * uncommitted technical failure; the latter is reported as ATOMIC_OPERATION_FAILED and is
 * retryable.
 */
public interface MemberManagerPort {
    Result<MemberProfile> findById(MemberId memberId);

    /** Looks up an email after applying the membership owner's canonicalization rules. */
    Result<MemberProfile> findByCanonicalEmail(String canonicalEmail);

    Result<Boolean> isEligible(MemberId memberId);

    long memberCount();

    Result<MemberProfile> offer(MemberId memberId, CategoryId categoryId);

    Result<MemberProfile> reinstate(MemberId memberId);

    record MemberProfile(MemberId id, String name, String canonicalEmail, MemberStatus status,
            Set<CategoryId> categories) {
        public MemberProfile {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(canonicalEmail, "canonicalEmail");
            Objects.requireNonNull(status, "status");
            categories = Set.copyOf(categories);
        }
    }
}
