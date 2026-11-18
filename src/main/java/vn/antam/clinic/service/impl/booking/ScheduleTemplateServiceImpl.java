package vn.antam.clinic.service.impl.booking;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.ClinicUserPrincipal;
import vn.antam.clinic.security.SecurityUtils;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.booking.BookingService;
import vn.antam.clinic.service.booking.ScheduleTemplateService;
import vn.antam.clinic.service.dto.booking.BookingDtos.AppointmentView;
import vn.antam.clinic.service.dto.booking.BookingDtos.CreateSlotRequest;
import vn.antam.clinic.service.dto.booking.BookingDtos.SlotView;
import vn.antam.clinic.service.dto.booking.ScheduleTemplateDtos.CopyRequest;
import vn.antam.clinic.service.dto.booking.ScheduleTemplateDtos.CreateTemplateRequest;
import vn.antam.clinic.service.dto.booking.ScheduleTemplateDtos.GenerateRequest;
import vn.antam.clinic.service.dto.booking.ScheduleTemplateDtos.TemplateView;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
public class ScheduleTemplateServiceImpl implements ScheduleTemplateService {

    private final JdbcTemplate jdbc;
    private final BookingService bookings;
    private final AuditService audit;
    private final Clock clock;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public ScheduleTemplateServiceImpl(
        JdbcTemplate jdbc,
        BookingService bookings,
        AuditService audit,
        Clock clock
    ) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateView> list() {
        long doctor = doctorId();
        return jdbc.query(
            "select * from weekly_slot_templates where doctor_staff_id=? and active=1 " +
                "order by weekday,starts_time",
            (rs, row) -> map(rs),
            doctor
        );
    }

    @Override
    @Transactional
    public TemplateView create(CreateTemplateRequest request) {
        long doctor = doctorId();
        if (
            request.weekday() < 1 ||
            request.weekday() > 7 ||
            !request.startsTime().isBefore(request.endsTime()) ||
            request.endsTime().toSecondOfDay() - request.startsTime().toSecondOfDay() > 8 * 3600
        ) throw bad("Mẫu tuần cần thời gian tăng dần và không quá 8 giờ.");
        Integer allowed = jdbc.queryForObject(
            "select count(*) from doctor_services ds join services sv on sv.id=ds.service_id " +
                "join rooms rm on rm.id=? where ds.doctor_staff_id=? and ds.service_id=? " +
                "and sv.active=1 and sv.category='EXAMINATION' and rm.active=1 and rm.room_type='CLINIC'",
            Integer.class,
            request.roomId(),
            doctor,
            request.serviceId()
        );
        if (allowed == null || allowed == 0) throw bad("Phòng hoặc dịch vụ không phù hợp.");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "insert into weekly_slot_templates " +
                    "(doctor_staff_id,weekday,starts_time,ends_time,room_id,service_id) values(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, doctor);
            statement.setInt(2, request.weekday());
            statement.setObject(3, request.startsTime());
            statement.setObject(4, request.endsTime());
            statement.setLong(5, request.roomId());
            statement.setLong(6, request.serviceId());
            return statement;
        }, key);
        long id = Objects.requireNonNull(key.getKey()).longValue();
        audit.record("CREATE", "WEEKLY_TEMPLATE", String.valueOf(id), null);
        return find(id);
    }

    @Override
    @Transactional
    public List<SlotView> generate(long id, GenerateRequest request) {
        long doctor = doctorId();
        Integer owned = jdbc.queryForObject(
            "select count(*) from weekly_slot_templates where id=? and doctor_staff_id=? and active=1",
            Integer.class,
            id,
            doctor
        );
        if (owned == null || owned == 0) throw forbidden();
        TemplateView template = find(id);
        if (
            request.from().isAfter(request.to()) ||
            request.to().isAfter(request.from().plusDays(30))
        ) throw bad("Chỉ tạo tối đa 31 ngày mỗi lần.");
        List<SlotView> result = new ArrayList<>();
        for (LocalDate day = request.from(); !day.isAfter(request.to()); day = day.plusDays(1)) {
            if (day.getDayOfWeek().getValue() == template.weekday()) {
                result.add(
                    bookings.createSlot(
                        new CreateSlotRequest(
                            template.roomId(),
                            template.serviceId(),
                            at(day, template.startsTime()),
                            at(day, template.endsTime()),
                            "Từ mẫu tuần #" + id
                        )
                    )
                );
            }
        }
        return result;
    }

    @Override
    @Transactional
    public TemplateView update(long id, CreateTemplateRequest request) {
        long doctor = doctorId();
        if (
            request.weekday() < 1 ||
            request.weekday() > 7 ||
            !request.startsTime().isBefore(request.endsTime()) ||
            request.endsTime().toSecondOfDay() - request.startsTime().toSecondOfDay() > 8 * 3600
        ) throw bad("Thời gian mẫu tuần không hợp lệ.");
        if (
            jdbc.queryForObject(
                "select count(*) from doctor_services ds join services sv on sv.id=ds.service_id join rooms rm on rm.id=? where ds.doctor_staff_id=? and ds.service_id=? and sv.active=1 and sv.category='EXAMINATION' and rm.active=1 and rm.room_type='CLINIC'",
                Integer.class,
                request.roomId(),
                doctor,
                request.serviceId()
            ) == 0
        ) throw bad("Phòng hoặc dịch vụ không phù hợp.");
        if (
            jdbc.update(
                "update weekly_slot_templates set weekday=?,starts_time=?,ends_time=?,room_id=?,service_id=? where id=? and doctor_staff_id=? and active=1",
                request.weekday(),
                request.startsTime(),
                request.endsTime(),
                request.roomId(),
                request.serviceId(),
                id,
                doctor
            ) != 1
        ) throw forbidden();
        audit.record("UPDATE", "WEEKLY_TEMPLATE", String.valueOf(id), null);
        return find(id);
    }

    @Override
    @Transactional
    public void disable(long id) {
        if (
            jdbc.update(
                "update weekly_slot_templates set active=0 where id=? and doctor_staff_id=? and active=1",
                id,
                doctorId()
            ) != 1
        ) throw forbidden();
        audit.record("DISABLE", "WEEKLY_TEMPLATE", String.valueOf(id), null);
    }

    @Override
    @Transactional
    public List<SlotView> copy(long id, CopyRequest request) {
        long doctor = doctorId();
        SlotView source = bookings.findSlot(id);
        if (source.doctorId() != doctor) throw forbidden();
        if (
            request.dates().size() > 31 ||
            request.dates().stream().distinct().count() != request.dates().size()
        ) throw bad("Chọn tối đa 31 ngày khác nhau.");
        LocalTime from = source.startsAt().atZone(ZONE).toLocalTime();
        LocalTime to = source.endsAt().atZone(ZONE).toLocalTime();
        if (!from.isBefore(to)) throw bad("Chỉ sao chép khoảng nằm trong một ngày.");
        List<SlotView> result = new ArrayList<>();
        for (LocalDate day : request.dates()) {
            result.add(
                bookings.createSlot(
                    new CreateSlotRequest(
                        source.roomId(),
                        source.serviceId(),
                        at(day, from),
                        at(day, to),
                        "Sao chép từ #" + id
                    )
                )
            );
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> affected(long slotId) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "ADMIN")) throw forbidden();
        bookings.findSlot(slotId);
        return jdbc.query(
            "select a.id from appointments a join slot_reservations r on r.appointment_id=a.id " +
                "where r.slot_id=? and a.status='CONFIRMED'",
            (rs, row) -> bookings.findAppointment(rs.getLong(1)),
            slotId
        );
    }

    private TemplateView find(long id) {
        List<TemplateView> rows = jdbc.query(
            "select * from weekly_slot_templates where id=? and active=1",
            (rs, row) -> map(rs),
            id
        );
        if (rows.isEmpty()) throw new ApiException(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy",
            "Mẫu tuần không tồn tại."
        );
        return rows.getFirst();
    }

    private static TemplateView map(ResultSet rs) throws java.sql.SQLException {
        return new TemplateView(
            rs.getLong("id"),
            rs.getInt("weekday"),
            rs.getTime("starts_time").toLocalTime(),
            rs.getTime("ends_time").toLocalTime(),
            rs.getLong("room_id"),
            rs.getLong("service_id"),
            rs.getBoolean("active")
        );
    }

    private long doctorId() {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "DOCTOR")) throw forbidden();
        List<Long> ids = jdbc.query(
            "select id from staff where user_id=? and active=1",
            (rs, row) -> rs.getLong(1),
            actor.getId()
        );
        if (ids.isEmpty()) throw forbidden();
        return ids.getFirst();
    }

    private static Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZONE).toInstant();
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
            "Bạn không có quyền thao tác lịch này."
        );
    }

    private static ApiException bad(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Lịch không hợp lệ", detail);
    }
}
