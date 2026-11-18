package vn.antam.clinic.service.scheduler;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@EnableScheduling
public class EmailOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxWorker.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final boolean starttls;

    public EmailOutboxWorker(
        JdbcTemplate jdbc,
        PlatformTransactionManager manager,
        Clock clock,
        @Value("${app.smtp.host:}") String host,
        @Value("${app.smtp.port:1025}") int port,
        @Value("${app.smtp.username:}") String username,
        @Value("${app.smtp.password:}") String password,
        @Value("${app.smtp.from:no-reply@antam.local}") String from,
        @Value("${app.smtp.starttls:false}") boolean starttls
    ) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.clock = clock;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
        this.starttls = starttls;
    }

    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "email-outbox",
        lockAtMostFor = "30m",
        lockAtLeastFor = "2s"
    )
    @Scheduled(fixedDelayString = "${app.smtp.poll-delay-ms:30000}")
    public void dispatch() {
        transactions.executeWithoutResult(status -> discardStale());
        if (host.isBlank()) return;
        for (int i = 0; i < 10; i++) {
            Task task = transactions.execute(status -> claim());
            if (task == null) return;
            try {
                Integer current = jdbc.queryForObject(
                    "select count(*) from email_outbox o left join appointments a on a.id=o.appointment_id where o.id=? and o.status='SENDING' and (o.appointment_id is null or o.appointment_revision=a.revision)",
                    Integer.class,
                    task.id()
                );
                if (current == null || current == 0) {
                    transactions.executeWithoutResult(status -> discardStale());
                    continue;
                }
                send(task);
                transactions.executeWithoutResult(status ->
                    jdbc.update(
                        "update email_outbox set status='SENT',sent_at=?,last_error=null where id=?",
                        Timestamp.from(clock.instant()),
                        task.id()
                    )
                );
            } catch (Exception error) {
                log.warn("Email outbox {} failed: {}", task.id(), error.toString());
                String detail = error.toString();
                if (detail.length() > 500) detail = detail.substring(0, 500);
                String finalDetail = detail;
                transactions.executeWithoutResult(status ->
                    jdbc.update(
                        "update email_outbox set status=?,last_error=?,next_attempt_at=? where id=?",
                        task.attempts() >= 5 ? "FAILED" : "PENDING",
                        finalDetail,
                        Timestamp.from(
                            clock.instant().plusSeconds(Math.min(3600, 30L << task.attempts()))
                        ),
                        task.id()
                    )
                );
            }
        }
    }

    private void discardStale() {
        jdbc.update(
            "update email_outbox o join appointments a on a.id=o.appointment_id set o.status='SKIPPED',o.last_error='Lịch hẹn đã thay đổi' " +
                "where o.status in ('PENDING','SENDING') and o.appointment_revision<>a.revision"
        );
    }

    private Task claim() {
        discardStale();
        List<Task> tasks = jdbc.query(
            "select id,recipient,subject,body,attempts from email_outbox " +
                "where status in ('PENDING','SENDING') and next_attempt_at<=? " +
                "order by next_attempt_at,id limit 1 for update skip locked",
            (rs, row) ->
                new Task(
                    rs.getLong("id"),
                    rs.getString("recipient"),
                    rs.getString("subject"),
                    rs.getString("body"),
                    rs.getInt("attempts") + 1
                ),
            Timestamp.from(clock.instant())
        );
        if (tasks.isEmpty()) return null;
        Task task = tasks.getFirst();
        jdbc.update(
            "update email_outbox set status='SENDING',attempts=?,next_attempt_at=? where id=?",
            task.attempts(),
            Timestamp.from(clock.instant().plusSeconds(300)),
            task.id()
        );
        return task;
    }

    private void send(Task task) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", host);
        properties.setProperty("mail.smtp.port", String.valueOf(port));
        properties.setProperty("mail.smtp.auth", String.valueOf(!username.isBlank()));
        properties.setProperty("mail.smtp.starttls.enable", String.valueOf(starttls));
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "10000");
        Session session = username.isBlank()
            ? Session.getInstance(properties)
            : Session.getInstance(
                  properties,
                  new Authenticator() {
                      @Override
                      protected PasswordAuthentication getPasswordAuthentication() {
                          return new PasswordAuthentication(username, password);
                      }
                  }
              );
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(
            Message.RecipientType.TO,
            InternetAddress.parse(task.recipient(), false)
        );
        message.setSubject(task.subject(), StandardCharsets.UTF_8.name());
        message.setText(task.body(), StandardCharsets.UTF_8.name());
        Transport.send(message);
    }

    private record Task(long id, String recipient, String subject, String body, int attempts) {}
}
