package vn.antam.clinic.service.impl.realtime;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.event.ClinicEventPublisher;
import vn.antam.clinic.service.realtime.QueueCallService;

@Service
public class QueueCallServiceImpl implements QueueCallService {

    private final JdbcTemplate db;
    private final ClinicEventPublisher events;

    public QueueCallServiceImpl(JdbcTemplate db, ClinicEventPublisher events) {
        this.db = db;
        this.events = events;
    }

    @Override
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public Map<String, Object> state(long appointmentId) {
        require("DOCTOR");
        var rows = db.queryForList(
            "select a.id,a.queue_number queueNumber,a.call_count callCount,a.called_at calledAt from appointments a join appointment_slots s on s.id=a.slot_id where a.id=? and s.doctor_staff_id=?",
            appointmentId,
            staff(db)
        );
        if (rows.isEmpty()) throw forbidden();
        return rows.getFirst();
    }

    @Override
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional
    public Map<String, Object> call(long appointmentId) {
        require("DOCTOR");
        long doctorId = staff(db);
        var rows = db.queryForList(
            "select a.id from appointments a join appointment_slots s on s.id=a.slot_id where a.id=? and s.doctor_staff_id=? for update",
            appointmentId,
            doctorId
        );
        if (rows.isEmpty()) throw forbidden();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var window = dates(today, today);
        Integer waiting = db.queryForObject(
            """
            select count(*) from appointments a left join encounters e on e.appointment_id=a.id
            where a.id=? and a.status='CHECKED_IN' and a.queue_number is not null
            and a.checked_in_at>=? and a.checked_in_at<? and (e.status is null or e.status='WAITING')
            """,
            Integer.class,
            appointmentId,
            window.from(),
            window.to()
        );
        if (waiting == null || waiting == 0) throw conflict(
            "Chỉ gọi lượt đã tiếp nhận hôm nay và đang chờ khám."
        );
        db.update(
            "update appointments set called_at=UTC_TIMESTAMP(6),call_count=call_count+1 where id=?",
            appointmentId
        );
        events.queueUpdated(appointmentId);
        return db.queryForMap(
            "select id,queue_number queueNumber,call_count callCount,called_at calledAt from appointments where id=?",
            appointmentId
        );
    }
}
