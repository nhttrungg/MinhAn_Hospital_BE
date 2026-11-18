package vn.antam.clinic.service.impl.demo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.demo.HospitalDirectorySeeder;
import vn.antam.clinic.service.support.ClinicJdbc;

/** Fictional outpatient directory inspired by university-hospital specialty groups.
 * No real clinician identities, affiliation, contact information or hospital prices are imported. */
@Service
public class HospitalDirectorySeederImpl implements HospitalDirectorySeeder {

    static final String DATASET = "outpatient-directory-v1";
    private static final String NOTE = "Dữ liệu danh mục demo mở rộng";
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PasswordEncoder passwords;

    record SpecialtyFixture(String code, String name, String description, int price) {}

    static final List<SpecialtyFixture> SPECIALTIES = List.of(
        new SpecialtyFixture(
            "TIM",
            "Tim mạch",
            "Khám và tư vấn sức khỏe tim mạch, theo dõi huyết áp.",
            250000
        ),
        new SpecialtyFixture(
            "TH",
            "Tiêu hóa - Gan mật",
            "Khám bệnh lý tiêu hóa và tư vấn theo dõi gan mật.",
            250000
        ),
        new SpecialtyFixture(
            "NT",
            "Nội tiết - Đái tháo đường",
            "Khám nội tiết, tư vấn theo dõi đường huyết và tuyến giáp.",
            250000
        ),
        new SpecialtyFixture("HH", "Hô hấp", "Khám và tư vấn theo dõi các vấn đề hô hấp.", 220000),
        new SpecialtyFixture(
            "CXK",
            "Cơ xương khớp",
            "Khám đau khớp, tư vấn theo dõi sức khỏe cơ xương khớp.",
            250000
        ),
        new SpecialtyFixture(
            "TK",
            "Thần kinh",
            "Khám và tư vấn các vấn đề thần kinh ngoại trú.",
            280000
        ),
        new SpecialtyFixture(
            "TNT",
            "Thận - Tiết niệu",
            "Khám và tư vấn theo dõi sức khỏe thận, đường tiết niệu.",
            250000
        ),
        new SpecialtyFixture(
            "DL",
            "Da liễu",
            "Khám và tư vấn chăm sóc các vấn đề da liễu.",
            200000
        ),
        new SpecialtyFixture(
            "TMH",
            "Tai Mũi Họng",
            "Khám tai, mũi, họng và tư vấn chăm sóc ngoại trú.",
            220000
        ),
        new SpecialtyFixture("MAT", "Mắt", "Khám mắt và tư vấn chăm sóc thị lực.", 220000),
        new SpecialtyFixture(
            "RHM",
            "Răng Hàm Mặt",
            "Khám và tư vấn chăm sóc sức khỏe răng miệng.",
            200000
        ),
        new SpecialtyFixture(
            "SP",
            "Sản Phụ khoa",
            "Khám phụ khoa và tư vấn sức khỏe sinh sản.",
            280000
        ),
        new SpecialtyFixture("NHI", "Nhi khoa", "Khám và tư vấn theo dõi sức khỏe trẻ em.", 230000),
        new SpecialtyFixture(
            "UB",
            "Ung bướu",
            "Khám tư vấn và theo dõi ngoại trú chuyên khoa ung bướu.",
            300000
        ),
        new SpecialtyFixture("NAM", "Nam học", "Khám và tư vấn sức khỏe nam khoa.", 280000),
        new SpecialtyFixture(
            "CTCH",
            "Chấn thương chỉnh hình",
            "Khám và tư vấn các vấn đề chấn thương chỉnh hình.",
            280000
        ),
        new SpecialtyFixture(
            "PHCN",
            "Phục hồi chức năng",
            "Khám, đánh giá và tư vấn phục hồi chức năng.",
            220000
        ),
        new SpecialtyFixture(
            "DD",
            "Dinh dưỡng",
            "Khám và tư vấn dinh dưỡng theo nhu cầu sức khỏe.",
            200000
        )
    );

    private static final String[] FAMILIES = {
        "Nguyễn",
        "Trần",
        "Lê",
        "Phạm",
        "Hoàng",
        "Vũ",
        "Đặng",
        "Bùi",
        "Đỗ",
        "Hồ",
        "Ngô",
        "Dương",
        "Đinh",
        "Trịnh",
        "Mai",
        "Cao",
        "Lương",
        "Võ",
    };
    private static final String[] NAMES = {
        "Minh Anh",
        "Thu Hương",
        "Quang Huy",
        "Ngọc Linh",
        "Đức Thành",
        "Hải Yến",
        "Thanh Sơn",
        "Khánh Vy",
        "Tuấn Kiệt",
        "Phương Thảo",
        "Gia Khánh",
        "Bảo Ngọc",
        "Trung Hiếu",
        "Diệu Hằng",
        "Anh Dũng",
        "Thùy Chi",
        "Mạnh Cường",
        "Hồng Nhung",
    };
    private static final String[] TITLES = {
        "Bác sĩ chuyên khoa I",
        "Bác sĩ chuyên khoa II",
        "Thạc sĩ, bác sĩ",
    };

    // Declared metadata for fictional fixtures; never classify real people from names or portraits.
    static String fixtureGender(int number) {
        if (number < 1 || number > 54) throw new IllegalArgumentException(
            "Unknown fictional doctor fixture"
        );
        return java.util.Set.of(
            4,
            5,
            6,
            10,
            11,
            12,
            16,
            17,
            18,
            22,
            23,
            24,
            28,
            29,
            30,
            34,
            35,
            36,
            40,
            41,
            42,
            46,
            47,
            48,
            52,
            53,
            54
        ).contains(number)
            ? "FEMALE"
            : "MALE";
    }

    static String fixtureName(int number) {
        if (number < 1 || number > 54) throw new IllegalArgumentException(
            "Unknown fictional doctor fixture"
        );
        int index = (number - 1) / 3,
            member = (number - 1) % 3;
        return FAMILIES[index] + " " + NAMES[(index + member * 6) % NAMES.length];
    }

    public HospitalDirectorySeederImpl(JdbcTemplate jdbc, Clock clock, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.passwords = passwords;
    }

    @Override
    @Transactional
    public boolean initialize() {
        LocalDate today = LocalDate.now(clock);
        // A separate marker lets databases already initialized with the original demo receive this extension.
        jdbc.update(
            "insert into mock_data_runs(dataset,initialized_on) values(?,?) on duplicate key update dataset=dataset",
            DATASET,
            today
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
        String hash = passwords.encode("Demo@12345");
        long role = jdbc.queryForObject("select id from roles where code='DOCTOR'", Long.class);
        for (int index = 0; index < SPECIALTIES.size(); index++) {
            SpecialtyFixture fixture = SPECIALTIES.get(index);
            var copy = DemoDirectoryContent.forCode(fixture.code());
            long department = insert(
                "insert into departments(code,name,description) values(?,?,?)",
                "MOCK-K-" + fixture.code(),
                "Khoa " + fixture.name(),
                copy.department(fixture.name())
            );
            long specialty = insert(
                "insert into specialties(department_id,code,name,description) values(?,?,?,?)",
                department,
                "MOCK-CK-" + fixture.code(),
                fixture.name(),
                copy.specialty(fixture.name())
            );
            long room = insert(
                "insert into rooms(department_id,code,name,room_type,description) values(?,?,?,'CLINIC',?)",
                department,
                "MOCK-P-" + fixture.code(),
                "Phòng khám " + fixture.name(),
                copy.room(fixture.name())
            );
            long service = insert(
                "insert into services(code,name,category,unit,description) values(?,?,'EXAMINATION','lượt',?)",
                "MOCK-KB-" + fixture.code(),
                "Khám " + fixture.name(),
                copy.service(fixture.name())
            );
            jdbc.update(
                "insert into service_prices(service_id,unit_price,effective_from) values(?,?,?)",
                service,
                BigDecimal.valueOf(fixture.price()),
                today
            );
            for (int member = 0; member < 3; member++) {
                int number = index * 3 + member + 1;
                String username = "mockbs%03d".formatted(number);
                String fullName =
                    FAMILIES[index] + " " + NAMES[(index + member * 6) % NAMES.length];
                long user = insert(
                    "insert into users(username,password_hash,full_name,email,user_type,status) values(?,?,?,?,'STAFF','ACTIVE')",
                    username,
                    hash,
                    fullName,
                    username + "@example.com"
                );
                jdbc.update("insert into user_roles(user_id,role_id) values(?,?)", user, role);
                long doctor = insert(
                    "insert into staff(user_id,employee_code,department_id,title) values(?,?,?,?)",
                    user,
                    "MOCK-BS%03d".formatted(number),
                    department,
                    TITLES[member]
                );
                jdbc.update(
                    "insert into doctor_profiles(staff_id,specialty_id,bio,photo_url,gender) values(?,?,?,?,?)",
                    doctor,
                    specialty,
                    copy.biography(fullName, fixture.name(), member),
                    DemoDescriptionSeederImpl.photo(index, member),
                    fixtureGender(number)
                );
                jdbc.update(
                    "insert into doctor_services(doctor_staff_id,service_id) values(?,?)",
                    doctor,
                    service
                );
                createSchedule(doctor, room, service, member, today);
            }
        }
        jdbc.update(
            "insert into audit_logs(action,entity_type,entity_id,detail) values('MOCK_INIT','DEMO',?,?)",
            DATASET,
            "Bổ sung 18 chuyên khoa, 54 bác sĩ giả lập, phòng/dịch vụ và lịch ngoại trú 14 ngày; không gửi email."
        );
        jdbc.update(
            "update mock_data_runs set completed_at=? where dataset=?",
            Timestamp.from(clock.instant()),
            DATASET
        );
        return true;
    }

    private void createSchedule(long doctor, long room, long service, int member, LocalDate today) {
        // Three clinicians share one room at distinct hours, avoiding doctor and room conflicts.
        List<LocalTime> times = List.of(
            LocalTime.of(8 + member, 0),
            LocalTime.of(8 + member, 30),
            LocalTime.of(14 + member, 0),
            LocalTime.of(14 + member, 30)
        );
        jdbc.queryForObject("select id from staff where id=? for update", Long.class, doctor);
        jdbc.queryForObject("select id from rooms where id=? for update", Long.class, room);
        for (int weekday = 1; weekday <= 5; weekday++) {
            for (LocalTime time : times)
                jdbc.update(
                    "insert into weekly_slot_templates(doctor_staff_id,weekday,starts_time,ends_time,room_id,service_id) values(?,?,?,?,?,?)",
                    doctor,
                    weekday,
                    time,
                    time.plusMinutes(30),
                    room,
                    service
                );
        }
        for (int day = 1; day <= 14; day++) {
            for (LocalTime time : times) {
                Timestamp start = at(today.plusDays(day), time);
                Timestamp end = at(today.plusDays(day), time.plusMinutes(30));
                if (
                    jdbc.queryForObject(
                        "select count(*) from appointment_slots where status in ('DRAFT','SUBMITTED','PUBLISHED','CLOSE_REQUESTED') and (doctor_staff_id=? or room_id=?) and starts_at<? and ends_at>?",
                        Integer.class,
                        doctor,
                        room,
                        end,
                        start
                    ) > 0
                ) continue;
                jdbc.update(
                    "insert into appointment_slots(doctor_staff_id,room_id,service_id,starts_at,ends_at,status,note) values(?,?,?,?,?,'PUBLISHED',?)",
                    doctor,
                    room,
                    service,
                    start,
                    end,
                    NOTE
                );
            }
        }
    }

    private long insert(String sql, Object... values) {
        return ClinicJdbc.insert(jdbc, sql, values);
    }

    private Timestamp at(LocalDate date, LocalTime time) {
        return Timestamp.from(date.atTime(time).atZone(clock.getZone()).toInstant());
    }
}
