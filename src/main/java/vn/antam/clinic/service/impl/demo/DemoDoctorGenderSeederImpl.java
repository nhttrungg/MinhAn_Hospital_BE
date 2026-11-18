package vn.antam.clinic.service.impl.demo;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.demo.DemoDoctorGenderSeeder;

/** Adds declared gender metadata only to unchanged fictional directory fixtures. */
@Service
public class DemoDoctorGenderSeederImpl implements DemoDoctorGenderSeeder {

    private static final String DATASET = "outpatient-doctor-genders-v1";
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DemoDoctorGenderSeederImpl(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
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
        for (int number = 1; number <= 54; number++) jdbc.update(
            "update doctor_profiles dp join staff st on st.id=dp.staff_id join users u on u.id=st.user_id set dp.gender=? where u.username=? and st.employee_code=? and binary u.full_name=binary ? and dp.gender is null",
            HospitalDirectorySeederImpl.fixtureGender(number),
            "mockbs%03d".formatted(number),
            "MOCK-BS%03d".formatted(number),
            HospitalDirectorySeederImpl.fixtureName(number)
        );
        jdbc.update(
            "insert into audit_logs(action,entity_type,entity_id,detail) values('MOCK_GENDER_UPDATE','DEMO',?,?)",
            DATASET,
            "Bổ sung giới tính khai báo trong fixture bác sĩ giả lập; giữ hồ sơ đã biên tập và giới tính đã khai báo."
        );
        jdbc.update(
            "update mock_data_runs set completed_at=? where dataset=?",
            Timestamp.from(clock.instant()),
            DATASET
        );
        return true;
    }
}
