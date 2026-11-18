package vn.antam.clinic.service.impl.booking;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.domain.event.ClinicEvent;
import vn.antam.clinic.security.ClinicUserPrincipal;
import vn.antam.clinic.security.PatientAccess;
import vn.antam.clinic.security.SecurityUtils;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.booking.BookingService;
import vn.antam.clinic.service.booking.WaitlistService;
import vn.antam.clinic.service.dto.booking.BookingDtos.AppointmentView;
import vn.antam.clinic.service.dto.booking.BookingDtos.BookRequest;
import vn.antam.clinic.service.dto.booking.BookingDtos.CreateSlotRequest;
import vn.antam.clinic.service.dto.booking.BookingDtos.DoctorView;
import vn.antam.clinic.service.dto.booking.BookingDtos.SlotView;
import vn.antam.clinic.service.dto.booking.BookingDtos.SpecialtyView;
import vn.antam.clinic.service.event.ClinicEventPublisher;
import vn.antam.clinic.service.notification.AppointmentNotificationService;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
public class BookingServiceImpl implements BookingService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;
    private final AppointmentNotificationService notifications;
    private final PatientAccess access;
    private final WaitlistService waitlist;
    private final ClinicEventPublisher clinicEvents;
    private final ApplicationEventPublisher events;

    public BookingServiceImpl(
        JdbcTemplate jdbc,
        AuditService audit,
        Clock clock,
        AppointmentNotificationService notifications,
        PatientAccess access,
        WaitlistService waitlist,
        ClinicEventPublisher clinicEvents,
        ApplicationEventPublisher events
    ) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
        this.notifications = notifications;
        this.access = access;
        this.waitlist = waitlist;
        this.clinicEvents = clinicEvents;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(cacheNames = "catalog.specialties", sync = true)
    public List<SpecialtyView> specialties() {
        return jdbc.query(
            "select sp.id,sp.code,sp.name,sp.description from specialties sp join departments d on d.id=sp.department_id where sp.active=1 and d.active=1 order by sp.name",
            (rs, row) ->
                new SpecialtyView(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("description")
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(cacheNames = "doctors.public", sync = true)
    public List<DoctorView> doctors(Long specialtyId) {
        return jdbc.query(
            """
            select s.id, u.full_name, s.title, sp.id specialty_id, sp.name specialty_name,
                   dp.bio, dp.photo_url, dp.gender, (select avg(er.rating) from encounter_reviews er where er.doctor_staff_id=s.id and er.visible=1) rating_average, (select count(*) from encounter_reviews er where er.doctor_staff_id=s.id and er.visible=1) rating_count, sv.id service_id, sv.name service_name, sv.description service_description,
                   (select p.unit_price from service_prices p where p.service_id=sv.id
                    and p.effective_from<=? and (p.effective_to is null or p.effective_to>=?)
                    order by p.effective_from desc limit 1) price
            from doctor_profiles dp join staff s on s.id=dp.staff_id
            join users u on u.id=s.user_id join specialties sp on sp.id=dp.specialty_id
            join doctor_services ds on ds.doctor_staff_id=s.id
            join services sv on sv.id=ds.service_id
            where s.active=1 and u.status='ACTIVE' and sp.active=1 and sv.active=1 and exists (select 1 from departments d where d.id=sp.department_id and d.active=1) and exists (select 1 from user_roles ur join roles rl on rl.id=ur.role_id where ur.user_id=u.id and rl.code='DOCTOR')
              and (? is null or sp.id=?)
            order by u.full_name, sv.name
            """,
            (rs, row) ->
                new DoctorView(
                    rs.getLong("id"),
                    rs.getString("full_name"),
                    rs.getString("title"),
                    rs.getLong("specialty_id"),
                    rs.getString("specialty_name"),
                    rs.getString("bio"),
                    rs.getLong("service_id"),
                    rs.getString("service_name"),
                    money(rs.getBigDecimal("price")),
                    rs.getString("service_description"),
                    rs.getString("photo_url"),
                    rs.getString("gender"),
                    rs.getBigDecimal("rating_average"),
                    rs.getLong("rating_count")
                ),
            today(),
            today(),
            specialtyId,
            specialtyId
        );
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(cacheNames = "availableSlots", sync = true)
    public List<SlotView> availableSlots(
        Instant from,
        Instant to,
        Long doctorId,
        Long serviceId,
        Long specialtyId
    ) {
        return availableSlots(from, to, doctorId, serviceId, specialtyId, null);
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(cacheNames = "availableSlots", sync = true)
    public List<SlotView> availableSlots(
        Instant from,
        Instant to,
        Long doctorId,
        Long serviceId,
        Long specialtyId,
        String gender
    ) {
        if (
            gender != null && !java.util.Set.of("MALE", "FEMALE", "OTHER").contains(gender)
        ) throw bad("Bộ lọc giới tính không hợp lệ.");
        if (
            from == null ||
            to == null ||
            !from.isBefore(to) ||
            to.isAfter(from.plusSeconds(32L * 86400))
        ) {
            throw bad("Chọn khoảng tìm kiếm tối đa 32 ngày.");
        }
        return jdbc.query(
            slotSelect() +
                " where sl.status='PUBLISHED' and sl.starts_at>=? and sl.starts_at<? " +
                "and sl.starts_at>? and r.slot_id is null and st.active=1 and u.status='ACTIVE' " +
                "and rm.active=1 and rm.room_type='CLINIC' and sv.active=1 and sv.category='EXAMINATION' and exists (select 1 from user_roles ur join roles rl on rl.id=ur.role_id where ur.user_id=u.id and rl.code='DOCTOR') and exists (select 1 from doctor_services ds where ds.doctor_staff_id=st.id and ds.service_id=sv.id) and (? is null or sl.doctor_staff_id=?) " +
                "and (? is null or sl.service_id=?) " +
                "and exists (select 1 from doctor_profiles dp join specialties sp on sp.id=dp.specialty_id " +
                "where dp.staff_id=sl.doctor_staff_id and sp.active=1 and exists(select 1 from departments d where d.id=sp.department_id and d.active=1) and (? is null or sp.id=?) and (? is null or dp.gender=?)) " +
                "order by sl.starts_at, u.full_name limit 300",
            (rs, row) -> slot(rs),
            today(),
            today(),
            ts(from),
            ts(to),
            ts(now()),
            doctorId,
            doctorId,
            serviceId,
            serviceId,
            specialtyId,
            specialtyId,
            gender,
            gender
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotView> managedSlots() {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        boolean admin = role(actor, "ADMIN");
        if (!admin && !role(actor, "DOCTOR")) throw forbidden();
        Long doctor = admin ? null : staffId(actor.getId());
        return jdbc.query(
            slotSelect() +
                " where (? is null or sl.doctor_staff_id=?) " +
                "and sl.starts_at>=? order by sl.starts_at limit 300",
            (rs, row) -> slot(rs),
            today(),
            today(),
            doctor,
            doctor,
            ts(now().minusSeconds(86400))
        );
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SlotView createSlot(CreateSlotRequest request) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "DOCTOR")) throw forbidden();
        long doctor = staffId(actor.getId());
        if (
            request.startsAt() == null ||
            request.endsAt() == null ||
            !request.startsAt().isAfter(now()) ||
            !request.startsAt().isBefore(request.endsAt()) ||
            request.endsAt().isAfter(request.startsAt().plusSeconds(8 * 3600L))
        ) {
            throw bad("Khoảng khám phải ở tương lai, có độ dài từ trên 0 đến 8 giờ.");
        }
        lock("select id from staff where id=? and active=1 for update", doctor);
        lock(
            "select id from rooms where id=? and room_type='CLINIC' and active=1 for update",
            request.roomId()
        );
        assertDoctorService(doctor, request.serviceId());
        Integer allowed = jdbc.queryForObject(
            "select count(*) from doctor_services ds join services sv on sv.id=ds.service_id " +
                "where ds.doctor_staff_id=? and ds.service_id=? and sv.active=1 and sv.category='EXAMINATION'",
            Integer.class,
            doctor,
            request.serviceId()
        );
        if (allowed == null || allowed == 0) throw bad("Bác sĩ không thực hiện dịch vụ khám này.");
        assertNoOverlap(doctor, request.roomId(), request.startsAt(), request.endsAt(), -1);
        long id = insert(
            "insert into appointment_slots(doctor_staff_id,room_id,service_id,starts_at,ends_at,status,note) " +
                "values(?,?,?,?,?,'DRAFT',?)",
            doctor,
            request.roomId(),
            request.serviceId(),
            ts(request.startsAt()),
            ts(request.endsAt()),
            request.note()
        );
        audit.record("CREATE", "APPOINTMENT_SLOT", String.valueOf(id), null);
        return findSlot(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SlotView submitSlot(long id) {
        SlotView slot = findSlot(id);
        requireDoctor(slot.doctorId());
        lock("select id from staff where id=? and active=1 for update", slot.doctorId());
        lock("select id from rooms where id=? and active=1 for update", slot.roomId());
        slot = lockSlot(id);
        if (!slot.status().equals("DRAFT") && !slot.status().equals("REJECTED")) throw conflict(
            "Chỉ gửi được lịch nháp hoặc bị từ chối."
        );
        assertDoctorService(slot.doctorId(), slot.serviceId());
        if (!slot.startsAt().isAfter(now())) throw conflict("Lịch đã qua giờ bắt đầu.");
        assertNoOverlap(slot.doctorId(), slot.roomId(), slot.startsAt(), slot.endsAt(), id);
        jdbc.update(
            "update appointment_slots set status='SUBMITTED', rejection_reason=null where id=?",
            id
        );
        audit.record("SUBMIT", "APPOINTMENT_SLOT", String.valueOf(id), null);
        return findSlot(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SlotView editSlot(long id, CreateSlotRequest request) {
        SlotView before = findSlot(id);
        requireDoctor(before.doctorId());
        if (
            !request.startsAt().isAfter(now()) ||
            !request.startsAt().isBefore(request.endsAt()) ||
            request.endsAt().isAfter(request.startsAt().plusSeconds(8 * 3600L))
        ) throw bad("Khoảng khám phải ở tương lai, dài trên 0 và không quá 8 giờ.");
        lock("select id from staff where id=? and active=1 for update", before.doctorId());
        for (long room : new long[] {
            Math.min(before.roomId(), request.roomId()),
            Math.max(before.roomId(), request.roomId()),
        })
            lock(
                "select id from rooms where id=? and active=1 and room_type='CLINIC' for update",
                room
            );
        before = lockSlot(id);
        if (!List.of("DRAFT", "REJECTED").contains(before.status())) throw conflict(
            "Chỉ sửa lịch nháp hoặc bị từ chối."
        );
        if (
            jdbc.queryForObject(
                "select count(*) from doctor_services ds join services sv on sv.id=ds.service_id where ds.doctor_staff_id=? and ds.service_id=? and sv.active=1 and sv.category='EXAMINATION'",
                Integer.class,
                before.doctorId(),
                request.serviceId()
            ) == 0
        ) throw bad("Dịch vụ không phù hợp.");
        assertNoOverlap(
            before.doctorId(),
            request.roomId(),
            request.startsAt(),
            request.endsAt(),
            id
        );
        jdbc.update(
            "update appointment_slots set room_id=?,service_id=?,starts_at=?,ends_at=?,note=?,status='DRAFT',rejection_reason=null where id=?",
            request.roomId(),
            request.serviceId(),
            ts(request.startsAt()),
            ts(request.endsAt()),
            request.note(),
            id
        );
        audit.record("UPDATE", "APPOINTMENT_SLOT", String.valueOf(id), null);
        return findSlot(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteSlot(long id) {
        SlotView slot = lockSlot(id);
        requireDoctor(slot.doctorId());
        if (!List.of("DRAFT", "REJECTED").contains(slot.status())) throw conflict(
            "Chỉ xóa lịch nháp hoặc bị từ chối."
        );
        jdbc.update("delete from appointment_slots where id=?", id);
        audit.record("DELETE", "APPOINTMENT_SLOT", String.valueOf(id), null);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SlotView rejectClose(long id, String reason) {
        requireAdmin();
        SlotView slot = lockSlot(id);
        if (!slot.status().equals("CLOSE_REQUESTED")) throw conflict("Chưa có yêu cầu nghỉ.");
        jdbc.update(
            "update appointment_slots set status='PUBLISHED',rejection_reason=? where id=?",
            reason,
            id
        );
        audit.record("REJECT_CLOSE", "APPOINTMENT_SLOT", String.valueOf(id), reason);
        return findSlot(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SlotView approveSlot(long id) {
        requireAdmin();
        SlotView slot = findSlot(id);
        lock("select id from staff where id=? for update", slot.doctorId());
        lock("select id from rooms where id=? and active=1 for update", slot.roomId());
        slot = lockSlot(id);
        if (!slot.status().equals("SUBMITTED")) throw conflict("Lịch chưa ở trạng thái chờ duyệt.");
        assertDoctorService(slot.doctorId(), slot.serviceId());
        if (
            jdbc.queryForObject(
                "select count(*) from doctor_absences where doctor_staff_id=? and starts_at<? and ends_at>?",
                Integer.class,
                slot.doctorId(),
                ts(slot.endsAt()),
                ts(slot.startsAt())
            ) > 0
        ) throw conflict("Bác sĩ nghỉ trong khoảng giờ này.");
        if (!slot.startsAt().isAfter(now())) throw conflict("Lịch đã qua giờ bắt đầu.");
        assertNoOverlap(slot.doctorId(), slot.roomId(), slot.startsAt(), slot.endsAt(), id);
        jdbc.update("update appointment_slots set status='PUBLISHED' where id=?", id);
        audit.record("APPROVE", "APPOINTMENT_SLOT", String.valueOf(id), null);
        return findSlot(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SlotView rejectSlot(long id, String reason) {
        requireAdmin();
        SlotView slot = lockSlot(id);
        if (!slot.status().equals("SUBMITTED")) throw conflict("Lịch chưa ở trạng thái chờ duyệt.");
        jdbc.update(
            "update appointment_slots set status='REJECTED', rejection_reason=? where id=?",
            reason.trim(),
            id
        );
        audit.record("REJECT", "APPOINTMENT_SLOT", String.valueOf(id), reason);
        return findSlot(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SlotView requestClose(long id) {
        SlotView slot = lockSlot(id);
        requireDoctor(slot.doctorId());
        if (!slot.status().equals("PUBLISHED")) throw conflict("Chỉ xin nghỉ ở lịch đã công bố.");
        jdbc.update("update appointment_slots set status='CLOSE_REQUESTED' where id=?", id);
        audit.record("REQUEST_CLOSE", "APPOINTMENT_SLOT", String.valueOf(id), null);
        return findSlot(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SlotView approveClose(long id) {
        requireAdmin();
        List<Long> patients = jdbc.query(
            "select a.patient_id from slot_reservations r " +
                "join appointments a on a.id=r.appointment_id where r.slot_id=?",
            (rs, row) -> rs.getLong(1),
            id
        );
        if (!patients.isEmpty()) lock(
            "select id from patient_profiles where id=? for update",
            patients.getFirst()
        );
        SlotView slot = lockSlot(id);
        if (!slot.status().equals("CLOSE_REQUESTED")) throw conflict("Lịch chưa có yêu cầu đóng.");
        Integer started = jdbc.queryForObject(
            "select count(*) from appointments a " +
                "join slot_reservations r on r.appointment_id=a.id " +
                "where r.slot_id=? and a.status<>'CONFIRMED'",
            Integer.class,
            id
        );
        if (started != null && started > 0) throw conflict("Lịch đã tiếp nhận, không thể đóng.");
        jdbc.update("update appointment_slots set status='CLOSED' where id=?", id);
        jdbc.update(
            "update appointments a join slot_reservations r on r.appointment_id=a.id " +
                "set a.status='NEEDS_RESCHEDULE',a.revision=a.revision+1 where r.slot_id=? and a.status='CONFIRMED'",
            id
        );
        for (Long appointmentId : jdbc.query(
            "select id from appointments where slot_id=? and status='NEEDS_RESCHEDULE'",
            (rs, row) -> rs.getLong(1),
            id
        )) {
            change(
                appointmentId,
                id,
                null,
                "NEEDS_RESCHEDULE",
                SecurityUtils.currentPrincipal().getId()
            );
            notifications.enqueue(appointmentId, "Bác sĩ nghỉ: lịch hẹn cần đổi");
        }
        jdbc.update("delete from slot_reservations where slot_id=?", id);
        audit.record("APPROVE_CLOSE", "APPOINTMENT_SLOT", String.valueOf(id), null);
        return findSlot(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @vn.antam.clinic.service.support.RetryOnLockConflict
    public AppointmentView book(BookRequest request, String key) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        lock("select id from users where id=? for update", actor.getId());
        long patientId = requestedPatient(actor, request.patientId());
        lock("select id from patient_profiles where id=? and active=1 for update", patientId);
        String hash = hash(
            request.slotId() + ":" + patientId + ":" + Objects.toString(request.symptoms(), "")
        );
        Long previous = prior(actor.getId(), "BOOK", key, hash);
        if (previous != null) return findAppointment(previous);
        SlotView slot = lockSlot(request.slotId());
        ensureBookable(slot);
        assertPatientFree(patientId, slot.startsAt(), slot.endsAt(), -1);
        BigDecimal price = currentPrice(slot.serviceId());
        long id = insert(
            "insert into appointments(appointment_code,patient_id,slot_id,status,symptoms,booked_price) " +
                "values(?,?,?,'CONFIRMED',?,?)",
            temporaryCode(),
            patientId,
            slot.id(),
            request.symptoms(),
            price
        );
        jdbc.update(
            "update appointments set appointment_code=? where id=?",
            "LH" + String.format("%010d", id),
            id
        );
        jdbc.update(
            "insert into slot_reservations(slot_id,appointment_id) values(?,?)",
            slot.id(),
            id
        );
        change(id, null, slot.id(), "BOOK", actor.getId());
        remember(actor.getId(), "BOOK", key, hash, id);
        audit.record("BOOK", "APPOINTMENT", String.valueOf(id), null);
        notifications.enqueue(id, "Đặt lịch thành công");
        waitlist.booked(patientId, slot.id());
        return findAppointment(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @vn.antam.clinic.service.support.RetryOnLockConflict
    public AppointmentView reschedule(long id, long newSlotId, String key) {
        return rescheduleInternal(id, newSlotId, key, null);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @vn.antam.clinic.service.support.RetryOnLockConflict
    public AppointmentView rescheduleReplacement(
        long id,
        long newSlotId,
        String key,
        int coordinationRevision
    ) {
        vn.antam.clinic.service.support.ClinicJdbc.require("RECEPTIONIST");
        return rescheduleInternal(id, newSlotId, key, coordinationRevision);
    }

    private AppointmentView rescheduleInternal(
        long id,
        long newSlotId,
        String key,
        Integer coordinationRevision
    ) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        lock("select id from users where id=? for update", actor.getId());
        AppointmentView before = findAppointment(id);
        requirePatientOrReceptionist(actor, before.patientId());
        lock("select id from patient_profiles where id=? for update", before.patientId());
        String hash = hash(id + ":" + newSlotId);
        Long previous = prior(actor.getId(), "RESCHEDULE", key, hash);
        if (previous != null) return findAppointment(previous);
        long oldSlotId = before.slot().id();
        for (long slotId : new long[] {
            Math.min(oldSlotId, newSlotId),
            Math.max(oldSlotId, newSlotId),
        }) {
            lock("select id from appointment_slots where id=? for update", slotId);
        }
        lock("select id from appointments where id=? for update", id);
        before = findAppointment(id);
        var coordination = jdbc.queryForList(
            "select * from appointment_coordination where appointment_id=? and resolution='PENDING' for update",
            id
        );
        boolean replacement = !coordination.isEmpty();
        if (replacement) {
            var c = coordination.getFirst();
            if (
                coordinationRevision == null ||
                !role(actor, "RECEPTIONIST") ||
                !c.get("contact_status").equals("AGREED") ||
                ((Number) c.get("revision")).intValue() != coordinationRevision
            ) throw conflict("Lễ tân cần chốt từ hồ sơ điều phối đã được bệnh nhân đồng ý.");
            if (
                jdbc.queryForObject(
                    "select count(*) from invoices i where i.appointment_id=? and i.status in ('PARTIAL','PAID') and (select coalesce(sum(r.amount),0) from receipts r where r.invoice_id=i.id)>(select coalesce(sum(f.amount),0) from refunds f where f.invoice_id=i.id)",
                    Integer.class,
                    id
                ) > 0
            ) throw conflict("Chuyển thu ngân đối chiếu thanh toán trước khi đổi lịch.");
        } else if (coordinationRevision != null) throw conflict(
            "Hồ sơ điều phối không còn chờ xử lý."
        );
        if (
            !before.status().equals("CONFIRMED") && !before.status().equals("NEEDS_RESCHEDULE")
        ) throw conflict("Lịch đã tiếp nhận hoặc không thể đổi.");
        if (
            before.status().equals("CONFIRMED") && !before.slot().startsAt().isAfter(now())
        ) throw conflict("Đã qua giờ hẹn.");
        if (newSlotId == oldSlotId) return before;
        SlotView next = findSlot(newSlotId);
        ensureBookable(next);
        if (
            replacement &&
            (next.serviceId() != before.slot().serviceId() ||
                !jdbc
                    .queryForObject(
                        "select specialty_id from doctor_profiles where staff_id=?",
                        Long.class,
                        next.doctorId()
                    )
                    .equals(
                        jdbc.queryForObject(
                            "select specialty_id from doctor_profiles where staff_id=?",
                            Long.class,
                            before.slot().doctorId()
                        )
                    ))
        ) throw conflict("Lịch thay thế phải cùng dịch vụ và chuyên khoa.");
        assertPatientFree(before.patientId(), next.startsAt(), next.endsAt(), id);
        jdbc.update("delete from slot_reservations where appointment_id=?", id);
        jdbc.update(
            "update appointments set slot_id=?,status='CONFIRMED',booked_price=?,reminder_sent_at=null,revision=revision+1 where id=?",
            next.id(),
            replacement ? new BigDecimal(before.bookedPrice()) : currentPrice(next.serviceId()),
            id
        );
        jdbc.update(
            "insert into slot_reservations(slot_id,appointment_id) values(?,?)",
            next.id(),
            id
        );
        change(id, oldSlotId, next.id(), "RESCHEDULE", actor.getId());
        if (replacement) jdbc.update(
            "update appointment_coordination set resolution='RESCHEDULED',replacement_slot_id=?,resolved_at=?,resolved_by=?,revision=revision+1 where appointment_id=?",
            next.id(),
            ts(now()),
            actor.getId(),
            id
        );
        remember(actor.getId(), "RESCHEDULE", key, hash, id);
        audit.record("RESCHEDULE", "APPOINTMENT", String.valueOf(id), null);
        notifications.enqueue(id, "Lịch hẹn đã đổi");
        waitlist.booked(before.patientId(), next.id());
        clinicEvents.queueUpdated(id);
        slotFreed(before);
        return findAppointment(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @vn.antam.clinic.service.support.RetryOnLockConflict
    public AppointmentView cancel(long id, String reason) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        lock("select id from users where id=? for update", actor.getId());
        AppointmentView before = findAppointment(id);
        requirePatientOrReceptionist(actor, before.patientId());
        lock("select id from patient_profiles where id=? for update", before.patientId());
        lock("select id from appointment_slots where id=? for update", before.slot().id());
        lock("select id from appointments where id=? for update", id);
        before = findAppointment(id);
        if (
            !before.status().equals("CONFIRMED") && !before.status().equals("NEEDS_RESCHEDULE")
        ) throw conflict("Lịch này không thể hủy.");
        if (
            before.status().equals("CONFIRMED") && !before.slot().startsAt().isAfter(now())
        ) throw conflict("Đã qua giờ hẹn.");
        jdbc.update("delete from slot_reservations where appointment_id=?", id);
        jdbc.update(
            "update appointments set status='CANCELLED',cancellation_reason=?,revision=revision+1 where id=?",
            reason,
            id
        );
        change(id, before.slot().id(), null, "CANCEL", actor.getId());
        jdbc.update(
            "update appointment_coordination set resolution='CANCELLED',contact_note=?,resolved_at=?,resolved_by=?,revision=revision+1 where appointment_id=? and resolution='PENDING'",
            reason,
            ts(now()),
            actor.getId(),
            id
        );
        audit.record("CANCEL", "APPOINTMENT", String.valueOf(id), reason);
        notifications.enqueue(id, "Lịch hẹn đã hủy");
        clinicEvents.queueUpdated(id);
        slotFreed(before);
        return findAppointment(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AppointmentView checkIn(long id) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "RECEPTIONIST")) throw forbidden();
        AppointmentView before = findAppointment(id);
        lock("select id from staff where id=? for update", before.slot().doctorId());
        lock("select id from appointment_slots where id=? for update", before.slot().id());
        lock("select id from appointments where id=? for update", id);
        before = findAppointment(id);
        if (
            before.status().equals("CHECKED_IN") || before.status().equals("COMPLETED")
        ) return before;
        if (!before.status().equals("CONFIRMED")) throw conflict("Lịch không thể tiếp nhận.");
        LocalDate appointmentDay = before
            .slot()
            .startsAt()
            .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            .toLocalDate();
        if (!appointmentDay.equals(LocalDate.now(clock))) throw bad(
            "Chỉ tiếp nhận lịch trong ngày."
        );
        int next = jdbc.queryForObject(
            "select coalesce(max(a.queue_number),0)+1 from appointments a " +
                "join appointment_slots sl on sl.id=a.slot_id where sl.doctor_staff_id=? " +
                "and sl.starts_at>=? and sl.starts_at<?",
            Integer.class,
            before.slot().doctorId(),
            ts(appointmentDay.atStartOfDay(clock.getZone()).toInstant()),
            ts(appointmentDay.plusDays(1).atStartOfDay(clock.getZone()).toInstant())
        );
        jdbc.update(
            "update appointments set status='CHECKED_IN',queue_number=?,checked_in_at=?,revision=revision+1 where id=?",
            next,
            ts(now()),
            id
        );
        long invoiceId = insert(
            "insert into invoices(invoice_code,appointment_id,patient_id,source_type,source_id) values(?,?,?,'EXAMINATION',?)",
            temporaryCode(),
            id,
            before.patientId(),
            id
        );
        jdbc.update(
            "update invoices set invoice_code=? where id=?",
            "HD" + String.format("%010d", invoiceId),
            invoiceId
        );
        String unit = jdbc.queryForObject(
            "select unit from services where id=?",
            String.class,
            before.slot().serviceId()
        );
        jdbc.update(
            "insert into invoice_lines(invoice_id,source_type,source_id,description_snapshot,unit_snapshot,quantity,unit_price) " +
                "values(?,'EXAMINATION',?,?,?,?,?)",
            invoiceId,
            id,
            before.slot().serviceName(),
            unit,
            BigDecimal.ONE,
            new BigDecimal(before.bookedPrice())
        );
        if (new BigDecimal(before.bookedPrice()).signum() == 0) jdbc.update(
            "update invoices set status='PAID' where id=?",
            invoiceId
        );
        insert(
            "insert into encounters(appointment_id,patient_id,doctor_staff_id,chief_complaint) values(?,?,?,?)",
            id,
            before.patientId(),
            before.slot().doctorId(),
            before.symptoms()
        );
        change(id, before.slot().id(), before.slot().id(), "CHECK_IN", actor.getId());
        audit.record("CHECK_IN", "APPOINTMENT", String.valueOf(id), null);
        clinicEvents.queueUpdated(id);
        return findAppointment(id);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AppointmentView markNoShow(long id) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "RECEPTIONIST")) throw forbidden();
        AppointmentView before = findAppointment(id);
        lock("select id from appointment_slots where id=? for update", before.slot().id());
        lock("select id from appointments where id=? for update", id);
        before = findAppointment(id);
        if (
            !before.status().equals("CONFIRMED") || before.slot().startsAt().isAfter(now())
        ) throw conflict("Chỉ đánh dấu vắng hẹn sau giờ bắt đầu lịch đã xác nhận.");
        jdbc.update("update appointments set status='NO_SHOW',revision=revision+1 where id=?", id);
        change(id, before.slot().id(), before.slot().id(), "NO_SHOW", actor.getId());
        audit.record("NO_SHOW", "APPOINTMENT", String.valueOf(id), null);
        clinicEvents.queueUpdated(id);
        return findAppointment(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> receptionAppointments() {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "RECEPTIONIST")) throw forbidden();
        return jdbc.query(
            appointmentSelect() +
                "where sl.starts_at>=? and sl.starts_at<? and a.status in ('CONFIRMED','CHECKED_IN','NEEDS_RESCHEDULE') " +
                "order by sl.starts_at limit 200",
            (rs, row) -> appointmentRow(rs),
            today(),
            today(),
            ts(now().minusSeconds(86400)),
            ts(now().plusSeconds(30L * 86400))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> myAppointments() {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "PATIENT")) throw forbidden();
        return jdbc.query(
            appointmentSelect() +
                "where " +
                PatientAccess.ownedSql("p") +
                " order by a.booked_at desc limit 100",
            (rs, row) -> appointmentRow(rs),
            today(),
            today(),
            actor.getId(),
            actor.getId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> queueForDoctor() {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "DOCTOR")) throw forbidden();
        long doctor = staffId(actor.getId());
        LocalDate day = LocalDate.now(clock);
        return jdbc.query(
            appointmentSelect() +
                "where sl.doctor_staff_id=? and sl.starts_at>=? and sl.starts_at<? " +
                "and a.status in ('CONFIRMED','CHECKED_IN') order by sl.starts_at",
            (rs, row) -> appointmentRow(rs),
            today(),
            today(),
            doctor,
            ts(day.atStartOfDay(clock.getZone()).toInstant()),
            ts(day.plusDays(1).atStartOfDay(clock.getZone()).toInstant())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentView viewAppointment(long id) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        AppointmentView view = findAppointment(id);
        if (
            role(actor, "ADMIN") || role(actor, "RECEPTIONIST") || role(actor, "CASHIER")
        ) return view;
        if (role(actor, "PATIENT") && access.owns(actor.getId(), view.patientId())) return view;
        if (role(actor, "DOCTOR") && staffId(actor.getId()) == view.slot().doctorId()) return view;
        throw forbidden();
    }

    @Override
    public AppointmentView findAppointment(long id) {
        List<AppointmentView> rows = jdbc.query(
            """
            select a.*,p.full_name patient_name from appointments a
            join patient_profiles p on p.id=a.patient_id where a.id=?
            """,
            (rs, row) ->
                new AppointmentView(
                    rs.getLong("id"),
                    rs.getString("appointment_code"),
                    rs.getLong("patient_id"),
                    rs.getString("patient_name"),
                    findSlot(rs.getLong("slot_id")),
                    rs.getString("status"),
                    rs.getString("symptoms"),
                    (Integer) rs.getObject("queue_number"),
                    money(rs.getBigDecimal("booked_price")),
                    instant(rs, "checked_in_at"),
                    rs.getInt("revision")
                ),
            id
        );
        if (rows.isEmpty()) throw notFound("Không tìm thấy lịch hẹn.");
        return rows.getFirst();
    }

    @Override
    public SlotView findSlot(long id) {
        List<SlotView> rows = jdbc.query(
            slotSelect() + " where sl.id=?",
            (rs, row) -> slot(rs),
            today(),
            today(),
            id
        );
        if (rows.isEmpty()) throw notFound("Không tìm thấy khoảng khám.");
        return rows.getFirst();
    }

    private String slotSelect() {
        return """
        select sl.*,u.full_name doctor_name,rm.name room_name,sv.name service_name,
               (select p.unit_price from service_prices p where p.service_id=sl.service_id
                and p.effective_from<=? and (p.effective_to is null or p.effective_to>=?)
                order by p.effective_from desc limit 1) price,
               r.slot_id reserved_slot
        from appointment_slots sl join staff st on st.id=sl.doctor_staff_id
        join users u on u.id=st.user_id join rooms rm on rm.id=sl.room_id
        join services sv on sv.id=sl.service_id
        left join slot_reservations r on r.slot_id=sl.id
        """;
    }

    private String appointmentSelect() {
        return """
        select sl.*,u.full_name doctor_name,rm.name room_name,sv.name service_name,
          (select p.unit_price from service_prices p where p.service_id=sl.service_id
           and p.effective_from<=? and (p.effective_to is null or p.effective_to>=?)
           order by p.effective_from desc limit 1) price,r.slot_id reserved_slot,
          a.id appointment_id,a.appointment_code,a.patient_id,p.full_name patient_name,
          a.status appointment_status,a.symptoms,a.queue_number,a.booked_price,a.checked_in_at,a.revision
        from appointments a join patient_profiles p on p.id=a.patient_id
        join appointment_slots sl on sl.id=a.slot_id join staff st on st.id=sl.doctor_staff_id
        join users u on u.id=st.user_id join rooms rm on rm.id=sl.room_id
        join services sv on sv.id=sl.service_id left join slot_reservations r on r.slot_id=sl.id
        """;
    }

    private AppointmentView appointmentRow(ResultSet rs) throws java.sql.SQLException {
        return new AppointmentView(
            rs.getLong("appointment_id"),
            rs.getString("appointment_code"),
            rs.getLong("patient_id"),
            rs.getString("patient_name"),
            slot(rs),
            rs.getString("appointment_status"),
            rs.getString("symptoms"),
            (Integer) rs.getObject("queue_number"),
            money(rs.getBigDecimal("booked_price")),
            instant(rs, "checked_in_at"),
            rs.getInt("revision")
        );
    }

    private SlotView slot(ResultSet rs) throws java.sql.SQLException {
        Instant start = instant(rs, "starts_at");
        String status = rs.getString("status");
        return new SlotView(
            rs.getLong("id"),
            rs.getLong("doctor_staff_id"),
            rs.getString("doctor_name"),
            rs.getLong("room_id"),
            rs.getString("room_name"),
            rs.getLong("service_id"),
            rs.getString("service_name"),
            start,
            instant(rs, "ends_at"),
            status,
            status.equals("PUBLISHED") &&
                start.isAfter(now()) &&
                rs.getObject("reserved_slot") == null,
            money(rs.getBigDecimal("price")),
            rs.getString("rejection_reason"),
            rs.getString("note")
        );
    }

    private SlotView lockSlot(long id) {
        lock("select id from appointment_slots where id=? for update", id);
        return findSlot(id);
    }

    private void ensureBookable(SlotView slot) {
        if (!slot.available()) throw conflict("Khoảng khám đã kín hoặc không còn được đặt.");
        if (
            jdbc.queryForObject(
                "select count(*) from doctor_absences where doctor_staff_id=? and starts_at<? and ends_at>?",
                Integer.class,
                slot.doctorId(),
                ts(slot.endsAt()),
                ts(slot.startsAt())
            ) > 0
        ) throw conflict("Bác sĩ nghỉ trong khoảng giờ này.");
        Integer valid = jdbc.queryForObject(
            """
            select count(*) from appointment_slots sl join doctor_profiles dp on dp.staff_id=sl.doctor_staff_id
            join staff st on st.id=dp.staff_id join users u on u.id=st.user_id
            join rooms rm on rm.id=sl.room_id join services sv on sv.id=sl.service_id
            join specialties sp on sp.id=dp.specialty_id join departments dept on dept.id=sp.department_id
            where sl.id=? and st.active=1 and u.status='ACTIVE' and rm.active=1 and sv.active=1 and sp.active=1 and dept.active=1
            and exists (select 1 from doctor_services ds where ds.doctor_staff_id=st.id and ds.service_id=sv.id)
            and exists (select 1 from user_roles ur join roles rl on rl.id=ur.role_id where ur.user_id=u.id and rl.code='DOCTOR')
            """,
            Integer.class,
            slot.id()
        );
        if (valid == null || valid == 0) throw conflict(
            "Bác sĩ, phòng hoặc dịch vụ không còn hoạt động."
        );
    }

    private void assertDoctorService(long doctor, long service) {
        Integer valid = jdbc.queryForObject(
            "select count(*) from doctor_profiles dp join specialties sp on sp.id=dp.specialty_id join departments d on d.id=sp.department_id join staff st on st.id=dp.staff_id join users u on u.id=st.user_id join doctor_services ds on ds.doctor_staff_id=st.id join services sv on sv.id=ds.service_id where st.id=? and sv.id=? and st.active=1 and u.status='ACTIVE' and sp.active=1 and d.active=1 and sv.active=1 and sv.category='EXAMINATION' and exists(select 1 from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=u.id and r.code='DOCTOR')",
            Integer.class,
            doctor,
            service
        );
        if (valid == null || valid == 0) throw conflict(
            "Hồ sơ bác sĩ hoặc dịch vụ không còn hợp lệ."
        );
    }

    private void assertNoOverlap(
        long doctor,
        long room,
        Instant start,
        Instant end,
        long exceptId
    ) {
        Integer count = jdbc.queryForObject(
            """
            select count(*) from appointment_slots sl
            where sl.id<>? and sl.status in ('DRAFT','SUBMITTED','PUBLISHED','CLOSE_REQUESTED')
              and (sl.doctor_staff_id=? or sl.room_id=?) and sl.starts_at<? and sl.ends_at>?
            """,
            Integer.class,
            exceptId,
            doctor,
            room,
            ts(end),
            ts(start)
        );
        if (count != null && count > 0) throw conflict(
            "Bác sĩ hoặc phòng đã có khoảng khám trùng giờ."
        );
    }

    private void assertPatientFree(
        long patient,
        Instant start,
        Instant end,
        long exceptAppointment
    ) {
        Integer count = jdbc.queryForObject(
            """
            select count(*) from appointments a join slot_reservations r on r.appointment_id=a.id
            join appointment_slots sl on sl.id=r.slot_id
            where a.patient_id=? and a.id<>? and sl.starts_at<? and sl.ends_at>?
            """,
            Integer.class,
            patient,
            exceptAppointment,
            ts(end),
            ts(start)
        );
        if (count != null && count > 0) throw conflict("Bệnh nhân đã có lịch khám trùng giờ.");
    }

    private BigDecimal currentPrice(long serviceId) {
        List<BigDecimal> prices = jdbc.query(
            "select unit_price from service_prices where service_id=? " +
                "and effective_from<=? and (effective_to is null or effective_to>=?) " +
                "order by effective_from desc limit 1",
            (rs, row) -> rs.getBigDecimal(1),
            serviceId,
            today(),
            today()
        );
        if (prices.isEmpty()) throw conflict("Dịch vụ chưa có giá hiện hành.");
        return prices.getFirst();
    }

    private void slotFreed(AppointmentView before) {
        events.publishEvent(
            new ClinicEvent(
                "slot.freed",
                before.slot().roomId(),
                before.slot().doctorId(),
                access.recipientUserId(before.patientId()),
                Map.of(
                    "slotId",
                    before.slot().id(),
                    "releaseKey",
                    "slot:" +
                        before.slot().id() +
                        ":change:" +
                        jdbc.queryForObject(
                            "select max(id) from appointment_changes where appointment_id=? and action in ('CANCEL','RESCHEDULE')",
                            Long.class,
                            before.id()
                        )
                )
            )
        );
    }

    private long requestedPatient(ClinicUserPrincipal actor, Long requested) {
        if (role(actor, "PATIENT")) {
            long patient = requested == null ? access.primaryPatientId(actor.getId()) : requested;
            access.requireOwn(actor.getId(), patient);
            return patient;
        }
        if (role(actor, "RECEPTIONIST") && requested != null) return requested;
        throw forbidden();
    }

    private void requirePatientOrReceptionist(ClinicUserPrincipal actor, long patientId) {
        if (role(actor, "RECEPTIONIST")) return;
        if (role(actor, "PATIENT") && access.owns(actor.getId(), patientId)) return;
        throw forbidden();
    }

    private long staffId(long userId) {
        List<Long> ids = jdbc.query(
            "select id from staff where user_id=? and active=1",
            (rs, row) -> rs.getLong(1),
            userId
        );
        if (ids.isEmpty()) throw forbidden();
        return ids.getFirst();
    }

    private void requireDoctor(long doctorId) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "DOCTOR") || staffId(actor.getId()) != doctorId) throw forbidden();
    }

    private void requireAdmin() {
        if (!role(SecurityUtils.currentPrincipal(), "ADMIN")) throw forbidden();
    }

    private static boolean role(ClinicUserPrincipal actor, String name) {
        return actor
            .getAuthorities()
            .stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + name));
    }

    private void lock(String sql, Object... params) {
        List<Long> ids = jdbc.query(sql, (rs, row) -> rs.getLong(1), params);
        if (ids.isEmpty()) throw conflict("Dữ liệu đã thay đổi hoặc không còn hoạt động.");
    }

    private long insert(String sql, Object... params) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
            );
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private void change(
        long appointmentId,
        Long oldSlotId,
        Long newSlotId,
        String action,
        long actorId
    ) {
        jdbc.update(
            "insert into appointment_changes(appointment_id,old_slot_id,new_slot_id,actor_user_id,action) " +
                "values(?,?,?,?,?)",
            appointmentId,
            oldSlotId,
            newSlotId,
            actorId,
            action
        );
    }

    private Long prior(long actor, String operation, String key, String hash) {
        if (key == null || key.isBlank()) return null;
        if (key.length() > 100) throw bad("Mã chống gửi lặp quá dài.");
        List<Object[]> rows = jdbc.query(
            "select request_hash,result_id from idempotency_results " +
                "where actor_user_id=? and operation=? and request_key=?",
            (rs, row) -> new Object[] { rs.getString(1), rs.getLong(2) },
            actor,
            operation,
            key
        );
        if (rows.isEmpty()) return null;
        if (!hash.equals(rows.getFirst()[0])) throw conflict(
            "Mã gửi lặp đã dùng cho yêu cầu khác."
        );
        return (Long) rows.getFirst()[1];
    }

    private void remember(long actor, String operation, String key, String hash, long id) {
        if (key != null && !key.isBlank()) jdbc.update(
            "insert into idempotency_results " +
                "(actor_user_id,operation,request_key,request_hash,result_id) values(?,?,?,?,?)",
            actor,
            operation,
            key,
            hash,
            id
        );
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String temporaryCode() {
        return "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 22);
    }

    private Date today() {
        return Date.valueOf(LocalDate.now(clock));
    }

    private Instant now() {
        return clock.instant();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static String money(BigDecimal amount) {
        return amount == null ? null : amount.toPlainString();
    }

    private static Instant instant(ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static ApiException bad(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", detail);
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Không thực hiện được", detail);
    }

    private static ApiException forbidden() {
        return new ApiException(
            HttpStatus.FORBIDDEN,
            "Không có quyền",
            "Bạn không có quyền thực hiện thao tác này."
        );
    }

    private static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy", detail);
    }
}
