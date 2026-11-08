package ie.ucd.meitheal.membermanager;

import java.util.Objects;
import java.util.UUID;

public final class PreparedRegistration {
    private final UUID token;
    private final Member member;

    PreparedRegistration(UUID token, Member member) {
        this.token = Objects.requireNonNull(token, "token");
        this.member = Objects.requireNonNull(member, "member");
    }

    UUID token() {
        return token;
    }

    public Member member() {
        return member;
    }

    public MemberId memberId() {
        return member.id();
    }
}
