package vn.antam.clinic.service.impl.booking;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.PatientAccess;
import vn.antam.clinic.service.booking.WaitlistService;
import vn.antam.clinic.service.dto.booking.WaitlistDtos.*;
import vn.antam.clinic.service.notification.AppointmentNotificationService;

@Service
public class WaitlistServiceImpl implements WaitlistService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final PatientAccess access;
    private final AppointmentNotificationService notices;
    private final Clock clock;

    public WaitlistServiceImpl(
        JdbcTemplate jdbc,
        PatientAccess access,
        AppointmentNotificationService notices,
        Clock clock
    ) {
        this.jdbc = jdbc;
        this.access = access;
        this.notices = notices;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Entry join(JoinRequest request) {
        require("PATIENT");
        jdbc.queryForList("select id from users where id=? for update", user());
        long patient =
            request.patientId() == null ? access.primaryPatientId(user()) : request.patientId();
        jdbc.queryForList("select id from patient_profiles where id=? for update", patient);
        access.requireOwn(user(), patient);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        if (
            request.dateFrom() == null ||
            request.dateTo() == null ||
            request.dateFrom().isBefore(today) ||
            request.dateTo().isBefore(request.dateFrom()) ||
            request.dateTo().isAfter(request.dateFrom().plusDays(31))
        ) throw bad("Chọn khoảng ngày trong tương lai, tối đa 32 ngày.");
        if (
            request.serviceId() != null &&
            jdbc.queryForObject(
                "select count(*) from services where id=? and active=1 and category='EXAMINATION'",
                Integer.class,
                request.serviceId()
            ) == 0
        ) throw bad("Dịch vụ không hợp lệ.");
        if (
            request.specialtyId() != null &&
            jdbc.queryForObject(
                "select count(*) from specialties sp join departments d on d.id=sp.department_id where sp.id=? and sp.active=1 and d.active=1",
                Integer.class,
                request.specialtyId()
            ) == 0
        ) throw bad("Chuyên khoa không hợp lệ.");
        if (
            request.doctorId() != null &&
            jdbc.queryForObject(
                "select count(*) from doctor_profiles dp join staff st on st.id=dp.staff_id join users u on u.id=st.user_id join specialties sp on sp.id=dp.specialty_id join departments d on d.id=sp.department_id where dp.staff_id=? and st.active=1 and u.status='ACTIVE' and sp.active=1 and d.active=1 and (? is null or dp.specialty_id=?) and (? is null or exists(select 1 from doctor_services ds where ds.doctor_staff_id=dp.staff_id and ds.service_id=?)) and exists(select 1 from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=u.id and r.code='DOCTOR')",
                Integer.class,
                request.doctorId(),
                request.specialtyId(),
                request.specialtyId(),
                request.serviceId(),
                request.serviceId()
            ) == 0
        ) throw bad("Bác sĩ không phù hợp với bộ lọc.");
        var duplicates = jdbc.queryForList(
            "select id from waitlist_entries where patient_id=? and status in ('ACTIVE','NOTIFIED') and service_id<=>? and specialty_id<=>? and doctor_staff_id<=>? and date_from=? and date_to=?",
            patient,
            request.serviceId(),
            request.specialtyId(),
            request.doctorId(),
            Date.valueOf(request.dateFrom()),
            Date.valueOf(request.dateTo())
        );
        if (!duplicates.isEmpty()) return find(
            ((Number) duplicates.getFirst().get("id")).longValue()
        );
        int active = jdbc.queryForObject(
            "select count(*) from waitlist_entries w join patient_profiles p on p.id=w.patient_id where w.status in ('ACTIVE','NOTIFIED') and " +
                PatientAccess.ownedSql("p"),
            Integer.class,
            user(),
            user()
        );
        if (active >= 3) throw conflict(
            "Tối đa 3 đăng ký chờ đang hoạt động cho tài khoản và người thân."
        );
        long id = insert(
            jdbc,
            "insert into waitlist_entries(patient_id,service_id,specialty_id,doctor_staff_id,date_from,date_to) values(?,?,?,?,?,?)",
            patient,
            request.serviceId(),
            request.specialtyId(),
            request.doctorId(),
            Date.valueOf(request.dateFrom()),
            Date.valueOf(request.dateTo())
        );
        return find(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Entry> mine() {
        require("PATIENT");
        return jdbc.query(
            "select w.id from waitlist_entries w join patient_profiles p on p.id=w.patient_id where " +
                PatientAccess.ownedSql("p") +
                " order by w.id desc limit 100",
            (r, n) -> find(r.getLong(1)),
            user(),
            user()
        );
    }

    @Override
    @Transactional
    public void cancel(long id) {
        require("PATIENT");
        var rows = jdbc.queryForList(
            "select patient_id,status from waitlist_entries where id=? for update",
            id
        );
        if (rows.isEmpty()) throw missing();
        access.requireOwn(user(), ((Number) rows.getFirst().get("patient_id")).longValue());
        String state = String.valueOf(rows.getFirst().get("status"));
        if (state.equals("CANCELLED")) return;
        if (!List.of("ACTIVE", "NOTIFIED").contains(state)) throw conflict(
            "Đăng ký chờ đã kết thúc."
        );
        jdbc.update("update waitlist_entries set status='CANCELLED' where id=?", id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void slotFreed(long slotId) {
        var changes = jdbc.queryForList(
            "select id from appointment_changes where old_slot_id=? and action in ('CANCEL','RESCHEDULE') order by id desc limit 1",
            slotId
        );
        String key =
            "slot:" +
            slotId +
            ":change:" +
            (changes.isEmpty() ? "initial" : changes.getFirst().get("id"));
        slotFreed(slotId, key);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void slotFreed(long slotId, String releaseKey) {
        var slots = jdbc.queryForList(
            "select sl.*,dp.specialty_id from appointment_slots sl join doctor_profiles dp on dp.staff_id=sl.doctor_staff_id where sl.id=? for update",
            slotId
        );
        if (slots.isEmpty()) return;
        var slot = slots.getFirst();
        var start = instant(slot.get("starts_at"));
        if (
            !"PUBLISHED".equals(slot.get("status")) ||
            !start.isAfter(clock.instant()) ||
            jdbc.queryForObject(
                "select count(*) from slot_reservations where slot_id=?",
                Integer.class,
                slotId
            ) > 0
        ) return;
        // Apply the same inactive doctor/room/service restrictions as booking search.
        if (
            jdbc.queryForObject(
                "select count(*) from appointment_slots sl join staff st on st.id=sl.doctor_staff_id join users u on u.id=st.user_id join services sv on sv.id=sl.service_id join rooms rm on rm.id=sl.room_id join doctor_profiles dp on dp.staff_id=st.id join specialties sp on sp.id=dp.specialty_id join departments d on d.id=sp.department_id where sl.id=? and st.active=1 and u.status='ACTIVE' and sv.active=1 and sv.category='EXAMINATION' and rm.active=1 and rm.room_type='CLINIC' and sp.active=1 and d.active=1 and exists(select 1 from doctor_services ds where ds.doctor_staff_id=st.id and ds.service_id=sv.id) and exists(select 1 from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=u.id and r.code='DOCTOR')",
                Integer.class,
                slotId
            ) == 0
        ) return;
        if (
            jdbc.update(
                "insert ignore into waitlist_slot_releases(release_key,slot_id) values(?,?)",
                releaseKey,
                slotId
            ) == 0
        ) return;
        Date day = Date.valueOf(start.atZone(ZONE).toLocalDate());
        var entries = jdbc.queryForList(
            "select w.id,w.patient_id from waitlist_entries w where w.status='ACTIVE' and exists(select 1 from patient_profiles p where p.id=w.patient_id and p.active=1) and w.date_from<=? and w.date_to>=? and (w.service_id is null or w.service_id=?) and (w.specialty_id is null or w.specialty_id=?) and (w.doctor_staff_id is null or w.doctor_staff_id=?) order by w.created_at,w.id limit 3 for update skip locked",
            day,
            day,
            slot.get("service_id"),
            slot.get("specialty_id"),
            slot.get("doctor_staff_id")
        );
        for (var entry : entries) {
            long id = ((Number) entry.get("id")).longValue();
            long patient = ((Number) entry.get("patient_id")).longValue();
            Long recipient = access.recipientUserId(patient);
            jdbc.update(
                "update waitlist_entries set status='NOTIFIED',notified_at=?,notified_slot_id=? where id=? and status='ACTIVE'",
                Timestamp.from(clock.instant()),
                slotId,
                id
            );
            if (recipient != null) notices.notify(
                recipient,
                "WAITLIST",
                "Đã có chỗ khám phù hợp",
                "Có giờ khám phù hợp ngày " + day + ". Chỗ chưa được giữ; vui lòng đặt lịch sớm.",
                "/booking?slotId=" +
                    slotId +
                    "&doctorId=" +
                    slot.get("doctor_staff_id") +
                    "&serviceId=" +
                    slot.get("service_id") +
                    "&date=" +
                    day +
                    "&patientId=" +
                    patient,
                "waitlist:" + id
            );
        }
    }

    /** Called inside the booking transaction, including reschedules. */
    @Override
    @Transactional
    public void booked(long patient, long slotId) {
        jdbc.update(
            "update waitlist_entries w join appointment_slots sl on sl.id=? join doctor_profiles dp on dp.staff_id=sl.doctor_staff_id set w.status='BOOKED' where w.patient_id=? and w.status in ('ACTIVE','NOTIFIED') and w.date_from<=date(convert_tz(sl.starts_at,'+00:00','+07:00')) and w.date_to>=date(convert_tz(sl.starts_at,'+00:00','+07:00')) and (w.service_id is null or w.service_id=sl.service_id) and (w.specialty_id is null or w.specialty_id=dp.specialty_id) and (w.doctor_staff_id is null or w.doctor_staff_id=sl.doctor_staff_id)",
            slotId,
            patient
        );
    }

    @Override
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "waitlist-expiry",
        lockAtMostFor = "30m",
        lockAtLeastFor = "2s"
    )
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void expire() {
        jdbc.update(
            "update waitlist_entries set status='EXPIRED' where status in ('ACTIVE','NOTIFIED') and date_to<?",
            Date.valueOf(LocalDate.now(clock.withZone(ZONE)))
        );
    }

    private Entry find(long id) {
        return jdbc.queryForObject(
            "select w.*,p.full_name patient_name,sv.name service_name,sp.name specialty_name,u.full_name doctor_name from waitlist_entries w join patient_profiles p on p.id=w.patient_id left join services sv on sv.id=w.service_id left join specialties sp on sp.id=w.specialty_id left join staff st on st.id=w.doctor_staff_id left join users u on u.id=st.user_id where w.id=?",
            (r, n) ->
                new Entry(
                    r.getLong("id"),
                    r.getLong("patient_id"),
                    r.getString("patient_name"),
                    r.getObject("service_id", Long.class),
                    r.getString("service_name"),
                    r.getObject("specialty_id", Long.class),
                    r.getString("specialty_name"),
                    r.getObject("doctor_staff_id", Long.class),
                    r.getString("doctor_name"),
                    r.getDate("date_from").toLocalDate(),
                    r.getDate("date_to").toLocalDate(),
                    r.getString("status"),
                    r.getTimestamp("notified_at") == null
                        ? null
                        : r.getTimestamp("notified_at").toInstant(),
                    r.getObject("notified_slot_id", Long.class),
                    r.getTimestamp("created_at").toInstant()
                ),
            id
        );
    }
}
