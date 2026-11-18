package vn.antam.clinic.service.scheduler;

import java.sql.Timestamp;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.antam.clinic.service.booking.WaitlistService;

@Component
public class WaitlistDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(WaitlistDeliveryWorker.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final WaitlistService waitlist;

    public WaitlistDeliveryWorker(JdbcTemplate jdbc, Clock clock, WaitlistService waitlist) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.waitlist = waitlist;
    }

    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "waitlist-delivery",
        lockAtMostFor = "30m",
        lockAtLeastFor = "2s"
    )
    @Scheduled(fixedDelayString = "${app.waitlist.retry-delay-ms:600000}")
    public void retry() {
        // Recovery if a node stops after booking commit but before event delivery.
        var pending = jdbc.queryForList(
            "select c.old_slot_id,concat('slot:',c.old_slot_id,':change:',c.id) release_key from appointment_changes c join appointment_slots sl on sl.id=c.old_slot_id left join waitlist_slot_releases r on r.release_key=concat('slot:',c.old_slot_id,':change:',c.id) where c.action in ('CANCEL','RESCHEDULE') and r.release_key is null and sl.status='PUBLISHED' and sl.starts_at>? and not exists(select 1 from slot_reservations sr where sr.slot_id=sl.id) and c.id=(select max(c2.id) from appointment_changes c2 where c2.old_slot_id=c.old_slot_id and c2.action in ('CANCEL','RESCHEDULE')) order by c.id limit 100",
            Timestamp.from(clock.instant())
        );
        for (var release : pending) {
            long slot = ((Number) release.get("old_slot_id")).longValue();
            try {
                waitlist.slotFreed(slot, String.valueOf(release.get("release_key")));
            } catch (RuntimeException error) {
                log.error("Waitlist retry failed for slot {}", slot, error);
            }
        }
    }
}
