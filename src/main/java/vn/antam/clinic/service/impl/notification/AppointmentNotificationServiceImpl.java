package vn.antam.clinic.service.impl.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.notification.AppointmentNotificationService;
import vn.antam.clinic.service.support.ClinicJdbc;

@Service
public class AppointmentNotificationServiceImpl implements AppointmentNotificationService {

    private final JdbcTemplate jdbc;

    public AppointmentNotificationServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void enqueue(long appointment, String title) {
        enqueue(appointment, "APPOINTMENT", title, null, "/portal", null);
    }

    @Override
    @Transactional
    public void enqueue(
        long appointment,
        String kind,
        String title,
        String body,
        String link,
        String dedupeKey
    ) {
        jdbc.query(
            "select a.appointment_code,a.revision,coalesce(p.owner_user_id,p.user_id) recipient_user,p.email,u.email account_email " +
                "from appointments a join patient_profiles p on p.id=a.patient_id left join users u on u.id=coalesce(p.owner_user_id,p.user_id) where a.id=? and p.active=1",
            r -> {
                String message =
                    body == null
                        ? title +
                          " · " +
                          r.getString("appointment_code") +
                          ". Xem chi tiết lịch hẹn tại cổng bệnh nhân hoặc liên hệ quầy tiếp nhận."
                        : body;
                Long recipient = r.getObject("recipient_user", Long.class);
                boolean added =
                    recipient == null ||
                    insertNotice(recipient, appointment, kind, title, message, link, dedupeKey);
                String email = r.getString("account_email");
                if ((email == null || email.isBlank()) && recipient == null) email = r.getString(
                    "email"
                );
                if (added && email != null && !email.isBlank()) jdbc.update(
                    "insert into email_outbox(recipient,subject,body,appointment_id,appointment_revision) values(?,?,?,?,?)",
                    email,
                    title,
                    message,
                    appointment,
                    r.getInt("revision")
                );
            },
            appointment
        );
    }

    @Override
    @Transactional
    public void notify(long userId, String kind, String title, String body, String link) {
        notify(userId, kind, title, body, link, null);
    }

    /** Enqueue portal and email atomically; a stable key makes repeat calls a no-op. */
    @Override
    @Transactional
    public void notify(
        long userId,
        String kind,
        String title,
        String body,
        String link,
        String dedupeKey
    ) {
        if (!insertNotice(userId, null, kind, title, body, link, dedupeKey)) return;
        var emails = jdbc.query(
            "select email from users where id=? and status='ACTIVE'",
            (r, n) -> r.getString(1),
            userId
        );
        if (
            !emails.isEmpty() && emails.getFirst() != null && !emails.getFirst().isBlank()
        ) jdbc.update(
            "insert into email_outbox(recipient,subject,body) values(?,?,?)",
            emails.getFirst(),
            title,
            body
        );
    }

    private boolean insertNotice(
        long user,
        Long appointment,
        String kind,
        String title,
        String body,
        String link,
        String key
    ) {
        if (key == null) return (
            jdbc.update(
                "insert into notifications(user_id,appointment_id,kind,title,body,link) values(?,?,?,?,?,?)",
                user,
                appointment,
                kind,
                title,
                body,
                link
            ) == 1
        );
        return (
            jdbc.update(
                "insert ignore into notifications(user_id,appointment_id,kind,title,body,link,dedupe_key) values(?,?,?,?,?,?,?)",
                user,
                appointment,
                kind,
                title,
                body,
                link,
                key
            ) == 1
        );
    }

    @Override
    public PageResponse<Notice> list(int page, int size) {
        return ClinicJdbc.queryPage(
            jdbc,
            "select * from notifications where user_id=? order by id desc",
            (r, n) ->
                new Notice(
                    r.getLong("id"),
                    r.getObject("appointment_id", Long.class),
                    r.getString("title"),
                    r.getString("body"),
                    r.getTimestamp("created_at").toInstant().toString(),
                    r.getTimestamp("read_at") != null,
                    r.getString("kind"),
                    r.getString("link")
                ),
            page,
            size,
            ClinicJdbc.user()
        );
    }

    @Override
    @Transactional
    public void read(long id) {
        if (
            jdbc.update(
                "update notifications set read_at=coalesce(read_at,UTC_TIMESTAMP(6)) where id=? and user_id=?",
                id,
                ClinicJdbc.user()
            ) == 0
        ) throw ClinicJdbc.missing();
    }
}
