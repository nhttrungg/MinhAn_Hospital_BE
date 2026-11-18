package vn.antam.clinic.service.support;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import vn.antam.clinic.security.SecurityUtils;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.web.rest.errors.ApiException;

public final class ClinicJdbc {

    private ClinicJdbc() {}

    /** JDBC maps MySQL DATETIME to LocalDateTime and TIMESTAMP to Timestamp. Both store UTC. */
    public static java.time.Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.LocalDateTime dateTime) return dateTime.toInstant(
            java.time.ZoneOffset.UTC
        );
        if (value instanceof java.time.Instant instant) return instant;
        throw new IllegalArgumentException("Unsupported database timestamp: " + value.getClass());
    }

    public static boolean role(String name) {
        return SecurityUtils.currentPrincipal()
            .getAuthorities()
            .stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + name));
    }

    public static void require(String... roles) {
        for (String name : roles) if (role(name)) return;
        throw forbidden();
    }

    public static long user() {
        return SecurityUtils.currentPrincipal().getId();
    }

    public static long staff(JdbcTemplate jdbc) {
        List<Long> ids = jdbc.query(
            "select id from staff where user_id=? and active=1",
            (rs, n) -> rs.getLong(1),
            user()
        );
        if (ids.isEmpty()) throw forbidden();
        return ids.getFirst();
    }

    public static long insert(JdbcTemplate jdbc, String sql, Object... params) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) s.setObject(i + 1, params[i]);
            return s;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    public static <T> PageResponse<T> page(List<T> rows, int page, int size) {
        validatePage(page, size);
        int start = (int) Math.min((long) page * size, rows.size());
        return new PageResponse<>(
            rows.subList(start, Math.min(start + size, rows.size())),
            page,
            size,
            rows.size(),
            (rows.size() + size - 1) / size
        );
    }

    public static <T> PageResponse<T> queryPage(
        JdbcTemplate jdbc,
        String sql,
        RowMapper<T> mapper,
        int page,
        int size,
        Object... args
    ) {
        validatePage(page, size);
        long total = jdbc.queryForObject(
            "select count(*) from (" + sql + ") page_count",
            Long.class,
            args
        );
        Object[] params = java.util.Arrays.copyOf(args, args.length + 2);
        params[args.length] = size;
        params[args.length + 1] = (long) page * size;
        return new PageResponse<>(
            jdbc.query(sql + " limit ? offset ?", mapper, params),
            page,
            size,
            total,
            (int) ((total + size - 1) / size)
        );
    }

    public record DateWindow(Timestamp from, Timestamp to) {}

    public static DateWindow dates(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(zone);
        if (from == null) from = today.minusDays(30);
        if (to == null) to = today.plusDays(30);
        if (from.isAfter(to) || to.isAfter(from.plusDays(366))) throw bad(
            "Chọn khoảng ngày tăng dần, tối đa 366 ngày."
        );
        return new DateWindow(
            Timestamp.from(from.atStartOfDay(zone).toInstant()),
            Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant())
        );
    }

    public static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw bad("Phân trang không hợp lệ (size 1–100).");
    }

    public static String like(String q) {
        if (q == null) return "%";
        if (q.length() > 200) throw bad("Tìm kiếm tối đa 200 ký tự.");
        return "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public static ApiException bad(String text) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", text);
    }

    public static ApiException conflict(String text) {
        return new ApiException(HttpStatus.CONFLICT, "Không thể cập nhật", text);
    }

    public static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy", "Bản ghi không tồn tại.");
    }

    public static ApiException forbidden() {
        return new ApiException(
            HttpStatus.FORBIDDEN,
            "Không có quyền",
            "Bạn không có quyền thao tác dữ liệu này."
        );
    }
}
