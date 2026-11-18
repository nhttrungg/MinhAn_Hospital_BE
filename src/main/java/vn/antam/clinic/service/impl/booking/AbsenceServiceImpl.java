package vn.antam.clinic.service.impl.booking;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.booking.*;
import vn.antam.clinic.service.dto.booking.AbsenceDtos.*;
import vn.antam.clinic.service.dto.booking.BookingDtos.AppointmentView;
import vn.antam.clinic.service.event.ClinicEventPublisher;
import vn.antam.clinic.service.notification.AppointmentNotificationService;

@Service
public class AbsenceServiceImpl implements AbsenceService {

    private final JdbcTemplate db;
    private final BookingService booking;
    private final AuditService audit;
    private final AppointmentNotificationService notifications;
    private final ClinicEventPublisher events;
    private final Clock clock;

    public AbsenceServiceImpl(
        JdbcTemplate db,
        BookingService booking,
        AuditService audit,
        AppointmentNotificationService notifications,
        ClinicEventPublisher events,
        Clock clock
    ) {
        this.db = db;
        this.booking = booking;
        this.audit = audit;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
    }

    private String time(Object v) {
        return v == null ? null : instant(v).toString();
    }

    private void validate(AbsenceInput in) {
        require("ADMIN");
        if (
            !in.startsAt().isBefore(in.endsAt()) ||
            in.endsAt().isAfter(in.startsAt().plus(Duration.ofDays(30)))
        ) throw bad("Khoảng nghỉ tối đa 30 ngày và phải tăng dần.");
        if (
            db.queryForObject(
                "select count(*) from doctor_profiles where staff_id=?",
                Integer.class,
                in.doctorId()
            ) != 1
        ) throw bad("Không có bác sĩ.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Impact> preview(AbsenceInput in) {
        validate(in);
        return impacts(in);
    }

    private List<Impact> impacts(AbsenceInput in) {
        return db.query(
            "select s.id,s.starts_at,s.ends_at,s.status,a.id appointment_id,a.appointment_code,p.full_name,a.status appointment_status from appointment_slots s left join appointments a on a.slot_id=s.id and a.status in ('CONFIRMED','NEEDS_RESCHEDULE','CHECKED_IN','COMPLETED') left join patient_profiles p on p.id=a.patient_id where s.doctor_staff_id=? and s.starts_at<? and s.ends_at>? and s.status not in ('REJECTED','CLOSED') order by s.starts_at,s.id,a.id",
            (r, n) ->
                new Impact(
                    r.getLong(1),
                    time(r.getObject(2)),
                    time(r.getObject(3)),
                    r.getString(4),
                    r.getObject(5, Long.class),
                    r.getString(6),
                    r.getString(7),
                    r.getString(8),
                    Set.of("CHECKED_IN", "COMPLETED").contains(
                        Objects.toString(r.getString(8), "")
                    ) || !instant(r.getObject(2)).isAfter(clock.instant())
                ),
            in.doctorId(),
            Timestamp.from(in.endsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)),
            Timestamp.from(in.startsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
        );
    }

    private Absence absence(long id) {
        return db
            .query(
                "select a.*,u.full_name doctor_name,actor.full_name actor_name,((select count(*) from appointment_coordination c where c.absence_id=a.id and c.resolution='PENDING')+(select count(*) from appointment_coordination_archive c where c.absence_id=a.id and c.resolution='PENDING')) pending,((select count(*) from appointment_coordination c where c.absence_id=a.id and c.resolution='RESCHEDULED')+(select count(*) from appointment_coordination_archive c where c.absence_id=a.id and c.resolution='RESCHEDULED')) rescheduled,((select count(*) from appointment_coordination c where c.absence_id=a.id and c.resolution='CANCELLED')+(select count(*) from appointment_coordination_archive c where c.absence_id=a.id and c.resolution='CANCELLED')) cancelled from doctor_absences a join staff s on s.id=a.doctor_staff_id join users u on u.id=s.user_id join users actor on actor.id=a.actor_user_id where a.id=?",
                (r, n) ->
                    new Absence(
                        r.getLong("id"),
                        r.getLong("doctor_staff_id"),
                        r.getString("doctor_name"),
                        time(r.getObject("starts_at")),
                        time(r.getObject("ends_at")),
                        r.getString("reason"),
                        r.getString("actor_name"),
                        time(r.getObject("created_at")),
                        r.getLong("pending"),
                        r.getLong("rescheduled"),
                        r.getLong("cancelled")
                    ),
                id
            )
            .getFirst();
    }

    @Override
    @Transactional
    public AbsenceConfirmed confirm(AbsenceInput in) {
        validate(in);
        db.queryForList("select id from staff where id=? for update", in.doctorId());
        var previous = db.queryForList(
            "select id,doctor_staff_id,starts_at,ends_at,reason from doctor_absences where actor_user_id=? and request_id=?",
            user(),
            in.requestId()
        );
        if (!previous.isEmpty()) {
            var p = previous.getFirst();
            if (
                ((Number) p.get("doctor_staff_id")).longValue() != in.doctorId() ||
                !instant(p.get("starts_at")).equals(
                    in.startsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                ) ||
                !instant(p.get("ends_at")).equals(
                    in.endsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                ) ||
                !p.get("reason").equals(in.reason())
            ) throw conflict("Mã xác nhận đã dùng cho một đợt nghỉ khác.");
            return new AbsenceConfirmed(
                absence(((Number) p.get("id")).longValue()),
                impacts(in).stream().filter(Impact::separateHandling).toList()
            );
        }
        db.queryForList(
            "select id from appointment_slots where doctor_staff_id=? and starts_at<? and ends_at>? order by id for update",
            in.doctorId(),
            Timestamp.from(in.endsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)),
            Timestamp.from(in.startsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
        );
        var impact = impacts(in);
        long id = insert(
            db,
            "insert into doctor_absences(doctor_staff_id,starts_at,ends_at,reason,actor_user_id,request_id) values(?,?,?,?,?,?)",
            in.doctorId(),
            Timestamp.from(in.startsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)),
            Timestamp.from(in.endsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)),
            in.reason(),
            user(),
            in.requestId()
        );
        Set<Long> excluded = new HashSet<>();
        for (var item : impact) if (item.separateHandling()) excluded.add(item.slotId());
        Set<Long> closed = new HashSet<>();
        for (var item : impact) {
            if (excluded.contains(item.slotId())) continue;
            if (closed.add(item.slotId())) db.update(
                "update appointment_slots set status='CLOSED',note=? where id=?",
                in.reason(),
                item.slotId()
            );
            if (item.appointmentId() != null) {
                db.queryForList(
                    "select id from appointments where id=? for update",
                    item.appointmentId()
                );
                if (
                    db.update(
                        "update appointments set status='NEEDS_RESCHEDULE',revision=revision+1 where id=? and status='CONFIRMED'",
                        item.appointmentId()
                    ) == 1
                ) {
                    db.update(
                        "insert ignore into appointment_coordination_archive select * from appointment_coordination where appointment_id=? and resolution<>'PENDING'",
                        item.appointmentId()
                    );
                    db.update(
                        "insert into appointment_coordination(appointment_id,absence_id,old_slot_id) values(?,?,?) on duplicate key update absence_id=values(absence_id),old_slot_id=values(old_slot_id),contact_status='NOT_CONTACTED',contact_note=null,resolution='PENDING',agreed_at=null,agreed_by=null,replacement_slot_id=null,resolved_at=null,resolved_by=null,revision=revision+1",
                        item.appointmentId(),
                        id,
                        item.slotId()
                    );
                    db.update(
                        "delete from slot_reservations where appointment_id=?",
                        item.appointmentId()
                    );
                    db.update(
                        "insert into appointment_changes(appointment_id,old_slot_id,actor_user_id,action) values(?,?,?,'NEEDS_RESCHEDULE')",
                        item.appointmentId(),
                        item.slotId(),
                        user()
                    );
                    notifications.enqueue(
                        item.appointmentId(),
                        "Bác sĩ nghỉ: lễ tân sẽ liên hệ đổi lịch"
                    );
                    events.queueUpdated(item.appointmentId());
                }
            }
        }
        audit.record("CONFIRM", "DOCTOR_ABSENCE", String.valueOf(id), in.reason());
        return new AbsenceConfirmed(
            absence(id),
            impact.stream().filter(Impact::separateHandling).toList()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Absence> absences() {
        require("ADMIN", "RECEPTIONIST");
        return db.query("select id from doctor_absences order by id desc limit 100", (r, n) ->
            absence(r.getLong(1))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Coordination> coordination(Long absenceId) {
        require("ADMIN", "RECEPTIONIST");
        return db.query(
            "select c.*,a.appointment_code,p.full_name patient_name,sl.starts_at,u.full_name doctor_name,agree.full_name agreed_name,resolve.full_name resolved_name,exists(select 1 from invoices i where i.appointment_id=a.id and i.status in ('PARTIAL','PAID') and (select coalesce(sum(r.amount),0) from receipts r where r.invoice_id=i.id)>(select coalesce(sum(f.amount),0) from refunds f where f.invoice_id=i.id)) financial from (select * from appointment_coordination union all select * from appointment_coordination_archive) c join appointments a on a.id=c.appointment_id join patient_profiles p on p.id=a.patient_id join appointment_slots sl on sl.id=c.old_slot_id join staff st on st.id=sl.doctor_staff_id join users u on u.id=st.user_id left join users agree on agree.id=c.agreed_by left join users resolve on resolve.id=c.resolved_by where (? is null or c.absence_id=?) order by c.absence_id desc,c.resolution,sl.starts_at,a.id",
            (r, n) ->
                new Coordination(
                    r.getLong("appointment_id"),
                    r.getLong("absence_id"),
                    r.getString("appointment_code"),
                    r.getString("patient_name"),
                    time(r.getObject("starts_at")),
                    r.getString("doctor_name"),
                    r.getString("contact_status"),
                    r.getString("contact_note"),
                    r.getString("resolution"),
                    r.getInt("revision"),
                    time(r.getObject("agreed_at")),
                    r.getString("agreed_name"),
                    time(r.getObject("resolved_at")),
                    r.getString("resolved_name"),
                    r.getBoolean("financial")
                ),
            absenceId,
            absenceId
        );
    }

    private Coordination coordination(long id) {
        return coordination((Long) null)
            .stream()
            .filter(c -> c.appointmentId() == id)
            .findFirst()
            .orElseThrow(() -> missing());
    }

    @Override
    @Transactional
    public Coordination contact(long id, ContactInput in) {
        require("RECEPTIONIST");
        if (
            !Set.of("NOT_CONTACTED", "UNREACHABLE", "CHOOSING", "AGREED").contains(in.status())
        ) throw bad("Trạng thái liên hệ không hợp lệ.");
        if (in.status().equals("AGREED") && (in.note() == null || in.note().isBlank())) throw bad(
            "Ghi nhận nội dung bệnh nhân đã đồng ý."
        );
        if (
            db.update(
                "update appointment_coordination set contact_status=?,contact_note=?,agreed_at=?,agreed_by=?,revision=revision+1 where appointment_id=? and revision=? and resolution='PENDING'",
                in.status(),
                in.note(),
                in.status().equals("AGREED") ? Timestamp.from(clock.instant()) : null,
                in.status().equals("AGREED") ? user() : null,
                id,
                in.revision()
            ) != 1
        ) throw conflict("Hồ sơ điều phối đã thay đổi.");
        audit.record(
            "CONTACT",
            "APPOINTMENT_COORDINATION",
            String.valueOf(id),
            in.status() + ": " + in.note()
        );
        return coordination(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> candidates(long id, int days) {
        require("RECEPTIONIST", "ADMIN");
        if (days != 7 && days != 30) throw bad("Tìm trong 7 hoặc 30 ngày.");
        var c = coordination(id);
        if (!c.resolution().equals("PENDING")) throw conflict("Lịch đã được xử lý.");
        return candidateRows(id, days, null);
    }

    private List<Candidate> candidateRows(long id, int days, Long slot) {
        return db.query(
            "select s.id,s.doctor_staff_id,u.full_name,s.room_id,r.name,s.starts_at,s.ends_at,s.service_id,sv.name service_name,(select count(*) from appointment_slots x join slot_reservations sr on sr.slot_id=x.id where x.doctor_staff_id=s.doctor_staff_id and date(x.starts_at + interval 7 hour)=date(s.starts_at + interval 7 hour))/(select greatest(count(*),1) from appointment_slots x where x.doctor_staff_id=s.doctor_staff_id and x.status='PUBLISHED' and date(x.starts_at + interval 7 hour)=date(s.starts_at + interval 7 hour)) occupancy from appointments a join appointment_coordination c on c.appointment_id=a.id join appointment_slots old on old.id=c.old_slot_id join doctor_profiles od on od.staff_id=old.doctor_staff_id join appointment_slots s on s.service_id=old.service_id join doctor_profiles dp on dp.staff_id=s.doctor_staff_id and dp.specialty_id=od.specialty_id join specialties sp on sp.id=dp.specialty_id join departments dept on dept.id=sp.department_id join staff st on st.id=s.doctor_staff_id join users u on u.id=st.user_id join rooms r on r.id=s.room_id join services sv on sv.id=s.service_id where a.id=? and s.status='PUBLISHED' and s.starts_at>? and s.starts_at<=? and st.active=1 and u.status='ACTIVE' and r.active=1 and sv.active=1 and sp.active=1 and dept.active=1 and exists(select 1 from doctor_services ds where ds.doctor_staff_id=s.doctor_staff_id and ds.service_id=s.service_id) and exists(select 1 from user_roles ur join roles rl on rl.id=ur.role_id where ur.user_id=u.id and rl.code='DOCTOR') and (? is null or s.id=?) and not exists(select 1 from doctor_absences da where da.doctor_staff_id=s.doctor_staff_id and da.starts_at<s.ends_at and da.ends_at>s.starts_at) and not exists(select 1 from slot_reservations sr where sr.slot_id=s.id) and not exists(select 1 from appointments other join appointment_slots os on os.id=other.slot_id where other.patient_id=a.patient_id and other.id<>a.id and other.status in ('CONFIRMED','CHECKED_IN') and os.starts_at<s.ends_at and os.ends_at>s.starts_at) order by (date(s.starts_at + interval 7 hour)=date(old.starts_at + interval 7 hour)) desc,abs(time_to_sec(time(old.starts_at))-time_to_sec(time(s.starts_at))),s.starts_at,occupancy,s.id limit 100",
            (r, n) ->
                new Candidate(
                    r.getLong(1),
                    r.getLong(2),
                    r.getString(3),
                    r.getLong(4),
                    r.getString(5),
                    time(r.getObject(6)),
                    time(r.getObject(7)),
                    r.getLong(8),
                    r.getString(9),
                    r.getDouble(10)
                ),
            id,
            Timestamp.from(clock.instant()),
            Timestamp.from(clock.instant().plus(Duration.ofDays(days))),
            slot,
            slot
        );
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AppointmentView finalizeReplacement(long id, FinalizeInput in) {
        require("RECEPTIONIST");
        db.queryForList("select id from users where id=? for update", user());
        var c = coordination(id);
        // BookingService persists its own idempotency result. A replay must use the same target.
        var previous = db.queryForList(
            "select result_id from idempotency_results where actor_user_id=? and operation='RESCHEDULE' and request_key=?",
            user(),
            in.requestId()
        );
        if (!previous.isEmpty()) return booking.reschedule(id, in.slotId(), in.requestId());
        if (!in.patientAgreed() || !c.contactStatus().equals("AGREED")) throw bad(
            "Lễ tân cần ghi nhận bệnh nhân đồng ý trước khi chốt."
        );
        if (c.revision() != in.revision() || !c.resolution().equals("PENDING")) throw conflict(
            "Hồ sơ điều phối đã thay đổi."
        );
        if (c.paymentReviewRequired()) throw conflict(
            "Có thanh toán cần đối chiếu; chuyển thu ngân xử lý trước."
        );
        if (candidateRows(id, 30, in.slotId()).isEmpty()) throw conflict(
            "Phương án không còn hợp lệ; tìm lại lịch còn trống."
        );
        var appointment = booking.rescheduleReplacement(
            id,
            in.slotId(),
            in.requestId(),
            in.revision()
        );
        audit.record(
            "FINALIZE_REPLACEMENT",
            "APPOINTMENT_COORDINATION",
            String.valueOf(id),
            "Bệnh nhân đã đồng ý"
        );
        return appointment;
    }
}
