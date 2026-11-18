package vn.antam.clinic.service.impl.clinical;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.clinical.*;
import vn.antam.clinic.service.dto.clinical.ClinicalProductivityDtos.*;

@Service
public class ClinicalProductivityServiceImpl implements ClinicalProductivityService {

    private final JdbcTemplate db;
    private final EncounterService encounters;
    private final PrescriptionService prescriptions;
    private final AuditService audit;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public ClinicalProductivityServiceImpl(
        JdbcTemplate db,
        EncounterService encounters,
        PrescriptionService prescriptions,
        AuditService audit,
        Clock clock
    ) {
        this.db = db;
        this.encounters = encounters;
        this.prescriptions = prescriptions;
        this.audit = audit;
        this.clock = clock;
    }

    private String encode(Object v) {
        try {
            return json.writeValueAsString(v);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String hash(Object v) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    encode(v).getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String time(Object v) {
        return v == null ? null : instant(v).toString();
    }

    private long specialty() {
        return db.queryForObject(
            "select specialty_id from doctor_profiles where staff_id=?",
            Long.class,
            staff(db)
        );
    }

    private Template template(long id) {
        var found = db.query(
            "select t.*,u.full_name author_name from clinical_templates t join users u on u.id=t.author_user_id where t.id=?",
            (r, n) ->
                new Template(
                    r.getLong("id"),
                    r.getObject("owner_staff_id", Long.class),
                    r.getLong("specialty_id"),
                    r.getString("name"),
                    r.getString("state"),
                    r.getInt("version"),
                    r.getString("chief_complaint"),
                    r.getString("diagnosis"),
                    r.getString("conclusion"),
                    r.getString("instructions"),
                    r.getString("author_name"),
                    time(r.getObject("updated_at"))
                ),
            id
        );
        if (found.isEmpty()) throw missing();
        return found.getFirst();
    }

    private void editable(Template t) {
        if (t.ownerStaffId() == null) require("ADMIN");
        else {
            require("DOCTOR");
            if (t.ownerStaffId() != staff(db)) throw forbidden();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Template> templates(Long specialtyId) {
        require("ADMIN", "DOCTOR");
        long scope = role("ADMIN") ? 0 : specialty();
        long owner = role("ADMIN") ? 0 : staff(db);
        return db.query(
            "select id from clinical_templates where (? is null or specialty_id=?) and ((owner_staff_id is null and (?=0 or (specialty_id=? and state='PUBLISHED'))) or owner_staff_id=?) order by name,id",
            (r, n) -> template(r.getLong(1)),
            specialtyId,
            specialtyId,
            scope,
            scope,
            owner
        );
    }

    private void validate(TemplateInput in) {
        if (in.shared()) require("ADMIN");
        else {
            require("DOCTOR");
            if (specialty() != in.specialtyId()) throw forbidden();
        }
        if (
            db.queryForObject(
                "select count(*) from specialties where id=? and active=1",
                Integer.class,
                in.specialtyId()
            ) != 1
        ) throw bad("Chuyên khoa không hoạt động.");
    }

    private void snapshot(Template t) {
        db.update(
            "insert into clinical_template_versions(template_id,version,snapshot,author_user_id,published_at) values(?,?,?,?,?)",
            t.id(),
            t.version(),
            encode(t),
            user(),
            t.state().equals("PUBLISHED") ? Timestamp.from(clock.instant()) : null
        );
    }

    @Override
    @Transactional
    public Template createTemplate(TemplateInput in) {
        validate(in);
        long id = insert(
            db,
            "insert into clinical_templates(owner_staff_id,specialty_id,name,state,chief_complaint,diagnosis,conclusion,instructions,author_user_id) values(?,?,?,?,?,?,?,?,?)",
            in.shared() ? null : staff(db),
            in.specialtyId(),
            in.name().trim(),
            in.shared() ? "DRAFT" : "PUBLISHED",
            in.chiefComplaint(),
            in.diagnosis(),
            in.conclusion(),
            in.instructions(),
            user()
        );
        Template t = template(id);
        snapshot(t);
        audit.record("CREATE", "CLINICAL_TEMPLATE", String.valueOf(id), null);
        return t;
    }

    @Override
    @Transactional
    public Template updateTemplate(long id, TemplateInput in) {
        Template old = template(id);
        editable(old);
        validate(in);
        if ((old.ownerStaffId() == null) != in.shared()) throw bad(
            "Không đổi phạm vi của mẫu đã tạo."
        );
        if (
            db.update(
                "update clinical_templates set name=?,specialty_id=?,chief_complaint=?,diagnosis=?,conclusion=?,instructions=?,state=?,version=version+1,author_user_id=?,updated_at=? where id=? and version=?",
                in.name().trim(),
                in.specialtyId(),
                in.chiefComplaint(),
                in.diagnosis(),
                in.conclusion(),
                in.instructions(),
                in.shared() ? "DRAFT" : "PUBLISHED",
                user(),
                Timestamp.from(clock.instant()),
                id,
                in.version()
            ) != 1
        ) throw conflict("Mẫu đã được sửa. Tải lại trước khi lưu.");
        Template t = template(id);
        snapshot(t);
        audit.record("UPDATE", "CLINICAL_TEMPLATE", String.valueOf(id), null);
        return t;
    }

    @Override
    @Transactional
    public Template templateState(long id, int version, boolean publish) {
        Template t = template(id);
        editable(t);
        if (publish) require("ADMIN");
        if (
            db.update(
                "update clinical_templates set state=?,updated_at=? where id=? and version=?",
                publish ? "PUBLISHED" : "INACTIVE",
                Timestamp.from(clock.instant()),
                id,
                version
            ) != 1
        ) throw conflict("Mẫu đã thay đổi.");
        if (publish) db.update(
            "update clinical_template_versions set published_at=coalesce(published_at,?) where template_id=? and version=?",
            Timestamp.from(clock.instant()),
            id,
            version
        );
        audit.record(
            publish ? "PUBLISH" : "DEACTIVATE",
            "CLINICAL_TEMPLATE",
            String.valueOf(id),
            null
        );
        return template(id);
    }

    private void ownEncounter(long id, boolean draft) {
        require("DOCTOR");
        var rows = db.queryForList(
            "select e.doctor_staff_id,e.status,a.status appointment_status from encounters e join appointments a on a.id=e.appointment_id where e.id=? for update",
            id
        );
        if (rows.isEmpty()) throw missing();
        var r = rows.getFirst();
        if (((Number) r.get("doctor_staff_id")).longValue() != staff(db)) throw forbidden();
        if (draft && !r.get("status").equals("IN_PROGRESS")) throw conflict(
            "Bệnh án không còn đang khám."
        );
        if (
            !draft && !Set.of("CHECKED_IN", "COMPLETED").contains(r.get("appointment_status"))
        ) throw forbidden();
    }

    private <T> T replay(long id, String kind, String key, Object in, Class<T> type) {
        var rows = db.queryForList(
            "select request_hash,response from clinical_draft_requests where actor_user_id=? and encounter_id=? and kind=? and request_id=?",
            user(),
            id,
            kind,
            key
        );
        if (rows.isEmpty()) return null;
        if (!rows.getFirst().get("request_hash").equals(hash(in))) throw conflict(
            "Mã lưu nháp đã dùng cho nội dung khác."
        );
        try {
            return json.readValue(rows.getFirst().get("response").toString(), type);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void remember(long id, String kind, String key, Object in, Object response) {
        db.update(
            "insert into clinical_draft_requests(actor_user_id,encounter_id,kind,request_id,request_hash,response) values(?,?,?,?,?,?)",
            user(),
            id,
            kind,
            key,
            hash(in),
            encode(response)
        );
    }

    @Override
    @Transactional
    public ChartSaved saveChart(long id, ChartSync in) {
        ownEncounter(id, true);
        ChartSaved previous = replay(id, "CHART", in.requestId(), in, ChartSaved.class);
        if (previous != null) return previous;
        if (in.template() != null) {
            Template t = template(in.template().templateId());
            var versions = db.queryForList(
                "select snapshot from clinical_template_versions where template_id=? and version=? and published_at is not null",
                t.id(),
                in.template().version()
            );
            if (versions.isEmpty() || t.state().equals("INACTIVE")) throw conflict(
                "Phiên bản mẫu chưa xuất bản hoặc đã ngưng sử dụng."
            );
            try {
                t = json.readValue(versions.getFirst().get("snapshot").toString(), Template.class);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            if (t.ownerStaffId() != null) {
                if (t.ownerStaffId() != staff(db)) throw forbidden();
            } else if (t.specialtyId() != specialty()) throw forbidden();
        }
        var data = encounters.save(id, in.draft());
        var result = new ChartSaved(data, data.revision(), clock.instant().toString());
        if (in.template() != null) db.update(
            "insert into encounter_template_applications(encounter_id,template_id,template_version,applied_by) values(?,?,?,?)",
            id,
            in.template().templateId(),
            in.template().version(),
            user()
        );
        remember(id, "CHART", in.requestId(), in, result);
        return result;
    }

    @Override
    @Transactional
    public RxSaved saveRx(long id, RxSync in) {
        ownEncounter(id, true);
        RxSaved previous = replay(id, "RX", in.requestId(), in, RxSaved.class);
        if (previous != null) return previous;
        var data = prescriptions.save(id, in.draft(), false);
        var result = new RxSaved(data, data.revision(), clock.instant().toString());
        remember(id, "RX", in.requestId(), in, result);
        return result;
    }

    @Override
    @Transactional
    public PatientSummary summary(long id) {
        ownEncounter(id, false);
        var c = encounters.get(id);
        var p = db.queryForMap(
            "select date_of_birth,medical_history,allergies,updated_at from patient_profiles where id=?",
            c.patientId()
        );
        var visits = db.queryForList(
            "select id,vitals,diagnosis,completed_at from encounters where patient_id=? and status='COMPLETED' and id<>? order by completed_at desc,id desc limit 1",
            c.patientId(),
            id
        );
        Map<String, Object> v = visits.isEmpty() ? Map.of() : visits.getFirst();
        var measured = db.queryForList(
            "select id,vitals,coalesce(completed_at,updated_at) measured_at from encounters where patient_id=? and (status='COMPLETED' or id=?) and vitals is not null and trim(vitals)<>'' order by coalesce(completed_at,updated_at) desc,id desc limit 1",
            c.patientId(),
            id
        );
        Map<String, Object> vit = measured.isEmpty() ? Map.of() : measured.getFirst();
        String profile = "/staff/encounters/" + id;
        String href = v.isEmpty() ? null : "/staff/encounters/" + v.get("id");
        List<PriorMedication> meds = db.query(
            "select l.name_snapshot,l.strength_snapshot,l.dose,l.frequency,l.quantity,rx.finalized_at,e.id from prescription_lines l join prescriptions rx on rx.id=l.prescription_id join encounters e on e.id=rx.encounter_id where e.patient_id=? and e.status='COMPLETED' and e.id<>? and rx.status='FINAL' and rx.version=(select max(r2.version) from prescriptions r2 where r2.encounter_id=e.id and r2.status='FINAL') order by rx.finalized_at desc,l.id limit 30",
            (r, n) ->
                new PriorMedication(
                    r.getString(1),
                    r.getString(2),
                    r.getString(3),
                    r.getString(4),
                    r.getString(5),
                    time(r.getObject(6)),
                    "/staff/encounters/" + r.getLong(7)
                ),
            c.patientId(),
            id
        );
        java.sql.Date birth = (java.sql.Date) p.get("date_of_birth");
        Integer age =
            birth == null
                ? null
                : Period.between(birth.toLocalDate(), LocalDate.now(clock)).getYears();
        var allergyDetails = db.query(
            "select id,ingredient,reaction,severity,recorded_at from patient_allergies where patient_id=? order by id",
            (r, n) ->
                new Source(
                    r.getString("ingredient") +
                        " · " +
                        Objects.toString(r.getString("reaction"), "Chưa khai phản ứng") +
                        " · " +
                        r.getString("severity"),
                    time(r.getObject("recorded_at")),
                    profile + "#allergy-" + r.getLong("id")
                ),
            c.patientId()
        );
        return new PatientSummary(
            id,
            age,
            new Source((String) p.get("medical_history"), time(p.get("updated_at")), profile),
            new Source((String) p.get("allergies"), time(p.get("updated_at")), profile),
            new Source(
                (String) vit.get("vitals"),
                time(vit.get("measured_at")),
                vit.isEmpty() ? null : "/staff/encounters/" + vit.get("id")
            ),
            new Source(
                v.isEmpty() ? null : "Lượt #" + v.get("id"),
                time(v.get("completed_at")),
                href
            ),
            new Source((String) v.get("diagnosis"), time(v.get("completed_at")), href),
            meds,
            allergyDetails
        );
    }

    @Override
    @Transactional
    public DiagnosticHistory history(long id, LocalDate from, LocalDate to) {
        ownEncounter(id, false);
        var c = encounters.get(id);
        LocalDate today = LocalDate.now(clock);
        var window = dates(from == null ? today.minusMonths(12) : from, to == null ? today : to);
        var results = db.queryForList(
            "select r.id,r.order_id,r.measurements,r.description,r.conclusion,r.published_at,o.encounter_id,o.service_id,s.name service_name from diagnostic_results r join diagnostic_orders o on o.id=r.order_id join services s on s.id=o.service_id join encounters e on e.id=o.encounter_id where e.patient_id=? and (e.status='COMPLETED' or e.id=?) and o.status<>'CANCELLED' and r.status='PUBLISHED' and r.version=(select max(r2.version) from diagnostic_results r2 where r2.order_id=r.order_id and r2.status='PUBLISHED') and r.published_at>=? and r.published_at<? order by r.published_at,r.id",
            c.patientId(),
            id,
            window.from(),
            window.to()
        );
        List<DiagnosticPoint> points = new ArrayList<>();
        int ambiguous = 0;
        for (var r : results) {
            List<Map<String, Object>> values;
            try {
                values = json.readValue(
                    r.get("measurements").toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<
                        List<Map<String, Object>>
                    >() {}
                );
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            for (var m : values) {
                Long definition = m.get("definitionId") instanceof Number n ? n.longValue() : null;
                String name = Objects.toString(m.get("name"), ""),
                    unit = Objects.toString(m.get("unit"), "");
                if (definition == null) {
                    var candidates = db.queryForList(
                        "select id from diagnostic_definitions where service_id=? and name=? and coalesce(unit,'')=?",
                        Long.class,
                        r.get("service_id"),
                        name,
                        unit
                    );
                    if (candidates.size() == 1) definition = candidates.getFirst();
                    else ambiguous++;
                }
                String value = Objects.toString(m.get("value"), "");
                BigDecimal numeric = null;
                try {
                    numeric = new BigDecimal(value);
                } catch (NumberFormatException ignored) {}
                points.add(
                    new DiagnosticPoint(
                        ((Number) r.get("id")).longValue(),
                        ((Number) r.get("order_id")).longValue(),
                        ((Number) r.get("encounter_id")).longValue(),
                        ((Number) r.get("service_id")).longValue(),
                        r.get("service_name").toString(),
                        definition,
                        name,
                        value,
                        numeric,
                        unit,
                        Objects.toString(m.get("referenceRange"), ""),
                        time(r.get("published_at")),
                        "/staff/encounters/" +
                            r.get("encounter_id") +
                            "#diagnostic-" +
                            r.get("order_id"),
                        definition != null
                    )
                );
            }
            if (values.isEmpty()) points.add(
                new DiagnosticPoint(
                    ((Number) r.get("id")).longValue(),
                    ((Number) r.get("order_id")).longValue(),
                    ((Number) r.get("encounter_id")).longValue(),
                    ((Number) r.get("service_id")).longValue(),
                    r.get("service_name").toString(),
                    null,
                    "Kết luận",
                    Objects.toString(
                        r.get("conclusion"),
                        Objects.toString(r.get("description"), "")
                    ),
                    null,
                    "",
                    "",
                    time(r.get("published_at")),
                    "/staff/encounters/" +
                        r.get("encounter_id") +
                        "#diagnostic-" +
                        r.get("order_id"),
                    false
                )
            );
        }
        return new DiagnosticHistory(points, ambiguous);
    }

    @Override
    @Transactional(readOnly = true)
    public Availability availability(StockRequest in) {
        require("DOCTOR");
        staff(db);
        List<MedicationStock> meds = new ArrayList<>();
        for (long id : new LinkedHashSet<>(in.medicationIds())) {
            var units = db.queryForList(
                "select unit from medications where id=? and active=1",
                String.class,
                id
            );
            if (units.isEmpty()) throw bad("Thuốc không hoạt động.");
            var counters = db.query(
                "select c.id,c.name,coalesce(sum(b.quantity-b.reserved),0) available from pharmacy_counters c left join medication_batches b on b.counter_id=c.id and b.medication_id=? and b.active=1 and b.expiry_date>=? where c.active=1 and (? is null or c.id=?) group by c.id,c.name order by c.name,c.id",
                (r, n) -> new CounterStock(r.getLong(1), r.getString(2), r.getBigDecimal(3)),
                id,
                LocalDate.now(clock),
                in.counterId(),
                in.counterId()
            );
            meds.add(
                new MedicationStock(
                    id,
                    units.getFirst(),
                    counters
                        .stream()
                        .map(CounterStock::available)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                    counters
                )
            );
        }
        return new Availability(clock.instant().toString(), meds);
    }
}
