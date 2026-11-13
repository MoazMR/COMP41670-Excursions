package ie.ucd.meitheal.engagement.port;

public interface MemberReadAccess {
    boolean exists(MemberId id);

    boolean isActive(MemberId id);
}
