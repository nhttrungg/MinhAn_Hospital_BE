package vn.antam.clinic.service.impl.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.Passwords;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.dto.identity.PatientRegistrationDtos.RegisterRequest;
import vn.antam.clinic.service.identity.PatientRegistrationService;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
public class PatientRegistrationServiceImpl implements PatientRegistrationService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final Clock clock;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PatientRegistrationServiceImpl(
        JdbcTemplate jdbc,
        PasswordEncoder passwords,
        AuditService audit,
        Clock clock,
        @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.audit = audit;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9._]{3,50}")) throw bad(
            "Tên đăng nhập dùng chữ thường, số, dấu chấm hoặc gạch dưới."
        );
        Passwords.check(request.password());
        Integer existing = jdbc.queryForObject(
            "select count(*) from users where username=? or email=?",
            Integer.class,
            username,
            email
        );
        if (existing != null && existing > 0) throw conflict(
            "Tên đăng nhập hoặc email đã được dùng."
        );
        long userId = insert(
            "insert into users(username,password_hash,full_name,email,phone,user_type,status) " +
                "values(?,?,?,?,?,'PATIENT','PENDING_ACTIVATION')",
            username,
            passwords.encode(request.password()),
            request.fullName().trim(),
            email,
            request.phone()
        );
        jdbc.update(
            "insert into user_roles(user_id,role_id) select ?,id from roles where code='PATIENT'",
            userId
        );
        String provisional = "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long patientId = insert(
            "insert into patient_profiles(patient_code,user_id,full_name,phone,email) values(?,?,?,?,?)",
            provisional,
            userId,
            request.fullName().trim(),
            request.phone(),
            email
        );
        jdbc.update(
            "update patient_profiles set patient_code=? where id=?",
            "BN" + String.format("%08d", patientId),
            patientId
        );
        issueToken(userId, email, "ACTIVATE", "Kích hoạt tài khoản Minh An", "/activate?token=");
        audit.record("REGISTER", "USER", String.valueOf(userId), username);
    }

    @Override
    @Transactional
    public void activate(String token) {
        TokenRow row = findToken(token, "ACTIVATE");
        if (row.usedAt() != null) {
            Integer active = jdbc.queryForObject(
                "select count(*) from users where id=? and status='ACTIVE'",
                Integer.class,
                row.userId()
            );
            if (active != null && active == 1) return;
            throw bad("Liên kết không hợp lệ hoặc đã hết hạn.");
        }
        if (!row.expiresAt().isAfter(clock.instant())) throw bad(
            "Liên kết không hợp lệ hoặc đã hết hạn."
        );
        int changed = jdbc.update(
            "update users set status='ACTIVE' where id=? and status='PENDING_ACTIVATION'",
            row.userId()
        );
        if (changed != 1) throw conflict("Tài khoản đã kích hoạt hoặc bị khóa.");
        jdbc.update(
            "update account_tokens set used_at=? where id=?",
            Timestamp.from(clock.instant()),
            row.id()
        );
        audit.record("ACTIVATE", "USER", String.valueOf(row.userId()), null);
    }

    @Override
    @Transactional
    public void forgotPassword(String address) {
        String email = address.trim().toLowerCase(Locale.ROOT);
        List<Long> ids = jdbc.query(
            "select id from users where email=? and status='ACTIVE'",
            (rs, index) -> rs.getLong(1),
            email
        );
        if (!ids.isEmpty()) issueToken(
            ids.getFirst(),
            email,
            "RESET",
            "Đặt lại mật khẩu Minh An",
            "/reset-password?token="
        );
    }

    @Override
    @Transactional
    public void resetPassword(String token, String password) {
        Passwords.check(password);
        TokenRow row = token(token, "RESET");
        int changed = jdbc.update(
            "update users set password_hash=?,session_version=session_version+1 where id=? and status='ACTIVE'",
            passwords.encode(password),
            row.userId()
        );
        if (changed != 1) throw conflict("Tài khoản không còn hoạt động.");
        jdbc.update(
            "update account_tokens set used_at=? where id=?",
            Timestamp.from(clock.instant()),
            row.id()
        );
        audit.record("RESET_PASSWORD", "USER", String.valueOf(row.userId()), null);
    }

    private TokenRow token(String raw, String purpose) {
        TokenRow row = findToken(raw, purpose);
        if (row.usedAt() != null || !row.expiresAt().isAfter(clock.instant())) throw bad(
            "Liên kết không hợp lệ hoặc đã hết hạn."
        );
        return row;
    }

    private TokenRow findToken(String raw, String purpose) {
        List<TokenRow> rows = jdbc.query(
            "select id,user_id,expires_at,used_at from account_tokens " +
                "where token_hash=? and purpose=? for update",
            (rs, index) -> map(rs),
            digest(raw),
            purpose
        );
        if (rows.isEmpty()) throw bad("Liên kết không hợp lệ hoặc đã hết hạn.");
        return rows.getFirst();
    }

    private void issueToken(
        long userId,
        String email,
        String purpose,
        String subject,
        String route
    ) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update(
            "insert into account_tokens(user_id,token_hash,purpose,expires_at) values(?,?,?,?)",
            userId,
            digest(raw),
            purpose,
            Timestamp.from(clock.instant().plusSeconds(86400))
        );
        String link = frontendUrl + route + raw;
        jdbc.update(
            "insert into email_outbox(recipient,subject,body) values(?,?,?)",
            email,
            subject,
            "Bệnh viện Minh An\n\nMở liên kết sau trong 24 giờ:\n" +
                link +
                "\n\nNếu bạn không yêu cầu, hãy bỏ qua email này."
        );
    }

    private static TokenRow map(ResultSet rs) throws java.sql.SQLException {
        Timestamp used = rs.getTimestamp("used_at");
        return new TokenRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getTimestamp("expires_at").toInstant(),
            used == null ? null : used.toInstant()
        );
    }

    private long insert(String sql, Object... params) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
            );
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private static String digest(String raw) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ApiException bad(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ", detail);
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Không thực hiện được", detail);
    }

    private record TokenRow(long id, long userId, Instant expiresAt, Instant usedAt) {}
}
