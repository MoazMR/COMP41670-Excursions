package ie.ucd.meitheal.membermanager;

public final class MemberManagerException extends RuntimeException {
    public enum Code {
        INVALID_NAME,
        INVALID_EMAIL,
        DUPLICATE_EMAIL,
        MEMBER_NOT_FOUND,
        MEMBER_SUSPENDED,
        INVALID_STATE,
        INVALID_CATEGORY,
        INVALID_REGISTRATION
    }

    private final Code code;

    public MemberManagerException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public MemberManagerException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
