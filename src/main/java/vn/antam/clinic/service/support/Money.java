package vn.antam.clinic.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.web.rest.errors.ApiException;

public final class Money {

    private static final BigDecimal MAX = new BigDecimal("9999999999.99");

    private Money() {}

    public static BigDecimal parseVnd(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("Đơn giá không được để trống.");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalid("Đơn giá phải là số, ví dụ 150000.00.");
        }
        if (value.scale() > 2) {
            throw invalid("Đơn giá chỉ được có tối đa 2 chữ số thập phân.");
        }
        try {
            value = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid("Đơn giá chỉ được có tối đa 2 chữ số thập phân.");
        }
        if (value.signum() < 0 || value.compareTo(MAX) > 0) {
            throw invalid("Đơn giá phải từ 0 đến 9999999999.99.");
        }
        return value;
    }

    public static String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Đơn giá không hợp lệ", detail);
    }
}
