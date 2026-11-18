package vn.antam.clinic.service.impl.booking;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.ClinicUserPrincipal;
import vn.antam.clinic.security.PatientAccess;
import vn.antam.clinic.security.SecurityUtils;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.booking.PatientService;
import vn.antam.clinic.service.dto.booking.FamilyDtos.*;
import vn.antam.clinic.service.dto.booking.PatientDtos.PatientInput;
import vn.antam.clinic.service.dto.booking.PatientDtos.PatientView;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
public class PatientServiceImpl implements PatientService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final PatientAccess access;

    public PatientServiceImpl(JdbcTemplate jdbc, AuditService audit, PatientAccess access) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.access = access;
    }

    @Override
    @Transactional(readOnly = true)
    public PatientView me() {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "PATIENT")) throw forbidden();
        return find(access.primaryPatientId(actor.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientView> search(String query) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "RECEPTIONIST") && !role(actor, "ADMIN")) throw forbidden();
        String q = query == null ? "" : query.trim();
        if (q.length() > 100) throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "Tìm kiếm quá dài",
            "Tối đa 100 ký tự."
        );
        return jdbc.query(
            "select * from patient_profiles where active=1 and " +
                "(?='' or patient_code like ? or full_name like ? or phone like ?) " +
                "order by id desc limit 50",
            (rs, row) -> map(rs),
            q,
            like(q),
            like(q),
            like(q)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PatientView get(long id) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (role(actor, "RECEPTIONIST") || role(actor, "ADMIN")) return find(id);
        if (role(actor, "PATIENT") && access.owns(actor.getId(), id)) return find(id);
        throw forbidden();
    }

    @Override
    @Transactional
    public PatientView create(PatientInput input) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "RECEPTIONIST")) throw forbidden();
        validate(input);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        String temp = "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "insert into patient_profiles " +
                    "(patient_code,full_name,phone,email,date_of_birth,gender,address,allergies,medical_history) " +
                    "values(?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, temp);
            fill(statement, 2, input);
            return statement;
        }, key);
        long id = Objects.requireNonNull(key.getKey()).longValue();
        jdbc.update(
            "update patient_profiles set patient_code=? where id=?",
            "BN" + String.format("%08d", id),
            id
        );
        audit.record("CREATE", "PATIENT", String.valueOf(id), null);
        return find(id);
    }

    @Override
    @Transactional
    public PatientView update(long id, PatientInput input) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (
            !role(actor, "RECEPTIONIST") &&
            (!role(actor, "PATIENT") || !access.owns(actor.getId(), id))
        ) throw forbidden();
        validate(input);
        int updated = jdbc.update(
            "update patient_profiles set full_name=?,phone=?,email=?,date_of_birth=?," +
                "gender=?,address=?,allergies=?,medical_history=? where id=? and active=1",
            input.fullName().trim(),
            input.phone(),
            input.email(),
            date(input),
            input.gender(),
            input.address(),
            input.allergies(),
            input.medicalHistory(),
            id
        );
        if (updated == 0) throw new ApiException(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy",
            "Hồ sơ bệnh nhân không tồn tại."
        );
        audit.record("UPDATE", "PATIENT", String.valueOf(id), null);
        return find(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FamilyView> family() {
        require("PATIENT");
        return jdbc.query(
            "select id,relationship from patient_profiles where owner_user_id=? and user_id is null and active=1 order by id",
            (r, n) -> FamilyView.from(find(r.getLong("id")), r.getString("relationship")),
            user()
        );
    }

    @Override
    @Transactional
    public FamilyView addFamily(FamilyInput input) {
        require("PATIENT");
        lockOwner();
        validate(input.patient());
        String relation = relationship(input.relationship());
        if (
            jdbc.queryForObject(
                "select count(*) from patient_profiles where owner_user_id=? and active=1",
                Integer.class,
                user()
            ) >= 5
        ) throw conflict("Mỗi tài khoản quản lý tối đa 5 người thân.");
        long id = insert(
            jdbc,
            "insert into patient_profiles(patient_code,owner_user_id,relationship,full_name,phone,email,date_of_birth,gender,address,allergies,medical_history) values(?,?,?,?,?,?,?,?,?,?,?)",
            "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
            user(),
            relation,
            input.fullName().trim(),
            input.phone(),
            input.email(),
            input.dateOfBirth() == null ? null : Date.valueOf(input.dateOfBirth()),
            input.gender(),
            input.address(),
            input.allergies(),
            input.medicalHistory()
        );
        jdbc.update(
            "update patient_profiles set patient_code=? where id=?",
            "BN" + String.format("%08d", id),
            id
        );
        audit.record("CREATE_FAMILY", "PATIENT", String.valueOf(id), relation);
        return FamilyView.from(find(id), relation);
    }

    @Override
    @Transactional
    public FamilyView updateFamily(long id, FamilyInput input) {
        require("PATIENT");
        lockOwner();
        requireFamily(id);
        String relation = relationship(input.relationship());
        PatientView patient = update(id, input.patient());
        jdbc.update("update patient_profiles set relationship=? where id=?", relation, id);
        return FamilyView.from(patient, relation);
    }

    @Override
    @Transactional
    public void removeFamily(long id) {
        require("PATIENT");
        lockOwner();
        requireFamily(id);
        // Coordinate with booking's patient lock before deactivation.
        jdbc.queryForList("select id from patient_profiles where id=? for update", id);
        if (
            jdbc.queryForObject(
                "select count(*) from appointments where patient_id=? and status in ('CONFIRMED','CHECKED_IN','NEEDS_RESCHEDULE')",
                Integer.class,
                id
            ) > 0
        ) throw conflict("Hãy hoàn tất hoặc hủy lịch hẹn đang hoạt động trước khi xóa người thân.");
        jdbc.update("update patient_profiles set active=0 where id=?", id);
        jdbc.update(
            "update waitlist_entries set status='CANCELLED' where patient_id=? and status in ('ACTIVE','NOTIFIED')",
            id
        );
        audit.record("REMOVE_FAMILY", "PATIENT", String.valueOf(id), null);
    }

    private void lockOwner() {
        jdbc.queryForList("select id from users where id=? for update", user());
        access.primaryPatientId(user());
    }

    private void requireFamily(long id) {
        if (
            jdbc.queryForObject(
                "select count(*) from patient_profiles where id=? and owner_user_id=? and user_id is null and active=1",
                Integer.class,
                id,
                user()
            ) == 0
        ) throw forbidden();
    }

    private static String relationship(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 30) throw bad(
            "Quan hệ người thân bắt buộc, tối đa 30 ký tự."
        );
        return value.trim();
    }

    private static void validate(PatientInput input) {
        if (
            input.dateOfBirth() != null &&
            input.dateOfBirth().isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))
        ) throw bad("Ngày sinh không được ở tương lai.");
        if (
            input.gender() != null &&
            !input.gender().isBlank() &&
            !java.util.Set.of("MALE", "FEMALE", "OTHER").contains(input.gender())
        ) throw bad("Giới tính không hợp lệ.");
    }

    private PatientView find(long id) {
        List<PatientView> rows = jdbc.query(
            "select * from patient_profiles where id=?",
            (rs, row) -> map(rs),
            id
        );
        if (rows.isEmpty()) throw new ApiException(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy",
            "Hồ sơ bệnh nhân không tồn tại."
        );
        return rows.getFirst();
    }

    private static PatientView map(ResultSet rs) throws java.sql.SQLException {
        Date birth = rs.getDate("date_of_birth");
        return new PatientView(
            rs.getLong("id"),
            rs.getString("patient_code"),
            rs.getString("full_name"),
            rs.getString("phone"),
            rs.getString("email"),
            birth == null ? null : birth.toLocalDate(),
            rs.getString("gender"),
            rs.getString("address"),
            rs.getString("allergies"),
            rs.getString("medical_history"),
            rs.getBoolean("active")
        );
    }

    private static void fill(PreparedStatement statement, int offset, PatientInput input)
        throws java.sql.SQLException {
        statement.setString(offset, input.fullName().trim());
        statement.setString(offset + 1, input.phone());
        statement.setString(offset + 2, input.email());
        statement.setDate(offset + 3, date(input));
        statement.setString(offset + 4, input.gender());
        statement.setString(offset + 5, input.address());
        statement.setString(offset + 6, input.allergies());
        statement.setString(offset + 7, input.medicalHistory());
    }

    private static Date date(PatientInput input) {
        return input.dateOfBirth() == null ? null : Date.valueOf(input.dateOfBirth());
    }

    private static String like(String input) {
        return "%" + input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static boolean role(ClinicUserPrincipal actor, String name) {
        return actor
            .getAuthorities()
            .stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + name));
    }

    private static ApiException forbidden() {
        return new ApiException(
            HttpStatus.FORBIDDEN,
            "Không có quyền",
            "Bạn không có quyền xem hoặc sửa hồ sơ này."
        );
    }
}
