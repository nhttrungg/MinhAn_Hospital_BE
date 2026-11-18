package vn.antam.clinic.service.support;

import java.util.Locale;

public final class SearchText {

    private SearchText() {}

    public static String like(String query) {
        String value = Text.blankToNull(query);
        if (value == null) {
            return null;
        }
        return value
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
