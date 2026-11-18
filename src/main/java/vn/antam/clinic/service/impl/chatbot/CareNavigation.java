package vn.antam.clinic.service.impl.chatbot;

import static vn.antam.clinic.service.support.ClinicJdbc.bad;

import java.time.*;
import java.util.regex.Pattern;
import vn.antam.clinic.service.support.chatbot.PublicLookupTools;

/** Routes to a place of care, never a diagnosis or treatment. */
final class CareNavigation {

    private CareNavigation() {}

    static boolean urgent(String text) {
        return Pattern.compile(
            "\\b(kho tho|dau nguc|bat tinh|ngat|non ra mau|oi ra mau|di ngoai ra mau|phan den|dau (?:bung|da day).{0,40}(?:du doi|dot ngot))\\b"
        )
            .matcher(text)
            .find();
    }

    static String date(String question, Clock clock) {
        String text = PublicLookupTools.normalize(question);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        LocalDate chosen = null;
        try {
            var iso = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b").matcher(text);
            var calendar = Pattern.compile(
                "(?<!\\d)(\\d{1,2})\\s*[/.-]\\s*(\\d{1,2})(?:\\s*[/.-]\\s*(\\d{4}))?(?!\\d)"
            ).matcher(text);
            if (iso.find()) chosen = LocalDate.parse(iso.group(1));
            else if (calendar.find()) chosen = LocalDate.of(
                calendar.group(3) == null ? today.getYear() : Integer.parseInt(calendar.group(3)),
                Integer.parseInt(calendar.group(2)),
                Integer.parseInt(calendar.group(1))
            );
            else if (text.contains("ngay mai")) chosen = today.plusDays(1);
            else if (text.contains("hom nay")) chosen = today;
        } catch (DateTimeException e) {
            throw bad("Ngày khám không hợp lệ. Bạn vui lòng nhập ngày/tháng/năm.");
        }
        if (
            chosen != null && (chosen.isBefore(today) || chosen.isAfter(today.plusDays(366)))
        ) throw bad("Bạn vui lòng chọn ngày khám từ hôm nay đến tối đa một năm tới.");
        return chosen == null ? null : chosen.toString();
    }
}
