package vn.antam.clinic.service.impl.demo;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.demo.DemoPortraitSeeder;

/** Assigns a distinct, local portrait to every seeded doctor without replacing custom photos. */
@Service
public class DemoPortraitSeederImpl implements DemoPortraitSeeder {

    static final String DATASET = "outpatient-portraits-v1";
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DemoPortraitSeederImpl(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    static String path(int ordinal) {
        if (ordinal < 1 || ordinal > 56) throw new IllegalArgumentException("Unknown demo doctor");
        return "/images/doctors/doctor-asian-%03d.jpg".formatted(ordinal);
    }

    @Override
    @Transactional
    public boolean initialize() {
        jdbc.update(
            "insert into mock_data_runs(dataset,initialized_on) values(?,?) on duplicate key update dataset=dataset",
            DATASET,
            LocalDate.now(clock)
        );
        if (
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "select completed_at is not null from mock_data_runs where dataset=? for update",
                    Boolean.class,
                    DATASET
                )
            )
        ) return false;
        assign("bacsi01", 1);
        assign("bacsi02", 2);
        for (int number = 1; number <= 54; number++) assign(
            "mockbs%03d".formatted(number),
            number + 2
        );
        jdbc.update(
            "insert into audit_logs(action,entity_type,entity_id,detail) values('MOCK_PORTRAIT_UPDATE','DEMO',?,?)",
            DATASET,
            "Gán ảnh minh họa châu Á riêng cho 56 bác sĩ demo; giữ ảnh đã biên tập và dữ liệu khám."
        );
        jdbc.update(
            "update mock_data_runs set completed_at=? where dataset=?",
            Timestamp.from(clock.instant()),
            DATASET
        );
        return true;
    }

    private void assign(String username, int ordinal) {
        // Upgrade only blanks or the eight exact legacy pool paths; never match a custom filename.
        jdbc.update(
            "update doctor_profiles dp join staff s on s.id=dp.staff_id join users u on u.id=s.user_id set dp.photo_url=? " +
                "where u.username=? and (dp.photo_url is null or trim(dp.photo_url)='' or dp.photo_url in " +
                "('/images/doctors/doctor-male-01.jpg','/images/doctors/doctor-male-02.jpg','/images/doctors/doctor-male-03.jpg','/images/doctors/doctor-male-04.jpg'," +
                "'/images/doctors/doctor-female-01.jpg','/images/doctors/doctor-female-02.jpg','/images/doctors/doctor-female-03.jpg','/images/doctors/doctor-female-04.jpg'))",
            path(ordinal),
            username
        );
    }
}
