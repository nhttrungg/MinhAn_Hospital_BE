package vn.antam.clinic.security;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Shared boundary for an active personal profile or an explicitly owned family profile. */
@Component
public class PatientAccess {

    private final JdbcTemplate jdbc;

    public PatientAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean owns(long userId, long patientId) {
        return (
            jdbc.queryForObject(
                "select count(*) from patient_profiles p where p.id=? and " + ownedSql("p"),
                Integer.class,
                patientId,
                userId,
                userId
            ) > 0
        );
    }

    public void requireOwn(long userId, long patientId) {
        if (!owns(userId, patientId)) throw forbidden();
    }

    public List<Long> ownedPatientIds(long userId) {
        return jdbc.query(
            "select p.id from patient_profiles p where " + ownedSql("p") + " order by p.id",
            (r, n) -> r.getLong(1),
            userId,
            userId
        );
    }

    public long primaryPatientId(long userId) {
        var ids = jdbc.query(
            "select id from patient_profiles where user_id=? and active=1",
            (r, n) -> r.getLong(1),
            userId
        );
        if (ids.isEmpty()) throw missing();
        return ids.getFirst();
    }

    public Long recipientUserId(long patientId) {
        var ids = jdbc.query(
            "select coalesce(owner_user_id,user_id) from patient_profiles where id=? and active=1",
            (r, n) -> (Long) r.getObject(1),
            patientId
        );
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /** Two bound parameters, both the account ID. Alias must be a source-code constant. */
    public static String ownedSql(String alias) {
        if (!alias.matches("[a-zA-Z][a-zA-Z0-9_]*")) throw new IllegalArgumentException(
            "Invalid SQL alias"
        );
        return alias + ".active=1 and (" + alias + ".user_id=? or " + alias + ".owner_user_id=?)";
    }
}
