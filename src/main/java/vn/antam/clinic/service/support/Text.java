package vn.antam.clinic.service.support;

import java.util.Locale;

public final class Text {

    private Text() {}

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String upperCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static String lower(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
