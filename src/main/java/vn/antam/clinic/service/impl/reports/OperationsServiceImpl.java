package vn.antam.clinic.service.impl.reports;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.dto.reports.OperationsDtos.*;
import vn.antam.clinic.service.reports.OperationsService;

@Service("capacityOperationsService")
public class OperationsServiceImpl implements OperationsService {

    private final JdbcTemplate db;
    private final Clock clock;
    private final AuditService audit;

    public OperationsServiceImpl(JdbcTemplate db, Clock clock, AuditService audit) {
        this.db = db;
        this.clock = clock;
        this.audit = audit;
    }

    private String time(Object v) {
        return v == null ? null : instant(v).toString();
    }

    private Thresholds limits() {
        return db.queryForObject(
            "select wait_payment_minutes,wait_doctor_minutes,wait_diagnostic_minutes from clinic_settings where id=1",
            (r, n) -> new Thresholds(r.getInt(1), r.getInt(2), r.getInt(3))
        );
    }

    @Override
    public Thresholds thresholds() {
        require("ADMIN");
        return limits();
    }

    @Override
    @Transactional
    public Thresholds updateThresholds(Thresholds in) {
        require("ADMIN");
        db.update(
            "update clinic_settings set wait_payment_minutes=?,wait_doctor_minutes=?,wait_diagnostic_minutes=? where id=1",
            in.paymentMinutes(),
            in.doctorMinutes(),
            in.diagnosticMinutes()
        );
        audit.record("WAIT_THRESHOLDS", "CLINIC_SETTINGS", "1", in.toString());
        return limits();
    }

    private List<DiagnosticProgress> progress(long id) {
        return db.query(
            "select o.id,s.name,o.status,case when o.status='ISSUED' and not exists(select 1 from invoice_lines l join invoices i on i.id=l.invoice_id where l.source_type='DIAGNOSTIC' and l.source_id=o.id and l.status='ACTIVE' and i.status='PAID') then 'UNPAID' when o.status='ISSUED' then 'UNACCEPTED' when o.status in ('ACCEPTED','IN_PROGRESS') then 'IN_PROGRESS' else o.status end phase from diagnostic_orders o join services s on s.id=o.service_id where o.encounter_id=? order by o.id",
            (r, n) ->
                new DiagnosticProgress(
                    r.getLong(1),
                    r.getString(2),
                    r.getString(3),
                    r.getString(4)
                ),
            id
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Bottlenecks bottlenecks(
        LocalDate from,
        LocalDate to,
        Long departmentId,
        Long doctorId,
        Long roomId
    ) {
        require("ADMIN", "RECEPTIONIST", "DOCTOR");
        if (!role("ADMIN") && !role("RECEPTIONIST")) {
            long own = staff(db);
            if (doctorId != null && doctorId != own) throw forbidden();
            doctorId = own;
        }
        LocalDate today = LocalDate.now(clock);
        var dates = dates(from == null ? today : from, to == null ? today : to);
        var threshold = limits();
        var found = db.queryForList(
            "select e.id,e.appointment_id,e.status encounter_status,a.appointment_code,p.full_name patient_name,s.doctor_staff_id,u.full_name doctor_name,s.room_id,rm.name room_name,h.stage,h.started_at from encounters e join appointments a on a.id=e.appointment_id join patient_profiles p on p.id=e.patient_id join appointment_slots s on s.id=a.slot_id join staff st on st.id=s.doctor_staff_id join users u on u.id=st.user_id join rooms rm on rm.id=s.room_id left join encounter_stages h on h.encounter_id=e.id and h.ended_at is null where e.status<>'COMPLETED' and a.checked_in_at>=? and a.checked_in_at<? and (? is null or st.department_id=?) and (? is null or e.doctor_staff_id=?) and (? is null or s.room_id=?) order by h.started_at,e.id",
            dates.from(),
            dates.to(),
            departmentId,
            departmentId,
            doctorId,
            doctorId,
            roomId,
            roomId
        );
        List<Bottleneck> rows = new ArrayList<>();
        for (var r : found) {
            long id = ((Number) r.get("id")).longValue();
            var diagnostics = progress(id);
            String stage = Objects.toString(
                r.get("stage"),
                switch (r.get("encounter_status").toString()) {
                    case "IN_PROGRESS" -> "EXAMINING";
                    case "WAITING_RESULTS" -> "DIAGNOSTICS";
                    default -> "DOCTOR";
                }
            );
            Long elapsed =
                r.get("started_at") == null
                    ? null
                    : Math.max(
                          0,
                          Duration.between(
                              instant(r.get("started_at")),
                              clock.instant()
                          ).getSeconds()
                      );
            int limit = switch (stage) {
                case "PAYMENT" -> threshold.paymentMinutes();
                case "DIAGNOSTICS" -> threshold.diagnosticMinutes();
                default -> threshold.doctorMinutes();
            };
            String href = switch (stage) {
                case "PAYMENT" -> "/staff/billing";
                case "DOCTOR" -> "/staff/queue";
                case "DIAGNOSTICS" -> "/staff/diagnostics";
                default -> "/staff/encounters/" + id;
            };
            rows.add(
                new Bottleneck(
                    id,
                    ((Number) r.get("appointment_id")).longValue(),
                    r.get("appointment_code").toString(),
                    r.get("patient_name").toString(),
                    ((Number) r.get("doctor_staff_id")).longValue(),
                    r.get("doctor_name").toString(),
                    ((Number) r.get("room_id")).longValue(),
                    r.get("room_name").toString(),
                    stage,
                    time(r.get("started_at")),
                    elapsed,
                    elapsed == null,
                    !stage.equals("EXAMINING") && elapsed != null && elapsed > limit * 60L,
                    href,
                    diagnostics
                )
            );
        }
        List<StageMetric> metrics = new ArrayList<>();
        for (String stage : List.of("PAYMENT", "DOCTOR", "EXAMINING", "DIAGNOSTICS", "REVIEW")) {
            var subset = rows
                .stream()
                .filter(r -> r.stage().equals(stage))
                .toList();
            var values = subset
                .stream()
                .map(Bottleneck::waitingSeconds)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
            metrics.add(
                new StageMetric(
                    stage,
                    subset.size(),
                    subset.stream().filter(Bottleneck::breached).count(),
                    percentile(values, .5),
                    percentile(values, .9)
                )
            );
        }
        return new Bottlenecks(clock.instant().toString(), rows, metrics);
    }

    private Double percentile(List<Long> values, double p) {
        if (values.isEmpty()) return null;
        double at = (values.size() - 1) * p;
        int low = (int) at,
            high = (int) Math.ceil(at);
        return values.get(low) + (values.get(high) - values.get(low)) * (at - low);
    }

    private record SlotFact(
        long id,
        long doctor,
        String doctorName,
        long room,
        String roomName,
        long dept,
        long service,
        String serviceName,
        Instant start,
        Instant end,
        String status,
        boolean complete
    ) {}

    private static final class Bucket {

        SlotFact slot;
        long opened, serving, closed, booked, checked, examined, minutes, bookedMinutes;
        boolean incomplete;

        Bucket(SlotFact slot) {
            this.slot = slot;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Capacity capacity(
        LocalDate from,
        LocalDate to,
        Long departmentId,
        Long doctorId,
        Long roomId,
        Long serviceId
    ) {
        require("ADMIN", "DOCTOR");
        if (!role("ADMIN")) {
            long own = staff(db);
            if (doctorId != null && doctorId != own) throw forbidden();
            doctorId = own;
        }
        LocalDate today = LocalDate.now(clock);
        if (from == null) from = today.minusDays(6);
        if (to == null) to = today;
        var window = dates(from, to);
        var slots = db.query(
            "select h.*,(not exists(select 1 from serving_slot_history baseline where baseline.slot_id=h.slot_id and baseline.history_complete=0)) complete_history,u.full_name doctor_name,r.name room_name,sv.name service_name from serving_slot_history h join staff st on st.id=h.doctor_staff_id join users u on u.id=st.user_id join rooms r on r.id=h.room_id join services sv on sv.id=h.service_id where h.starts_at>=? and h.starts_at<? and h.status in ('PUBLISHED','CLOSE_REQUESTED','CLOSED') and exists(select 1 from serving_slot_history opened where opened.slot_id=h.slot_id and opened.status in ('PUBLISHED','CLOSE_REQUESTED')) and h.id=(select max(x.id) from serving_slot_history x where x.slot_id=h.slot_id and (x.recorded_at<=h.ends_at or x.history_complete=0)) and (? is null or h.department_id=?) and (? is null or h.doctor_staff_id=?) and (? is null or h.room_id=?) and (? is null or h.service_id=?) order by h.starts_at,h.slot_id",
            (r, n) ->
                new SlotFact(
                    r.getLong("slot_id"),
                    r.getLong("doctor_staff_id"),
                    r.getString("doctor_name"),
                    r.getLong("room_id"),
                    r.getString("room_name"),
                    r.getLong("department_id"),
                    r.getLong("service_id"),
                    r.getString("service_name"),
                    instant(r.getObject("starts_at")),
                    instant(r.getObject("ends_at")),
                    r.getString("status"),
                    r.getBoolean("complete_history")
                ),
            window.from(),
            window.to(),
            departmentId,
            departmentId,
            doctorId,
            doctorId,
            roomId,
            roomId,
            serviceId,
            serviceId
        );
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (var s : slots) {
            var date = s.start().atZone(clock.getZone());
            String key =
                s.doctor() +
                ":" +
                s.room() +
                ":" +
                s.service() +
                ":" +
                date.toLocalDate() +
                ":" +
                date.getHour();
            var b = buckets.computeIfAbsent(key, k -> new Bucket(s));
            b.opened++;
            b.incomplete |= !s.complete();
            if (s.status().equals("CLOSED")) b.closed++;
            else {
                b.serving++;
                b.minutes += Duration.between(s.start(), s.end()).toMinutes();
            }
            var appointments = db.queryForList(
                "select h.status from capacity_appointment_history h where h.slot_id=? and h.id=(select max(x.id) from capacity_appointment_history x where x.appointment_id=h.appointment_id and x.recorded_at<=?) and h.status in ('CONFIRMED','CHECKED_IN','COMPLETED','NO_SHOW')",
                s.id(),
                Timestamp.from(s.end().isBefore(clock.instant()) ? s.end() : clock.instant())
            );
            // Baseline snapshots created after a historical appointment cannot prove historical utilization.
            long valid = appointments.size();
            b.booked += valid;
            if (!s.status().equals("CLOSED") && valid > 0) b.bookedMinutes += Duration.between(
                s.start(),
                s.end()
            ).toMinutes();
            b.checked += appointments
                .stream()
                .filter(a -> Set.of("CHECKED_IN", "COMPLETED").contains(a.get("status")))
                .count();
            b.examined += db.queryForObject(
                "select count(*) from capacity_exam_starts where slot_id=? and doctor_staff_id=?",
                Long.class,
                s.id(),
                s.doctor()
            );
        }
        List<CapacityRow> rows = new ArrayList<>();
        for (var b : buckets.values()) {
            var s = b.slot;
            var date = s.start().atZone(clock.getZone());
            rows.add(
                new CapacityRow(
                    s.doctor(),
                    s.doctorName(),
                    s.room(),
                    s.roomName(),
                    s.dept(),
                    s.service(),
                    s.serviceName(),
                    date.toLocalDate().toString(),
                    date.getHour(),
                    b.opened,
                    b.serving,
                    b.closed,
                    b.booked,
                    Math.max(0, b.serving - b.booked),
                    b.checked,
                    b.examined,
                    b.minutes,
                    b.bookedMinutes,
                    b.incomplete || b.serving == 0 ? null : (100d * b.booked) / b.serving,
                    b.incomplete || b.serving == 0 ? null : (100d * b.examined) / b.serving,
                    b.incomplete || b.minutes == 0 ? null : (100d * b.bookedMinutes) / b.minutes,
                    b.incomplete
                )
            );
        }
        return new Capacity(from.toString(), to.toString(), rows);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.matches("^[=+@\\-].*")) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String capacityCsv(
        LocalDate from,
        LocalDate to,
        Long departmentId,
        Long doctorId,
        Long roomId,
        Long serviceId
    ) {
        var report = capacity(from, to, departmentId, doctorId, roomId, serviceId);
        StringBuilder out = new StringBuilder("\uFEFF");
        out.append("Từ ngày,")
            .append(csv(report.from()))
            .append(",Đến ngày,")
            .append(csv(report.to()))
            .append(",Khoa,")
            .append(departmentId)
            .append(",Bác sĩ,")
            .append(doctorId)
            .append(",Phòng,")
            .append(roomId)
            .append(",Dịch vụ,")
            .append(serviceId)
            .append(
                "\r\nNgày,Giờ,Bác sĩ,Phòng,Dịch vụ,Đã mở,Phục vụ,Đóng,Lịch hẹn,Trống,Tiếp nhận,Bắt đầu khám,Phút mở,Phút đặt,Lấp lịch %,Thực khám %,Sử dụng lịch phòng %,Thiếu lịch sử\r\n"
            );
        for (var r : report.rows())
            out.append(csv(r.day()))
                .append(',')
                .append(r.hour())
                .append(',')
                .append(csv(r.doctorName()))
                .append(',')
                .append(csv(r.roomName()))
                .append(',')
                .append(csv(r.serviceName()))
                .append(',')
                .append(r.opened())
                .append(',')
                .append(r.serving())
                .append(',')
                .append(r.closed())
                .append(',')
                .append(r.booked())
                .append(',')
                .append(r.free())
                .append(',')
                .append(r.checkedIn())
                .append(',')
                .append(r.examined())
                .append(',')
                .append(r.openedMinutes())
                .append(',')
                .append(r.bookedMinutes())
                .append(',')
                .append(r.occupancy() == null ? "Chưa có lịch" : r.occupancy())
                .append(',')
                .append(r.examinationRate() == null ? "Chưa có lịch" : r.examinationRate())
                .append(',')
                .append(
                    r.roomScheduleUtilization() == null
                        ? "Chưa có lịch"
                        : r.roomScheduleUtilization()
                )
                .append(',')
                .append(r.insufficientHistory())
                .append("\r\n");
        return out.toString();
    }
}
