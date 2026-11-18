package vn.antam.clinic.service.support;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.util.*;

/** Stateless formatting and validation shared by HTTP and business layers. */
public final class QueueDisplayText {

    private QueueDisplayText() {}

    public static Set<Long> parseRooms(String input) {
        if (input == null || input.isBlank()) return Set.of();
        if (input.length() > 300) throw bad("Chọn tối đa 20 phòng.");
        Set<Long> ids = new TreeSet<>();
        for (String part : input.split(",", -1)) {
            try {
                long id = Long.parseLong(part.trim());
                if (id <= 0) throw new NumberFormatException();
                ids.add(id);
            } catch (NumberFormatException error) {
                throw bad("Mã phòng không hợp lệ.");
            }
        }
        if (ids.size() > 20) throw bad("Chọn tối đa 20 phòng.");
        return Set.copyOf(ids);
    }

    public static String maskName(String name) {
        if (name == null || name.isBlank()) return "Khách khám";
        String[] words = name.trim().split("\\s+");
        if (words.length == 1) return (
            words[0].substring(0, Character.charCount(words[0].codePointAt(0))) + "."
        );
        StringBuilder masked = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) masked
            .append(' ')
            .appendCodePoint(words[i].codePointAt(0))
            .append('.');
        return masked.toString();
    }
}
