package ie.ucd.meitheal.engagement;

public record RequestDuration(int minutes) {

    private static final int MIN_MINUTES = 1;
    private static final int MAX_MINUTES = 480;

    public RequestDuration {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException(
                "Request duration must be between " + MIN_MINUTES + " and " + MAX_MINUTES + " minutes inclusive"
            );
        }
    }
}
