package vn.antam.clinic.service.support;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.PatientAccess;

@Component
public class WorkflowSupport {

    private final JdbcTemplate db;
    private final PatientAccess access;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public WorkflowSupport(JdbcTemplate db, PatientAccess access) {
        this.db = db;
        this.access = access;
    }

    public JdbcTemplate db() {
        return db;
    }

    public ObjectMapper json() {
        return json;
    }

    public Map<String, Object> row(String sql, Object... args) {
        var rows = db.queryForList(sql, args);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    public long id(String sql, Object... args) {
        return insert(db, sql, args);
    }

    public static long number(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            throw bad("Thiếu hoặc sai mã: " + key);
        }
    }

    public static int revision(Map<String, Object> row) {
        return Math.toIntExact(number(row, "revision"));
    }

    public static String text(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    public static String required(Map<String, Object> row, String key, int max) {
        String s = text(row, key).trim();
        if (s.isBlank() || s.length() > max) throw bad(
            "Trường " + key + " bắt buộc, tối đa " + max + " ký tự."
        );
        return s;
    }

    public static BigDecimal decimal(Map<String, Object> row, String key) {
        try {
            BigDecimal n = new BigDecimal(text(row, key));
            if (
                n.scale() > 2 || n.abs().compareTo(new BigDecimal("99999999.99")) > 0
            ) throw new Exception();
            return n;
        } catch (Exception e) {
            throw bad("Số lượng / tiền không hợp lệ: " + key);
        }
    }

    public static boolean yes(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() == 1);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> lines(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof List<?> list) || list.size() > 100) throw bad(
            "Danh sách " + key + " không hợp lệ."
        );
        for (Object item : list)
            if (!(item instanceof Map<?, ?>)) throw bad("Dòng dữ liệu không hợp lệ.");
        return (List<Map<String, Object>>) value;
    }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> decode(String value) {
        try {
            return json.readValue(value, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void event(String type, long id, String action, Long counter, Object detail) {
        db.update(
            "insert into workflow_events(entity_type,entity_id,action,actor_user_id,counter_id,details) values(?,?,?,?,?,?)",
            type,
            id,
            action,
            user(),
            counter,
            encode(detail)
        );
    }

    public void expect(Map<String, Object> saved, Map<String, Object> request) {
        if (revision(saved) != revision(request)) throw conflict(
            "Dữ liệu đã thay đổi. Tải lại trước khi tiếp tục."
        );
    }

    public void counter(long id, String role) {
        require(role);
        row("select id from pharmacy_counters where id=? and active=1", id);
        if (
            db.queryForObject(
                "select count(*) from counter_staff cs join staff s on s.id=cs.staff_id where cs.counter_id=? and s.user_id=? and s.active=1",
                Integer.class,
                id,
                user()
            ) == 0
        ) throw forbidden();
    }

    public void counterRead(long id) {
        if (role("ADMIN")) {
            row("select id from pharmacy_counters where id=?", id);
            return;
        }
        require("PHARMACIST", "CASHIER");
        counter(id, role("PHARMACIST") ? "PHARMACIST" : "CASHIER");
    }

    public void diagnostic(long room, boolean review) {
        require("LAB_TECH");
        if (
            db.queryForObject(
                "select count(*) from diagnostic_staff ds join staff s on s.id=ds.staff_id where ds.room_id=? and s.user_id=? and s.active=1 and (?=0 or ds.can_review=1)",
                Integer.class,
                room,
                user(),
                review ? 1 : 0
            ) == 0
        ) throw forbidden();
    }

    public void patientEncounter(long id) {
        require("PATIENT");
        if (
            db.queryForObject(
                "select count(*) from encounters e join patient_profiles p on p.id=e.patient_id where e.id=? and " +
                    PatientAccess.ownedSql("p"),
                Integer.class,
                id,
                user(),
                user()
            ) == 0
        ) throw forbidden();
    }

    public Map<String, Object> encounter(long id, boolean doctorWrite) {
        var e = row(
            "select e.*,p.user_id patient_user from encounters e join patient_profiles p on p.id=e.patient_id where e.id=?",
            id
        );
        if (role("DOCTOR") && number(e, "doctor_staff_id") == staff(db)) return e;
        if (
            !doctorWrite &&
            role("PATIENT") &&
            access.owns(user(), number(e, "patient_id")) &&
            text(e, "status").equals("COMPLETED")
        ) return e;
        throw forbidden();
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> once(
        String operation,
        String key,
        Map<String, Object> input,
        Supplier<Map<String, Object>> action
    ) {
        if (key == null || key.isBlank() || key.length() > 100) throw bad(
            "Cần Idempotency-Key hợp lệ."
        );
        row("select id from users where id=? for update", user());
        String hash;
        try {
            hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    encode(new TreeMap<>(input)).getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        var previous = db.queryForList(
            "select request_hash,response from workflow_requests where actor_user_id=? and operation=? and request_key=?",
            user(),
            operation,
            key
        );
        if (!previous.isEmpty()) {
            var p = previous.getFirst();
            if (!text(p, "request_hash").equals(hash)) throw conflict(
                "Mã chống lặp đã dùng cho yêu cầu khác."
            );
            return decode(text(p, "response"));
        }
        var response = action.get();
        db.update(
            "insert into workflow_requests(actor_user_id,operation,request_key,request_hash,response) values(?,?,?,?,?)",
            user(),
            operation,
            key,
            hash,
            encode(response)
        );
        return response;
    }
}
