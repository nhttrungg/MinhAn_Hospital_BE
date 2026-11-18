package vn.antam.clinic.service.impl.clinical;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.ClinicUserPrincipal;
import vn.antam.clinic.security.SecurityUtils;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.clinical.EncounterService;
import vn.antam.clinic.service.clinical.Icd10Service;
import vn.antam.clinic.service.clinical.PrescriptionService;
import vn.antam.clinic.service.clinical.ReviewService;
import vn.antam.clinic.service.dto.clinical.EncounterDtos;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.AmendmentRequest;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.AmendmentView;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.DraftRequest;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.EncounterView;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.VitalsRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.support.ClinicJdbc;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
public class EncounterServiceImpl implements EncounterService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Icd10Service icd10;
    private final vn.antam.clinic.security.PatientAccess patientAccess;
    private final ReviewService reviews;
    private final vn.antam.clinic.service.event.ClinicEventPublisher events;
    private final vn.antam.clinic.service.clinical.PrescriptionService prescriptions;

    public EncounterServiceImpl(
        JdbcTemplate jdbc,
        AuditService audit,
        vn.antam.clinic.service.clinical.PrescriptionService prescriptions,
        Icd10Service icd10,
        vn.antam.clinic.security.PatientAccess patientAccess,
        ReviewService reviews,
        vn.antam.clinic.service.event.ClinicEventPublisher events
    ) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.prescriptions = prescriptions;
        this.icd10 = icd10;
        this.patientAccess = patientAccess;
        this.reviews = reviews;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public EncounterView forAppointment(long appointmentId) {
        List<Long> ids = jdbc.query(
            "select id from encounters where appointment_id=?",
            (rs, row) -> rs.getLong(1),
            appointmentId
        );
        if (ids.isEmpty()) throw notFound();
        return get(ids.getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public EncounterView get(long id) {
        EncounterView encounter = find(id);
        authorizeRead(encounter);
        return encounter;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EncounterView> myRecords(
        java.time.LocalDate from,
        java.time.LocalDate to,
        int page,
        int size
    ) {
        ClinicJdbc.require("PATIENT");
        var ids = patientAccess.ownedPatientIds(ClinicJdbc.user());
        if (ids.isEmpty()) return new PageResponse<>(
            List.of(),
            Math.max(0, page),
            Math.max(1, Math.min(100, size)),
            0,
            0
        );
        var dates = ClinicJdbc.dates(from, to);
        var args = new java.util.ArrayList<Object>(ids);
        args.add(dates.from());
        args.add(dates.to());
        return ClinicJdbc.queryPage(
            jdbc,
            "select e.id from encounters e where e.status='COMPLETED' and e.patient_id in (" +
                String.join(",", java.util.Collections.nCopies(ids.size(), "?")) +
                ") and e.completed_at>=? and e.completed_at<? order by e.completed_at desc,e.id desc",
            (r, n) -> get(r.getLong(1)),
            page,
            size,
            args.toArray()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<EncounterDtos.NurseReference> nurses() {
        ClinicJdbc.require("DOCTOR");
        return jdbc.query(
            "select s.id,u.full_name,d.name department_name from staff s join users u on u.id=s.user_id " +
                "join departments d on d.id=s.department_id where s.active=1 and u.status='ACTIVE' " +
                "and exists(select 1 from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=u.id and r.code='NURSE') order by u.full_name,s.id",
            (rs, row) ->
                new EncounterDtos.NurseReference(
                    rs.getLong("id"),
                    rs.getString("full_name"),
                    rs.getString("department_name")
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EncounterView> assigned(
        java.time.LocalDate from,
        java.time.LocalDate to,
        String status,
        int page,
        int size
    ) {
        ClinicJdbc.require("NURSE");
        var dates = ClinicJdbc.dates(from, to);
        return ClinicJdbc.queryPage(
            jdbc,
            "select e.id from encounters e join staff s on s.id=e.nurse_staff_id " +
                "join appointments a on a.id=e.appointment_id join appointment_slots sl on sl.id=a.slot_id " +
                "where s.user_id=? and sl.starts_at>=? and sl.starts_at<? and (? is null or e.status=?) order by sl.starts_at,e.id",
            (rs, row) -> get(rs.getLong(1)),
            page,
            size,
            SecurityUtils.currentPrincipal().getId(),
            dates.from(),
            dates.to(),
            status,
            status
        );
    }

    @Override
    @Transactional
    public EncounterView start(long appointmentId) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        long doctorId = doctorId(actor);
        List<Long> ids = jdbc.query(
            "select id from encounters where appointment_id=? and doctor_staff_id=?",
            (rs, row) -> rs.getLong(1),
            appointmentId,
            doctorId
        );
        if (ids.isEmpty()) throw forbidden();
        lock("select id from staff where id=? for update", doctorId);
        long id = ids.getFirst();
        lock("select id from encounters where id=? for update", id);
        EncounterView view = find(id);
        if (view.status().equals("IN_PROGRESS")) return view;
        if (
            !view.status().equals("WAITING") && !view.status().equals("WAITING_RESULTS")
        ) throw conflict("Lượt khám không còn ở trạng thái chờ.");
        Integer paid = jdbc.queryForObject(
            "select count(*) from invoices where appointment_id=? and status='PAID'",
            Integer.class,
            appointmentId
        );
        if (paid == null || paid == 0) throw conflict("Cần thu phí khám trước khi bắt đầu khám.");
        Integer other = jdbc.queryForObject(
            "select count(*) from encounters where doctor_staff_id=? and status='IN_PROGRESS' and id<>?",
            Integer.class,
            doctorId,
            id
        );
        if (other != null && other > 0) throw conflict("Bác sĩ đang khám cho bệnh nhân khác.");
        jdbc.update(
            "update encounters set status='IN_PROGRESS',started_at=coalesce(started_at,UTC_TIMESTAMP(6))," +
                "revision=revision+1 where id=?",
            id
        );
        audit.record("START", "ENCOUNTER", String.valueOf(id), null);
        events.queueUpdated(appointmentId);
        events.encounterUpdated(appointmentId);
        return find(id);
    }

    @Override
    @Transactional
    public EncounterView save(long id, DraftRequest request) {
        EncounterView before = find(id);
        requireDoctor(before);
        if (!before.status().equals("IN_PROGRESS")) throw conflict("Chỉ lưu nháp khi đang khám.");
        int changed = jdbc.update(
            "update encounters set chief_complaint=?,medical_history=?,allergies=?,vitals=?," +
                "diagnosis=?,conclusion=?,instructions=?,follow_up_date=?,revision=revision+1 where id=? and revision=? and status='IN_PROGRESS'",
            request.chiefComplaint(),
            request.medicalHistory(),
            request.allergies(),
            request.vitals(),
            request.diagnosis(),
            request.conclusion(),
            request.instructions(),
            request.followUpDate(),
            id,
            request.revision()
        );
        if (changed != 1) throw conflict(
            "Hồ sơ đã được cập nhật ở tab khác. Hãy tải lại trước khi lưu."
        );
        icd10.save(id, request.diagnoses());
        audit.record("SAVE_DRAFT", "ENCOUNTER", String.valueOf(id), null);
        return find(id);
    }

    @Override
    @Transactional
    public EncounterView waitResults(long id, int revision) {
        EncounterView before = find(id);
        requireDoctor(before);
        int changed = jdbc.update(
            "update encounters set status='WAITING_RESULTS',revision=revision+1 " +
                "where id=? and revision=? and status='IN_PROGRESS'",
            id,
            revision
        );
        if (changed != 1) throw conflict("Hồ sơ đã thay đổi hoặc không đang khám.");
        audit.record("WAIT_RESULTS", "ENCOUNTER", String.valueOf(id), null);
        events.queueUpdated(before.appointmentId());
        events.encounterUpdated(before.appointmentId());
        return find(id);
    }

    @Override
    @Transactional
    public EncounterView complete(long id, int revision) {
        lock("select id from encounters where id=? for update", id);
        EncounterView before = find(id);
        requireDoctor(before);
        if (before.status().equals("COMPLETED") && before.revision() == revision + 1) return before;
        if (
            jdbc.queryForObject(
                "select count(*) from diagnostic_orders where encounter_id=? and status not in ('COMPLETED','CANCELLED')",
                Integer.class,
                id
            ) > 0
        ) throw conflict("Còn chỉ định chưa có kết quả công bố hoặc chưa hủy.");
        if (
            ((before.diagnosis() == null || before.diagnosis().isBlank()) &&
                before.diagnoses().isEmpty()) ||
            before.conclusion() == null ||
            before.conclusion().isBlank()
        ) throw bad("Cần ghi chẩn đoán và kết luận trước khi hoàn tất.");
        int changed = jdbc.update(
            "update encounters set status='COMPLETED',completed_at=UTC_TIMESTAMP(6)," +
                "revision=revision+1 where id=? and revision=? and status='IN_PROGRESS'",
            id,
            revision
        );
        if (changed != 1) throw conflict("Hồ sơ đã thay đổi hoặc không đang khám.");
        jdbc.update(
            "update appointments set status='COMPLETED',revision=revision+1 where id=? and status='CHECKED_IN'",
            before.appointmentId()
        );
        prescriptions.finalizeDraft(id);
        audit.record("COMPLETE", "ENCOUNTER", String.valueOf(id), null);
        reviews.invite(id);
        events.queueUpdated(before.appointmentId());
        events.encounterUpdated(before.appointmentId());
        return find(id);
    }

    @Override
    @Transactional
    public EncounterView assignNurse(long id, long nurseStaffId) {
        lock("select id from encounters where id=? for update", id);
        EncounterView before = find(id);
        requireDoctor(before);
        Integer valid = jdbc.queryForObject(
            "select count(*) from staff s join user_roles ur on ur.user_id=s.user_id " +
                "join roles r on r.id=ur.role_id join users u on u.id=s.user_id where s.id=? and s.active=1 and u.status='ACTIVE' and r.code='NURSE'",
            Integer.class,
            nurseStaffId
        );
        if (valid == null || valid == 0) throw bad(
            "Nhân viên được chọn không phải điều dưỡng đang hoạt động."
        );
        if (before.status().equals("COMPLETED")) throw conflict("Hồ sơ đã hoàn tất.");
        jdbc.update(
            "update encounters set nurse_staff_id=?,revision=revision+1 where id=?",
            nurseStaffId,
            id
        );
        audit.record("ASSIGN_NURSE", "ENCOUNTER", String.valueOf(id), String.valueOf(nurseStaffId));
        return find(id);
    }

    @Override
    @Transactional
    public EncounterView vitals(long id, VitalsRequest request) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "NURSE")) throw forbidden();
        int changed = jdbc.update(
            "update encounters e join staff s on s.id=e.nurse_staff_id " +
                "set e.vitals=?,e.revision=e.revision+1 where e.id=? and e.revision=? " +
                "and e.status<>'COMPLETED' and s.user_id=?",
            request.vitals(),
            id,
            request.revision(),
            actor.getId()
        );
        if (changed != 1) throw conflict("Hồ sơ đã đổi, hoàn tất hoặc bạn chưa được phân công.");
        audit.record("VITALS", "ENCOUNTER", String.valueOf(id), null);
        return find(id);
    }

    @Override
    @Transactional
    public EncounterView amend(long id, AmendmentRequest request) {
        EncounterView before = find(id);
        requireDoctor(before);
        if (!before.status().equals("COMPLETED")) throw conflict("Chỉ bổ sung hồ sơ đã hoàn tất.");
        jdbc.update(
            "insert into encounter_amendments(encounter_id,author_user_id,reason,content) values(?,?,?,?)",
            id,
            SecurityUtils.currentPrincipal().getId(),
            request.reason().trim(),
            request.content().trim()
        );
        audit.record("AMEND", "ENCOUNTER", String.valueOf(id), request.reason());
        return find(id);
    }

    private EncounterView find(long id) {
        List<EncounterView> rows = jdbc.query(
            "select e.*,p.full_name patient_name,p.medical_history patient_medical_history," +
                "p.allergies patient_allergies,u.full_name doctor_name,a.appointment_code,nu.full_name nurse_name from encounters e join patient_profiles p on p.id=e.patient_id " +
                "join staff st on st.id=e.doctor_staff_id join users u on u.id=st.user_id join appointments a on a.id=e.appointment_id " +
                "left join staff ns on ns.id=e.nurse_staff_id left join users nu on nu.id=ns.user_id where e.id=?",
            (rs, row) ->
                new EncounterView(
                    rs.getLong("id"),
                    rs.getLong("appointment_id"),
                    rs.getLong("patient_id"),
                    rs.getString("patient_name"),
                    rs.getLong("doctor_staff_id"),
                    rs.getString("status"),
                    rs.getInt("revision"),
                    rs.getString("chief_complaint"),
                    rs.getString("medical_history"),
                    rs.getString("allergies"),
                    rs.getString("vitals"),
                    rs.getString("diagnosis"),
                    rs.getString("conclusion"),
                    rs.getString("patient_medical_history"),
                    rs.getString("patient_allergies"),
                    instant(rs, "started_at"),
                    instant(rs, "completed_at"),
                    amendments(id),
                    rs.getString("instructions"),
                    rs.getObject("follow_up_date", java.time.LocalDate.class),
                    rs.getString("doctor_name"),
                    rs.getString("appointment_code"),
                    rs.getObject("nurse_staff_id", Long.class),
                    rs.getString("nurse_name"),
                    icd10.diagnoses(id)
                ),
            id
        );
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst();
    }

    private List<AmendmentView> amendments(long id) {
        return jdbc.query(
            "select m.id,m.reason,m.content,m.created_at,u.full_name from encounter_amendments m join users u on u.id=m.author_user_id where m.encounter_id=? order by m.id",
            (rs, row) ->
                new AmendmentView(
                    rs.getLong(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getTimestamp(4).toInstant().toString(),
                    rs.getString(5)
                ),
            id
        );
    }

    private void authorizeRead(EncounterView encounter) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (
            role(actor, "DOCTOR") &&
            (doctorId(actor) == encounter.doctorId() ||
                (encounter.status().equals("COMPLETED") &&
                    jdbc.queryForObject(
                        "select count(*) from encounters e join appointments a on a.id=e.appointment_id where e.patient_id=? and e.doctor_staff_id=? and a.status='CHECKED_IN' and e.status in ('WAITING','IN_PROGRESS','WAITING_RESULTS')",
                        Integer.class,
                        encounter.patientId(),
                        doctorId(actor)
                    ) > 0))
        ) return;
        if (role(actor, "NURSE")) {
            Integer assigned = jdbc.queryForObject(
                "select count(*) from encounters e join staff s on s.id=e.nurse_staff_id " +
                    "where e.id=? and s.user_id=?",
                Integer.class,
                encounter.id(),
                actor.getId()
            );
            if (assigned != null && assigned > 0) return;
        }
        if (role(actor, "PATIENT") && encounter.status().equals("COMPLETED")) {
            if (patientAccess.owns(actor.getId(), encounter.patientId())) return;
        }
        throw forbidden();
    }

    private void requireDoctor(EncounterView encounter) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "DOCTOR") || doctorId(actor) != encounter.doctorId()) throw forbidden();
    }

    private long doctorId(ClinicUserPrincipal actor) {
        if (!role(actor, "DOCTOR")) throw forbidden();
        List<Long> ids = jdbc.query(
            "select id from staff where user_id=? and active=1",
            (rs, row) -> rs.getLong(1),
            actor.getId()
        );
        if (ids.isEmpty()) throw forbidden();
        return ids.getFirst();
    }

    private void lock(String sql, Object... params) {
        List<Long> ids = jdbc.query(sql, (rs, row) -> rs.getLong(1), params);
        if (ids.isEmpty()) throw notFound();
    }

    private static String instant(ResultSet rs, String name) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant().toString();
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
            "Bạn không được xem hoặc sửa lượt khám này."
        );
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy", "Lượt khám không tồn tại.");
    }

    private static ApiException bad(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Hồ sơ chưa đầy đủ", detail);
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Không cập nhật được lượt khám", detail);
    }
}
