package vn.antam.clinic.service.scheduler;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.antam.clinic.security.PatientAccess;
import vn.antam.clinic.service.notification.AppointmentNotificationService;

@Component
public class ReminderWorker {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AppointmentNotificationService notices;
    private final TransactionTemplate transactions;

    public ReminderWorker(
        JdbcTemplate jdbc,
        Clock clock,
        AppointmentNotificationService notices,
        PatientAccess access,
        PlatformTransactionManager manager
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.notices = notices;
        transactions = new TransactionTemplate(manager);
    }

    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "appointment-reminders",
        lockAtMostFor = "30m",
        lockAtLeastFor = "2s"
    )
    @Scheduled(fixedDelayString = "${app.reminders.poll-delay-ms:600000}")
    public void appointments() {
        var settings = jdbc.queryForList(
            "select reminders_enabled,reminder_hours_before from clinic_settings limit 1"
        );
        if (settings.isEmpty() || !enabled(settings.getFirst().get("reminders_enabled"))) return;
        int hours = ((Number) settings.getFirst().get("reminder_hours_before")).intValue();
        if (hours < 1 || hours > 168) hours = 24;
        var now = clock.instant();
        var due = jdbc.queryForList(
            "select a.id,a.patient_id from appointments a join appointment_slots sl on sl.id=a.slot_id join patient_profiles p on p.id=a.patient_id " +
                "where a.status='CONFIRMED' and p.active=1 and a.reminder_sent_at is null and sl.starts_at>? and sl.starts_at<=? order by sl.starts_at,a.id limit 300",
            Timestamp.from(now),
            Timestamp.from(now.plusSeconds(hours * 3600L))
        );
        int reminderHours = hours;
        for (var row : due)
            transactions.executeWithoutResult(tx -> {
                long id = ((Number) row.get("id")).longValue();
                // Match booking's patient -> slot -> appointment lock order.
                jdbc.queryForList(
                    "select id from patient_profiles where id=? for update",
                    row.get("patient_id")
                );
                Long slot = jdbc.queryForObject(
                    "select slot_id from appointments where id=?",
                    Long.class,
                    id
                );
                jdbc.queryForList("select id from appointment_slots where id=? for update", slot);
                if (
                    jdbc.update(
                        "update appointments a join appointment_slots sl on sl.id=a.slot_id join patient_profiles p on p.id=a.patient_id " +
                            "set a.reminder_sent_at=? where a.id=? and a.status='CONFIRMED' and p.active=1 and a.reminder_sent_at is null and sl.starts_at>? and sl.starts_at<=?",
                        Timestamp.from(now),
                        id,
                        Timestamp.from(now),
                        Timestamp.from(now.plusSeconds(reminderHours * 3600L))
                    ) == 0
                ) return;
                var visit = jdbc.queryForMap(
                    "select a.revision,p.full_name,sl.starts_at,u.full_name doctor_name,rm.name room_name from appointments a " +
                        "join patient_profiles p on p.id=a.patient_id join appointment_slots sl on sl.id=a.slot_id join staff st on st.id=sl.doctor_staff_id " +
                        "join users u on u.id=st.user_id join rooms rm on rm.id=sl.room_id where a.id=?",
                    id
                );
                String time = vn.antam.clinic.service.support.ClinicJdbc.instant(
                    visit.get("starts_at")
                )
                    .atZone(ZONE)
                    .format(DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy"));
                notices.enqueue(
                    id,
                    "REMINDER",
                    "Nhắc lịch khám",
                    "Nhắc lịch khám của " +
                        visit.get("full_name") +
                        " lúc " +
                        time +
                        ", BS. " +
                        visit.get("doctor_name") +
                        ", " +
                        visit.get("room_name") +
                        ". Vui lòng đến trước 15 phút.",
                    "/portal",
                    "reminder:" + id + ":" + visit.get("revision")
                );
            });
    }

    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "followup-reminders",
        lockAtMostFor = "30m",
        lockAtLeastFor = "2s"
    )
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Ho_Chi_Minh")
    public void followUps() {
        var settings = jdbc.queryForList("select reminders_enabled from clinic_settings limit 1");
        if (settings.isEmpty() || !enabled(settings.getFirst().get("reminders_enabled"))) return;
        Date target = Date.valueOf(LocalDate.now(clock.withZone(ZONE)).plusDays(3));
        var due = jdbc.queryForList(
            "select e.id,e.patient_id from encounters e join patient_profiles p on p.id=e.patient_id where e.status='COMPLETED' and p.active=1 and e.follow_up_date=? and e.follow_up_reminded_at is null limit 300",
            target
        );
        for (var row : due)
            transactions.executeWithoutResult(tx -> {
                long id = ((Number) row.get("id")).longValue();
                jdbc.queryForList(
                    "select id from patient_profiles where id=? for update",
                    row.get("patient_id")
                );
                if (
                    jdbc.update(
                        "update encounters e join patient_profiles p on p.id=e.patient_id set e.follow_up_reminded_at=? where e.id=? and p.active=1 and e.status='COMPLETED' and e.follow_up_date=? and e.follow_up_reminded_at is null",
                        Timestamp.from(clock.instant()),
                        id,
                        target
                    ) == 0
                ) return;
                var visit = jdbc.queryForMap(
                    "select e.patient_id,e.appointment_id,e.doctor_staff_id,p.full_name,sl.service_id from encounters e join patient_profiles p on p.id=e.patient_id join appointments a on a.id=e.appointment_id join appointment_slots sl on sl.id=a.slot_id where e.id=?",
                    id
                );
                long patient = ((Number) visit.get("patient_id")).longValue();
                notices.enqueue(
                    ((Number) visit.get("appointment_id")).longValue(),
                    "FOLLOW_UP",
                    "Nhắc lịch tái khám",
                    "Hẹn tái khám của " +
                        visit.get("full_name") +
                        " ngày " +
                        target.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                        ". Vui lòng đặt lịch trước khi đến.",
                    "/booking?doctorId=" +
                        visit.get("doctor_staff_id") +
                        "&serviceId=" +
                        visit.get("service_id") +
                        "&patientId=" +
                        patient +
                        "&date=" +
                        target +
                        "&followUp=" +
                        id,
                    "followup:" + id + ":" + target
                );
            });
    }

    private static boolean enabled(Object v) {
        return Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() == 1);
    }
}
