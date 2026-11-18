package vn.antam.clinic.service.impl.clinical;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.clinical.PrescriptionSafetyService;
import vn.antam.clinic.service.clinical.PrescriptionService;
import vn.antam.clinic.service.dto.clinical.PrescriptionDtos.*;
import vn.antam.clinic.service.support.ClinicJdbc;

@Service
public class PrescriptionServiceImpl implements PrescriptionService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final PrescriptionSafetyService safety;
    private final vn.antam.clinic.security.PatientAccess patientAccess;
    private final com.fasterxml.jackson.databind.ObjectMapper json =
        new com.fasterxml.jackson.databind.ObjectMapper();

    public PrescriptionServiceImpl(
        JdbcTemplate jdbc,
        AuditService audit,
        PrescriptionSafetyService safety,
        vn.antam.clinic.security.PatientAccess patientAccess
    ) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.safety = safety;
        this.patientAccess = patientAccess;
    }

    private record Context(long patientId, long doctorId, String status, int revision) {}

    private Context context(long id, boolean lock) {
        List<Context> rows = jdbc.query(
            "select patient_id,doctor_staff_id,status,revision from encounters where id=?" +
                (lock ? " for update" : ""),
            (r, n) -> new Context(r.getLong(1), r.getLong(2), r.getString(3), r.getInt(4)),
            id
        );
        if (rows.isEmpty()) throw ClinicJdbc.missing();
        return rows.getFirst();
    }

    private void readAccess(Context c) {
        if (ClinicJdbc.role("DOCTOR")) {
            long doctor = ClinicJdbc.staff(jdbc);
            if (
                c.doctorId() == doctor ||
                (c.status().equals("COMPLETED") &&
                    jdbc.queryForObject(
                        "select count(*) from encounters e join appointments a on a.id=e.appointment_id where e.patient_id=? and e.doctor_staff_id=? and a.status='CHECKED_IN' and e.status in ('WAITING','IN_PROGRESS','WAITING_RESULTS')",
                        Integer.class,
                        c.patientId(),
                        doctor
                    ) > 0)
            ) return;
        }
        if (
            ClinicJdbc.role("PATIENT") &&
            c.status().equals("COMPLETED") &&
            patientAccess.owns(ClinicJdbc.user(), c.patientId())
        ) return;
        throw ClinicJdbc.forbidden();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Prescription> list(long encounterId) {
        Context c = context(encounterId, false);
        readAccess(c);
        return jdbc.query(
            "select id from prescriptions where encounter_id=? " +
                (ClinicJdbc.role("PATIENT") ? "and status='FINAL' " : "") +
                "order by version desc",
            (r, n) -> find(r.getLong(1)),
            encounterId
        );
    }

    @Override
    @Transactional
    public Prescription save(long encounterId, SaveRequest in, boolean amendment) {
        Context c = context(encounterId, true);
        ClinicJdbc.require("DOCTOR");
        if (ClinicJdbc.staff(jdbc) != c.doctorId()) throw ClinicJdbc.forbidden();
        if (c.revision() != in.encounterRevision()) throw ClinicJdbc.conflict(
            "Bệnh án đã thay đổi. Tải lại trước khi kê đơn."
        );
        if (amendment) {
            if (
                jdbc.queryForObject(
                    "select count(*) from dispensings where encounter_id=? and status in ('DRAFT','READY')",
                    Integer.class,
                    encounterId
                ) > 0
            ) throw ClinicJdbc.conflict("Xử lý phiếu đang soạn/thu/giao trước khi sửa đơn.");
            if (
                jdbc.queryForObject(
                    "select count(*) from dispensings where encounter_id=? and status='DELIVERED'",
                    Integer.class,
                    encounterId
                ) > 0 &&
                !Boolean.TRUE.equals(in.remainingQuantityConfirmed())
            ) throw ClinicJdbc.bad(
                "Cần đối chiếu thuốc đã giao và xác nhận số lượng trong đơn mới là phần còn cần cấp."
            );
            if (
                !c.status().equals("COMPLETED") || in.reason() == null || in.reason().isBlank()
            ) throw ClinicJdbc.bad("Bổ sung đơn cần lượt đã hoàn tất và lý do.");
        } else if (!c.status().equals("IN_PROGRESS")) throw ClinicJdbc.conflict(
            "Chỉ kê đơn nháp khi đang khám."
        );
        var warnings = safety.check(
            c.patientId(),
            in.lines().stream().map(LineInput::medicationId).toList()
        );
        String override = in.overrideReason() == null ? null : in.overrideReason().trim();
        if (
            PrescriptionSafetyServiceImpl.requiresOverride(warnings) &&
            (override == null || override.isBlank())
        ) throw new vn.antam.clinic.web.rest.errors.ApiException(
            org.springframework.http.HttpStatus.CONFLICT,
            "Cần giải trình cảnh báo",
            "Đơn có cảnh báo nặng hoặc dị ứng. Nhập lý do trước khi lưu.",
            java.util.Map.of("code", "SAFETY_OVERRIDE_REQUIRED", "warnings", warnings)
        );
        List<Long> drafts = jdbc.query(
            "select id from prescriptions where encounter_id=? and status='DRAFT'",
            (r, n) -> r.getLong(1),
            encounterId
        );
        long id;
        if (!amendment && !drafts.isEmpty()) {
            id = drafts.getFirst();
            if (
                jdbc.update(
                    "update prescriptions set revision=revision+1 where id=? and revision=? and status='DRAFT'",
                    id,
                    in.revision()
                ) != 1
            ) throw ClinicJdbc.conflict("Đơn thuốc đã thay đổi ở tab khác.");
            jdbc.update("delete from prescription_lines where prescription_id=?", id);
        } else {
            if (!amendment && in.revision() != 0) throw ClinicJdbc.conflict(
                "Đơn thuốc chưa tồn tại hoặc đã thay đổi."
            );
            int version = jdbc.queryForObject(
                "select coalesce(max(version),0)+1 from prescriptions where encounter_id=?",
                Integer.class,
                encounterId
            );
            if (amendment && version - 1 != in.revision()) throw ClinicJdbc.conflict(
                "Đã có phiên bản đơn mới. Tải lại trước khi bổ sung."
            );
            id = ClinicJdbc.insert(
                jdbc,
                "insert into prescriptions(encounter_id,version,reason,author_user_id,status,finalized_at) values(?,?,?,?,?," +
                    (amendment ? "UTC_TIMESTAMP(6)" : "null") +
                    ")",
                encounterId,
                version,
                in.reason(),
                ClinicJdbc.user(),
                amendment ? "FINAL" : "DRAFT"
            );
        }
        for (LineInput line : in.lines()) {
            List<String[]> medications = jdbc.query(
                "select name,strength,unit from medications where id=? and active=1",
                (r, n) -> new String[] { r.getString(1), r.getString(2), r.getString(3) },
                line.medicationId()
            );
            if (medications.isEmpty()) throw ClinicJdbc.bad(
                "Thuốc không còn hoạt động: #" + line.medicationId()
            );
            String[] m = medications.getFirst();
            jdbc.update(
                "insert into prescription_lines(prescription_id,medication_id,name_snapshot,strength_snapshot,unit_snapshot,dose,route,frequency,days,quantity,instructions) values(?,?,?,?,?,?,?,?,?,?,?)",
                id,
                line.medicationId(),
                m[0],
                m[1],
                m[2],
                line.dose().trim(),
                line.route().trim(),
                line.frequency().trim(),
                line.days(),
                line.quantity(),
                line.instructions()
            );
        }
        if (amendment) jdbc.update(
            "update pharmacy_orders set prescription_id=?,status='OPEN',revision=revision+1 where encounter_id=?",
            id,
            encounterId
        );
        jdbc.update(
            "update prescriptions set safety_override_reason=?,safety_warnings_json=? where id=?",
            warnings.isEmpty() ? null : override,
            encodeWarnings(warnings),
            id
        );
        if (!warnings.isEmpty() && override != null && !override.isBlank()) audit.record(
            "SAFETY_OVERRIDE",
            "PRESCRIPTION",
            String.valueOf(id),
            override
        );
        audit.record(
            amendment ? "AMEND" : "SAVE_DRAFT",
            "PRESCRIPTION",
            String.valueOf(id),
            in.reason()
        );
        return find(id);
    }

    @Override
    public void finalizeDraft(long encounterId) {
        // Allergies/catalog may have changed since the draft: recheck before finalization.
        Context c = context(encounterId, false);
        var drafts = jdbc.query(
            "select id,safety_override_reason,safety_warnings_json from prescriptions where encounter_id=? and status='DRAFT'",
            (r, n) -> new Object[] { r.getLong(1), r.getString(2), r.getString(3) },
            encounterId
        );
        for (var draft : drafts) {
            long id = (Long) draft[0];
            var ids = jdbc.query(
                "select medication_id from prescription_lines where prescription_id=? order by id",
                (r, n) -> r.getLong(1),
                id
            );
            var warnings = safety.check(c.patientId(), ids);
            if (
                PrescriptionSafetyServiceImpl.requiresOverride(warnings) &&
                (draft[1] == null ||
                    ((String) draft[1]).isBlank() ||
                    !warnings.equals(decodeWarnings((String) draft[2])))
            ) throw new vn.antam.clinic.web.rest.errors.ApiException(
                org.springframework.http.HttpStatus.CONFLICT,
                "Cần kiểm tra lại đơn thuốc",
                "Cảnh báo đã thay đổi. Lưu lại đơn và giải trình trước khi hoàn tất."
            ).withCode("SAFETY_OVERRIDE_REQUIRED");
        }
        jdbc.update(
            "update prescriptions set status='FINAL',revision=revision+1,finalized_at=UTC_TIMESTAMP(6) where encounter_id=? and status='DRAFT'",
            encounterId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrescriptionSafetyService.Warning> check(long encounterId, CheckRequest in) {
        ClinicJdbc.require("DOCTOR");
        Context c = context(encounterId, false);
        if (ClinicJdbc.staff(jdbc) != c.doctorId()) throw ClinicJdbc.forbidden();
        return safety.check(c.patientId(), in.medicationIds());
    }

    private String encodeWarnings(List<PrescriptionSafetyService.Warning> warnings) {
        try {
            return json.writeValueAsString(warnings);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<PrescriptionSafetyService.Warning> decodeWarnings(String value) {
        if (value == null) return List.of();
        try {
            return json.readValue(
                value,
                new com.fasterxml.jackson.core.type.TypeReference<
                    List<PrescriptionSafetyService.Warning>
                >() {}
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Prescription find(long id) {
        return jdbc.queryForObject(
            "select p.*,u.full_name author_name from prescriptions p join users u on u.id=p.author_user_id where p.id=?",
            (r, n) ->
                new Prescription(
                    r.getLong("id"),
                    r.getLong("encounter_id"),
                    r.getInt("version"),
                    r.getInt("revision"),
                    r.getString("status"),
                    r.getString("reason"),
                    r.getString("author_name"),
                    r.getTimestamp("created_at").toInstant().toString(),
                    r.getTimestamp("finalized_at") == null
                        ? null
                        : r.getTimestamp("finalized_at").toInstant().toString(),
                    jdbc.query(
                        "select * from prescription_lines where prescription_id=? order by id",
                        (l, i) ->
                            new Line(
                                l.getLong("id"),
                                l.getLong("medication_id"),
                                l.getString("name_snapshot"),
                                l.getString("strength_snapshot"),
                                l.getString("unit_snapshot"),
                                l.getString("dose"),
                                l.getString("route"),
                                l.getString("frequency"),
                                l.getInt("days"),
                                l.getBigDecimal("quantity").toPlainString(),
                                l.getString("instructions")
                            ),
                        id
                    ),
                    r.getString("safety_override_reason"),
                    decodeWarnings(r.getString("safety_warnings_json"))
                ),
            id
        );
    }
}
