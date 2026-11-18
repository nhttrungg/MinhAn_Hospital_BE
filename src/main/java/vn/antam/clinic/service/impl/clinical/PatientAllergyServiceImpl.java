package vn.antam.clinic.service.impl.clinical;

import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.PatientAccess;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.clinical.PatientAllergyService;
import vn.antam.clinic.service.support.ClinicJdbc;

@Service
public class PatientAllergyServiceImpl implements PatientAllergyService {

    private final JdbcTemplate jdbc;
    private final PatientAccess access;
    private final AuditService audit;

    public PatientAllergyServiceImpl(JdbcTemplate jdbc, PatientAccess access, AuditService audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
    }

    private void authorize(long patientId) {
        if (ClinicJdbc.role("ADMIN") || ClinicJdbc.role("RECEPTIONIST")) return;
        if (ClinicJdbc.role("PATIENT") && access.owns(ClinicJdbc.user(), patientId)) return;
        if (
            ClinicJdbc.role("DOCTOR") &&
            jdbc.queryForObject(
                "select count(*) from encounters where patient_id=? and doctor_staff_id=? and status in ('WAITING','IN_PROGRESS','WAITING_RESULTS')",
                Integer.class,
                patientId,
                ClinicJdbc.staff(jdbc)
            ) > 0
        ) return;
        throw ClinicJdbc.forbidden();
    }

    private void lockPatient(long patient) {
        if (
            jdbc
                .query(
                    "select id from patient_profiles where id=? and active=1 for update",
                    (r, n) -> r.getLong(1),
                    patient
                )
                .isEmpty()
        ) throw ClinicJdbc.missing();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Allergy> list(long patientId) {
        authorize(patientId);
        return jdbc.query(
            "select * from patient_allergies where patient_id=? order by id",
            (r, n) ->
                new Allergy(
                    r.getLong("id"),
                    r.getLong("patient_id"),
                    r.getString("ingredient"),
                    r.getString("reaction"),
                    r.getString("severity"),
                    r.getInt("revision")
                ),
            patientId
        );
    }

    @Override
    @Transactional
    public Allergy save(long patientId, Long id, Input in) {
        authorize(patientId);
        lockPatient(patientId);
        String ingredient = in.ingredient().trim();
        var existing = list(patientId);
        for (var a : existing)
            if (
                (id == null || a.id() != id) &&
                ClinicalText.normalize(a.ingredient()).equals(ClinicalText.normalize(ingredient))
            ) throw ClinicJdbc.conflict("Dị ứng hoạt chất này đã được ghi nhận.");
        if (id == null) {
            if (existing.size() >= 100) throw ClinicJdbc.bad("Tối đa 100 mục dị ứng.");
            id = ClinicJdbc.insert(
                jdbc,
                "insert into patient_allergies(patient_id,ingredient,reaction,severity,recorded_at) values(?,?,?,?,UTC_TIMESTAMP(6))",
                patientId,
                ingredient,
                in.reaction(),
                in.severity()
            );
        } else if (
            jdbc.update(
                "update patient_allergies set ingredient=?,reaction=?,severity=?,recorded_at=UTC_TIMESTAMP(6),revision=revision+1 where id=? and patient_id=? and revision=?",
                ingredient,
                in.reaction(),
                in.severity(),
                id,
                patientId,
                in.revision()
            ) != 1
        ) throw ClinicJdbc.conflict("Dị ứng đã thay đổi. Tải lại trước khi sửa.");
        audit.record("SAVE", "PATIENT_ALLERGY", String.valueOf(id), String.valueOf(patientId));
        long savedId = id;
        return list(patientId)
            .stream()
            .filter(a -> a.id() == savedId)
            .findFirst()
            .orElseThrow();
    }

    @Override
    @Transactional
    public void delete(long patientId, long id, int revision) {
        authorize(patientId);
        lockPatient(patientId);
        if (
            jdbc.update(
                "delete from patient_allergies where patient_id=? and id=? and revision=?",
                patientId,
                id,
                revision
            ) != 1
        ) throw ClinicJdbc.conflict("Dị ứng đã thay đổi. Tải lại trước khi xóa.");
        audit.record("DELETE", "PATIENT_ALLERGY", String.valueOf(id), String.valueOf(patientId));
    }
}
