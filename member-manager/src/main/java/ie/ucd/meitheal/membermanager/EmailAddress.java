package ie.ucd.meitheal.membermanager;

import java.util.Locale;
import java.util.regex.Pattern;

public record EmailAddress(String value) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^(?=.{3,254}$)[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+"
                    + "(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
                    + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    public EmailAddress {
        if (value == null) {
            throw new IllegalArgumentException("Email address must not be null");
        }
        value = value.trim();
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Email address is invalid");
        }
    }

    public String canonicalValue() {
        return value.toLowerCase(Locale.ROOT);
    }
}
