package vn.antam.clinic.service.event;

import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.antam.clinic.domain.event.ClinicEvent;

@Component
public class ClinicEventPublisher {

    private final JdbcTemplate db;
    private final ApplicationEventPublisher events;

    public ClinicEventPublisher(JdbcTemplate db, ApplicationEventPublisher events) {
        this.db = db;
        this.events = events;
    }

    public void queueUpdated(long appointmentId) {
        appointment("queue.updated", appointmentId);
    }

    public void encounterUpdated(long appointmentId) {
        appointment("encounter.updated", appointmentId);
    }

    private void appointment(String type, long id) {
        db.query(
            "select s.room_id,s.doctor_staff_id,coalesce(p.user_id,p.owner_user_id) user_id from appointments a join appointment_slots s on s.id=a.slot_id join patient_profiles p on p.id=a.patient_id where a.id=?",
            rs -> {
                Long userId = rs.getObject("user_id", Long.class);
                events.publishEvent(
                    new ClinicEvent(
                        type,
                        rs.getLong("room_id"),
                        rs.getLong("doctor_staff_id"),
                        userId,
                        Map.of()
                    )
                );
            },
            id
        );
    }
}
