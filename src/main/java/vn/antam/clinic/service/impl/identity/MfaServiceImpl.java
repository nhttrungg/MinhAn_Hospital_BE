package vn.antam.clinic.service.impl.identity;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.integration.ai.AiSecretCipher;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.identity.MfaService;
import vn.antam.clinic.service.identity.TotpService;

@Service
public class MfaServiceImpl implements MfaService {

    private final JdbcTemplate jdbc;
    private final TotpService totp;
    private final Clock clock;
    private final PasswordEncoder passwords;
    private final AiSecretCipher cipher;
    private final AuditService audit;

    public MfaServiceImpl(
        JdbcTemplate jdbc,
        TotpService totp,
        Clock clock,
        PasswordEncoder passwords,
        AuditService audit,
        @Value("${app.security.mfa-master-key:}") String key
    ) {
        this.jdbc = jdbc;
        this.totp = totp;
        this.clock = clock;
        this.passwords = passwords;
        this.audit = audit;
        cipher = new AiSecretCipher(key);
    }

    @Override
    public boolean enabled(long id) {
        return (
            jdbc.queryForObject("select totp_enabled from users where id=?", Integer.class, id) == 1
        );
    }

    @Override
    public void password(long id, String password) {
        String hash = jdbc.queryForObject(
            "select password_hash from users where id=?",
            String.class,
            id
        );
        if (password == null || !passwords.matches(password, hash)) throw bad(
            "Mật khẩu hiện tại không đúng."
        );
    }

    @Override
    public byte[] encrypt(String secret) {
        if (!cipher.ready()) throw conflict("Cần cấu hình MFA_MASTER_KEY trước khi bật 2FA.");
        return cipher.encrypt(secret);
    }

    @Override
    @Transactional
    public List<String> enable(long id, byte[] secret, String code) {
        jdbc.queryForList("select id from users where id=? for update", id);
        if (enabled(id)) throw conflict("2FA đã bật.");
        long step = totp.matchingStep(
            cipher.decrypt(secret),
            code,
            clock.instant().getEpochSecond()
        );
        if (step < 0) throw bad("Mã xác thực không đúng hoặc đã hết hạn.");
        List<String> codes = totp.recoveryCodes();
        jdbc.update(
            "update users set totp_secret=?,totp_enabled=1,totp_last_step=?,session_version=session_version+1 where id=?",
            secret,
            step,
            id
        );
        jdbc.update("delete from user_recovery_codes where user_id=?", id);
        for (String recovery : codes)
            jdbc.update(
                "insert into user_recovery_codes(user_id,code_hash) values(?,?)",
                id,
                TotpServiceImpl.hash(recovery)
            );
        audit.record("MFA_ENABLE", "USER", String.valueOf(id), null);
        return codes;
    }

    @Override
    @Transactional
    public boolean verify(long id, long version, String code) {
        var rows = jdbc.queryForList("select * from users where id=? for update", id);
        if (rows.isEmpty()) return false;
        var row = rows.getFirst();
        var locked = vn.antam.clinic.service.support.ClinicJdbc.instant(row.get("locked_until"));
        if (
            !"ACTIVE".equals(row.get("status")) ||
            ((Number) row.get("session_version")).longValue() != version ||
            ((Number) row.get("totp_enabled")).intValue() != 1 ||
            (locked != null && locked.isAfter(clock.instant()))
        ) return false;
        if (code != null && code.matches("[0-9a-fA-F]{20}")) return (
            jdbc.update(
                "update user_recovery_codes set used_at=? where user_id=? and code_hash=? and used_at is null",
                Timestamp.from(clock.instant()),
                id,
                TotpServiceImpl.hash(code.toLowerCase(Locale.ROOT))
            ) == 1
        );
        long step = totp.matchingStep(
            cipher.decrypt((byte[]) row.get("totp_secret")),
            code,
            clock.instant().getEpochSecond()
        );
        if (step < 0 || step <= ((Number) row.get("totp_last_step")).longValue()) return false;
        jdbc.update("update users set totp_last_step=? where id=?", step, id);
        return true;
    }

    @Override
    @Transactional
    public void reset(long id) {
        if (
            jdbc.update(
                "update users set totp_enabled=0,totp_secret=null,totp_last_step=-1,session_version=session_version+1 where id=?",
                id
            ) == 0
        ) throw missing();
        jdbc.update("delete from user_recovery_codes where user_id=?", id);
        audit.record("MFA_RESET", "USER", String.valueOf(id), null);
    }

    @Override
    @Transactional
    public void unlock(long id) {
        if (
            jdbc.update("update users set failed_login_count=0,locked_until=null where id=?", id) ==
            0
        ) throw missing();
        audit.record("LOGIN_UNLOCK", "USER", String.valueOf(id), null);
    }
}
