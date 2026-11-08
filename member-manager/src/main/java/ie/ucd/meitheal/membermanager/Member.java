package ie.ucd.meitheal.membermanager;

import java.util.Objects;
import java.util.Set;

public record Member(
        MemberId id,
        String name,
        EmailAddress email,
        MemberStatus status,
        Set<CategoryId> offeredCategories) {

    public Member {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(status, "status");
        offeredCategories = Set.copyOf(Objects.requireNonNull(offeredCategories, "offeredCategories"));
    }

    Member withStatus(MemberStatus newStatus) {
        return new Member(id, name, email, newStatus, offeredCategories);
    }

    Member withOfferedCategory(CategoryId categoryId) {
        var updatedCategories = new java.util.HashSet<>(offeredCategories);
        updatedCategories.add(categoryId);
        return new Member(id, name, email, status, updatedCategories);
    }
}
