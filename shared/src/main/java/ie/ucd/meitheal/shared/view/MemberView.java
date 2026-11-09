package ie.ucd.meitheal.shared.view;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.id.MemberId;
import ie.ucd.meitheal.shared.model.MemberStatus;
import java.util.Objects;
import java.util.Set;

public record MemberView(
        MemberId id,
        String name,
        String email,
        MemberStatus status,
        Set<CategoryId> skills,
        long ledgerCredits) {

    public MemberView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(status, "status");
        skills = Set.copyOf(skills);
    }
}
