package vn.antam.clinic.service.impl.realtime;

import static vn.antam.clinic.service.support.ClinicJdbc.*;
import static vn.antam.clinic.service.support.QueueDisplayText.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.realtime.QueueDisplayService;

@Service
public class QueueDisplayServiceImpl implements QueueDisplayService {

    private final JdbcTemplate db;

    public QueueDisplayServiceImpl(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public boolean enabled() {
        Integer enabled = db.queryForObject(
            "select queue_display_enabled from clinic_settings where id=1",
            Integer.class
        );
        return enabled != null && enabled == 1;
    }

    @Override
    public void requireEnabled() {
        if (!enabled()) throw forbidden();
    }

    @Override
    @Transactional(readOnly = true)
    public Board board(Set<Long> roomIds) {
        requireEnabled();
        var settings = db.queryForMap(
            "select clinic_name,queue_display_mask_names from clinic_settings where id=1"
        );
        boolean mask = ((Number) settings.get("queue_display_mask_names")).intValue() != 0;
        DateWindow window = dates(
            LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),
            LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
        );
        // Deliberately select only display fields. No appointment code, patient ID or medical data.
        List<Map<String, Object>> rows = db.queryForList(
            """
            select r.id room_id,r.name room_name,a.queue_number,p.full_name,a.called_at,a.call_count,e.status encounter_status
            from appointments a join appointment_slots s on s.id=a.slot_id
            join rooms r on r.id=s.room_id join patient_profiles p on p.id=a.patient_id
            left join encounters e on e.appointment_id=a.id
            where r.active=1 and a.status='CHECKED_IN' and a.queue_number is not null
            and a.checked_in_at>=? and a.checked_in_at<?
            and (e.status is null or e.status in ('WAITING','IN_PROGRESS'))
            order by r.id,a.queue_number,a.checked_in_at
            """,
            window.from(),
            window.to()
        );
        List<Map<String, Object>> roomRows = db.queryForList(
            "select id,name from rooms where active=1 order by id"
        );
        List<RoomBoard> boards = new ArrayList<>();
        for (var room : roomRows) {
            long roomId = ((Number) room.get("id")).longValue();
            if (!roomIds.isEmpty() && !roomIds.contains(roomId)) continue;
            List<Map<String, Object>> tickets = rows
                .stream()
                .filter(r -> ((Number) r.get("room_id")).longValue() == roomId)
                .toList();
            if (roomIds.isEmpty() && tickets.isEmpty()) continue;
            var current = tickets
                .stream()
                .filter(r -> r.get("called_at") != null)
                .max(
                    Comparator.comparing(r ->
                        vn.antam.clinic.service.support.ClinicJdbc.instant(r.get("called_at"))
                    )
                )
                .orElse(null);
            List<Ticket> next = tickets
                .stream()
                .filter(
                    r ->
                        r != current &&
                        (r.get("encounter_status") == null ||
                            "WAITING".equals(r.get("encounter_status")))
                )
                .limit(5)
                .map(r -> ticket(r, mask))
                .toList();
            boards.add(
                new RoomBoard(
                    roomId,
                    String.valueOf(room.get("name")),
                    current == null ? null : ticket(current, mask),
                    next
                )
            );
        }
        return new Board(
            String.valueOf(settings.get("clinic_name")),
            Instant.now().toString(),
            boards
        );
    }

    private Ticket ticket(Map<String, Object> row, boolean mask) {
        String name = String.valueOf(row.get("full_name"));
        var at = vn.antam.clinic.service.support.ClinicJdbc.instant(row.get("called_at"));
        return new Ticket(
            ((Number) row.get("queue_number")).intValue(),
            mask ? maskName(name) : name,
            at == null ? null : at.toString(),
            ((Number) row.get("call_count")).intValue()
        );
    }
}
