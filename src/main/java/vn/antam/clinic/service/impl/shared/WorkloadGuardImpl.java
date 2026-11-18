package vn.antam.clinic.service.impl.shared;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.antam.clinic.service.shared.WorkloadGuard;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
public class WorkloadGuardImpl implements WorkloadGuard {

    private final JdbcTemplate jdbc;

    public WorkloadGuardImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void check(String field, long id) {
        String table = switch (field) {
            case "doctor" -> "staff";
            case "room" -> "rooms";
            case "service" -> "services";
            case "specialty" -> "specialties";
            default -> throw new IllegalArgumentException("Unknown workload field");
        };
        jdbc.query(
            "select id from " + table + " where id=? for update",
            (rs, n) -> rs.getLong(1),
            id
        );
        String column = switch (field) {
            case "doctor" -> "sl.doctor_staff_id";
            case "room" -> "sl.room_id";
            case "service" -> "sl.service_id";
            case "specialty" -> "dp.specialty_id";
            default -> throw new IllegalArgumentException("Unknown workload field");
        };
        jdbc.query(
            "select sl.id from appointment_slots sl join doctor_profiles dp on dp.staff_id=sl.doctor_staff_id where " +
                column +
                "=? order by sl.id for update",
            (rs, n) -> rs.getLong(1),
            id
        );
        List<Blocker> blockers = jdbc.query(
            "select a.id,a.appointment_code,a.status from appointments a join appointment_slots sl on sl.id=a.slot_id " +
                "join doctor_profiles dp on dp.staff_id=sl.doctor_staff_id left join encounters e on e.appointment_id=a.id " +
                "where " +
                column +
                "=? and (a.status in ('CONFIRMED','CHECKED_IN') or (e.id is not null and e.status<>'COMPLETED')) order by a.appointment_code limit 20 for update",
            (rs, n) -> new Blocker(rs.getLong(1), rs.getString(2), rs.getString(3)),
            id
        );
        if (!blockers.isEmpty()) {
            Integer total = jdbc.queryForObject(
                "select count(*) from appointments a join appointment_slots sl on sl.id=a.slot_id join doctor_profiles dp on dp.staff_id=sl.doctor_staff_id left join encounters e on e.appointment_id=a.id where " +
                    column +
                    "=? and (a.status in ('CONFIRMED','CHECKED_IN') or (e.id is not null and e.status<>'COMPLETED'))",
                Integer.class,
                id
            );
            throw new ApiException(
                org.springframework.http.HttpStatus.CONFLICT,
                "Chưa thể ngưng hoạt động",
                "Còn lịch/lượt khám cần xử lý trước: " +
                    String.join(", ", blockers.stream().map(Blocker::code).toList()),
                java.util.Map.of(
                    "code",
                    "RESOURCE_IN_USE",
                    "blockers",
                    blockers,
                    "totalBlockers",
                    total
                )
            );
        }
    }
}
