package vn.antam.clinic.service.impl.demo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.demo.DemoDescriptionSeeder;
import vn.antam.clinic.service.demo.DemoDoctorGenderSeeder;
import vn.antam.clinic.service.demo.DemoPortraitSeeder;
import vn.antam.clinic.service.demo.HospitalDirectorySeeder;
import vn.antam.clinic.service.demo.MockDataSeeder;
import vn.antam.clinic.service.demo.PharmacyDemoSeeder;
import vn.antam.clinic.service.support.ClinicJdbc;

/** Fictional, opt-in demo fixtures. All writes and the completion marker commit together. */
@Service
public class MockDataSeederImpl implements MockDataSeeder {

    static final String DATASET = "outpatient-demo-v1";
    private static final String NOTE = "Dữ liệu demo khởi tạo";
    private static final List<LocalTime> TIMES = List.of(
        LocalTime.of(8, 0),
        LocalTime.of(8, 30),
        LocalTime.of(9, 0),
        LocalTime.of(14, 0),
        LocalTime.of(14, 30),
        LocalTime.of(15, 0)
    );
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final HospitalDirectorySeeder directory;
    private final DemoDescriptionSeeder descriptions;
    private final DemoPortraitSeeder portraits;
    private final DemoDoctorGenderSeeder genders;
    private final PharmacyDemoSeeder pharmacy;

    public MockDataSeederImpl(
        JdbcTemplate jdbc,
        Clock clock,
        HospitalDirectorySeeder directory,
        DemoDescriptionSeeder descriptions,
        DemoPortraitSeeder portraits,
        DemoDoctorGenderSeeder genders,
        PharmacyDemoSeeder pharmacy
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.directory = directory;
        this.descriptions = descriptions;
        this.portraits = portraits;
        this.genders = genders;
        this.pharmacy = pharmacy;
    }

    private record Doctor(
        long staffId,
        long userId,
        long roomId,
        long serviceId,
        String serviceName,
        String unit,
        BigDecimal price
    ) {}

    @Override
    @Transactional
    public boolean initialize() {
        boolean directoryCreated = directory.initialize();
        boolean outpatientCreated = initializeOutpatient();
        boolean contentUpdated = descriptions.initialize();
        boolean portraitsUpdated = portraits.initialize();
        boolean gendersUpdated = genders.initialize();
        boolean pharmacyCreated = pharmacy.initialize();
        return (
            directoryCreated ||
            outpatientCreated ||
            contentUpdated ||
            portraitsUpdated ||
            gendersUpdated ||
            pharmacyCreated
        );
    }

    private boolean initializeOutpatient() {
        LocalDate today = LocalDate.now(clock);
        // The unique marker serializes concurrent startups. A failed seed rolls it back too.
        jdbc.update(
            "insert into mock_data_runs(dataset,initialized_on) values(?,?) " +
                "on duplicate key update dataset=dataset",
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

        List<Long> patients = createPatients();
        long medication = createMedications();
        long nurse = jdbc.queryForObject(
            "select s.id from staff s join users u on u.id=s.user_id " +
                "where u.username='dieuduong01'",
            Long.class
        );
        long cashier = jdbc.queryForObject(
            "select id from users where username='thungan01'",
            Long.class
        );
        List<Doctor> doctors = List.of(
            doctor("bacsi01", "P101", "KB-NOI", today),
            doctor("bacsi02", "P102", "KB-NGOAI", today)
        );

        for (int d = 0; d < doctors.size(); d++) {
            Doctor doctor = doctors.get(d);
            // Use the same doctor/room locks as normal schedule creation.
            jdbc.queryForObject(
                "select id from staff where id=? for update",
                Long.class,
                doctor.staffId()
            );
            jdbc.queryForObject(
                "select id from rooms where id=? for update",
                Long.class,
                doctor.roomId()
            );
            createTemplates(doctor);
            for (int day = 1; day <= 14; day++) {
                for (int time = 0; time < TIMES.size(); time++) {
                    Long slot = createSlot(
                        doctor,
                        today.plusDays(day),
                        TIMES.get(time),
                        "PUBLISHED"
                    );
                    if (slot != null && day <= 2 && time == 0) {
                        long patient = patients.get(day == 1 && d == 0 ? 0 : 1 + d * 2 + day);
                        createAppointment(
                            doctor,
                            patient,
                            slot,
                            "CONFIRMED",
                            null,
                            at(today.minusDays(1), LocalTime.of(16, 0)),
                            null
                        );
                    }
                }
            }

            // Historical visits make records, prescriptions and revenue reports reviewable.
            for (int day = 1; day <= 4; day++) {
                LocalDate date = today.minusDays(day);
                LocalTime time = LocalTime.of(10, 0);
                Long slot = createSlot(doctor, date, time, "PUBLISHED");
                if (slot == null) continue;
                String status = day == 3 ? "CANCELLED" : day == 4 ? "NO_SHOW" : "COMPLETED";
                long patient = patients.get(day == 1 && d == 0 ? 0 : 5 + d * 2 + (day % 2));
                Timestamp checkedIn = status.equals("COMPLETED")
                    ? at(date, time.minusMinutes(10))
                    : null;
                Long appointment = createAppointment(
                    doctor,
                    patient,
                    slot,
                    status,
                    checkedIn == null ? null : 1,
                    at(date.minusDays(1), LocalTime.of(16, 0)),
                    checkedIn
                );
                if (appointment != null && checkedIn != null) {
                    createVisit(
                        doctor,
                        patient,
                        appointment,
                        nurse,
                        cashier,
                        medication,
                        "COMPLETED",
                        "PAID",
                        checkedIn,
                        at(date, time),
                        at(date, time.plusMinutes(20))
                    );
                }
            }

            // Today's queues include unpaid/part-paid waiting visits and a paid active visit.
            for (int visit = 0; visit < 4; visit++) {
                Long slot = createSlot(doctor, today, LocalTime.of(8 + visit, 0), "PUBLISHED");
                if (slot == null) continue;
                long patient = patients.get(1 + d * 4 + visit);
                int queue = jdbc.queryForObject(
                    "select coalesce(max(a.queue_number),0)+1 from appointments a " +
                        "join appointment_slots sl on sl.id=a.slot_id where sl.doctor_staff_id=? " +
                        "and sl.starts_at>=? and sl.starts_at<?",
                    Integer.class,
                    doctor.staffId(),
                    at(today, LocalTime.MIDNIGHT),
                    at(today.plusDays(1), LocalTime.MIDNIGHT)
                );
                Timestamp now = Timestamp.from(clock.instant());
                Long appointment = createAppointment(
                    doctor,
                    patient,
                    slot,
                    "CHECKED_IN",
                    queue,
                    at(today.minusDays(1), LocalTime.of(16, 0)),
                    now
                );
                if (appointment == null) continue;
                String encounterStatus =
                    visit < 2 ? "WAITING" : visit == 2 ? "IN_PROGRESS" : "WAITING_RESULTS";
                if (
                    encounterStatus.equals("IN_PROGRESS") &&
                    jdbc.queryForObject(
                        "select count(*) from encounters where doctor_staff_id=? and status='IN_PROGRESS'",
                        Integer.class,
                        doctor.staffId()
                    ) > 0
                ) encounterStatus = "WAITING_RESULTS";
                createVisit(
                    doctor,
                    patient,
                    appointment,
                    nurse,
                    cashier,
                    medication,
                    encounterStatus,
                    visit == 0 ? "OPEN" : visit == 1 ? "PARTIAL" : "PAID",
                    now,
                    visit < 2 ? null : now,
                    null
                );
            }

            for (int i = 0; i < 3; i++) {
                String status = List.of("DRAFT", "SUBMITTED", "REJECTED").get(i);
                createSlot(doctor, today.plusDays(1), LocalTime.of(16, i * 20), status);
            }
            Long closed = createSlot(doctor, today.plusDays(3), LocalTime.of(16, 0), "CLOSED");
            if (closed != null) {
                createAppointment(
                    doctor,
                    patients.get(d == 0 ? 0 : 10),
                    closed,
                    "NEEDS_RESCHEDULE",
                    null,
                    at(today.minusDays(1), LocalTime.of(16, 0)),
                    null
                );
            }
        }

        jdbc.update(
            "update clinic_settings set address=coalesce(nullif(trim(address),''),?), " +
                "phone=coalesce(nullif(trim(phone),''),?),email=coalesce(nullif(trim(email),''),?) where id=1",
            "Quốc lộ 1A, Quỳnh Giang, Quỳnh Lưu, Nghệ An",
            "1900 866 823",
            "benhvienminhan@gmail.com"
        );
        jdbc.update(
            "insert into audit_logs(action,entity_type,entity_id,detail) values('MOCK_INIT','DEMO',?,?)",
            DATASET,
            "Khởi tạo bộ dữ liệu minh họa ngoại trú, không gửi email."
        );
        jdbc.update(
            "update mock_data_runs set completed_at=? where dataset=?",
            Timestamp.from(clock.instant()),
            DATASET
        );
        return true;
    }

    private List<Long> createPatients() {
        List<Long> ids = new ArrayList<>();
        ids.add(
            jdbc.queryForObject(
                "select p.id from patient_profiles p join users u on u.id=p.user_id " +
                    "where u.username='benhnhan01'",
                Long.class
            )
        );
        String[] names = {
            "Nguyễn Văn An",
            "Lê Thị Bình",
            "Trần Minh Châu",
            "Phạm Quốc Dũng",
            "Hoàng Ngọc Hà",
            "Võ Thanh Hùng",
            "Đặng Thu Lan",
            "Bùi Gia Minh",
            "Đỗ Hải Nam",
            "Ngô Thảo Nhi",
            "Phan Đức Phúc",
        };
        for (int i = 0; i < names.length; i++) {
            ids.add(
                insert(
                    "insert into patient_profiles(patient_code,full_name,phone,email,date_of_birth," +
                        "gender,address,allergies,medical_history,emergency_contact) values(?,?,?,?,?,?,?,?,?,?)",
                    "MOCK-BN%03d".formatted(i + 1),
                    names[i],
                    "090900%04d".formatted(i + 1),
                    "patient%02d@example.com".formatted(i + 1),
                    LocalDate.of(1980 + i * 2, i + 1, 15),
                    i % 2 == 0 ? "MALE" : "FEMALE",
                    "TP. Hồ Chí Minh (hồ sơ demo)",
                    "Chưa ghi nhận (minh họa)",
                    "Tiền sử bệnh minh họa",
                    "Người thân (demo)"
                )
            );
        }
        return ids;
    }

    private long createMedications() {
        long first = 0;
        for (int i = 1; i <= 6; i++) {
            long id = insert(
                "insert into medications(code,name,ingredient,strength,form,unit) values(?,?,?,?,?,?)",
                "MOCK-TH%03d".formatted(i),
                "Thuốc minh họa " + i,
                "Hoạt chất mẫu " + i,
                "Hàm lượng minh họa",
                "Viên",
                "viên"
            );
            if (i == 1) first = id;
        }
        return first;
    }

    private Doctor doctor(String username, String roomCode, String serviceCode, LocalDate date) {
        return jdbc.queryForObject(
            """
            select st.id,st.user_id,rm.id room_id,sv.id service_id,sv.name,sv.unit,p.unit_price
            from staff st join users u on u.id=st.user_id
            join doctor_profiles dp on dp.staff_id=st.id
            join specialties sp on sp.id=dp.specialty_id and sp.active=1
            join departments department on department.id=sp.department_id and department.active=1
            join doctor_services ds on ds.doctor_staff_id=st.id
            join services sv on sv.id=ds.service_id
            join service_prices p on p.service_id=sv.id
            join rooms rm on rm.code=? and rm.room_type='CLINIC'
            join departments room_department on room_department.id=rm.department_id
                and room_department.code='KB' and room_department.active=1
            where u.username=? and sv.code=? and st.active=1 and u.status='ACTIVE'
              and sv.active=1 and rm.active=1 and p.effective_from<=?
              and (p.effective_to is null or p.effective_to>=?)
              and exists(select 1 from user_roles ur join roles r on r.id=ur.role_id
                         where ur.user_id=u.id and r.code='DOCTOR')
            order by p.effective_from desc limit 1
            """,
            (rs, row) ->
                new Doctor(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getLong("room_id"),
                    rs.getLong("service_id"),
                    rs.getString("name"),
                    rs.getString("unit"),
                    rs.getBigDecimal("unit_price")
                ),
            roomCode,
            username,
            serviceCode,
            date,
            date
        );
    }

    private void createTemplates(Doctor doctor) {
        for (int weekday = 1; weekday <= 5; weekday++) {
            for (LocalTime time : List.of(LocalTime.of(8, 0), LocalTime.of(14, 0))) {
                jdbc.update(
                    "insert into weekly_slot_templates(doctor_staff_id,weekday,starts_time,ends_time,room_id,service_id) " +
                        "select ?,?,?,?,?,? where not exists(select 1 from weekly_slot_templates " +
                        "where doctor_staff_id=? and weekday=? and starts_time=? and ends_time=? and room_id=? and service_id=?)",
                    doctor.staffId(),
                    weekday,
                    time,
                    time.plusMinutes(30),
                    doctor.roomId(),
                    doctor.serviceId(),
                    doctor.staffId(),
                    weekday,
                    time,
                    time.plusMinutes(30),
                    doctor.roomId(),
                    doctor.serviceId()
                );
            }
        }
    }

    private Long createSlot(Doctor doctor, LocalDate date, LocalTime time, String status) {
        Timestamp start = at(date, time);
        Timestamp end = at(date, time.plusMinutes(status.equals("PUBLISHED") ? 30 : 20));
        // Never reuse or overwrite an existing slot, including a reservation owned by real data.
        if (
            jdbc.queryForObject(
                "select count(*) from appointment_slots where " +
                    "status in ('DRAFT','SUBMITTED','PUBLISHED','CLOSE_REQUESTED') " +
                    "and (doctor_staff_id=? or room_id=?) and starts_at<? and ends_at>?",
                Integer.class,
                doctor.staffId(),
                doctor.roomId(),
                end,
                start
            ) > 0
        ) return null;
        return insert(
            "insert into appointment_slots(doctor_staff_id,room_id,service_id,starts_at,ends_at,status,note,rejection_reason) " +
                "values(?,?,?,?,?,?,?,?)",
            doctor.staffId(),
            doctor.roomId(),
            doctor.serviceId(),
            start,
            end,
            status,
            NOTE,
            status.equals("REJECTED") ? "Lịch minh họa bị từ chối" : null
        );
    }

    private Long createAppointment(
        Doctor doctor,
        long patient,
        long slot,
        String status,
        Integer queue,
        Timestamp bookedAt,
        Timestamp checkedIn
    ) {
        jdbc.queryForObject(
            "select id from patient_profiles where id=? for update",
            Long.class,
            patient
        );
        if (
            !List.of("CANCELLED", "NEEDS_RESCHEDULE").contains(status) &&
            jdbc.queryForObject(
                "select count(*) from slot_reservations r " +
                    "join appointments a on a.id=r.appointment_id join appointment_slots existing on existing.id=r.slot_id " +
                    "join appointment_slots candidate on candidate.id=? where a.patient_id=? " +
                    "and existing.starts_at<candidate.ends_at and existing.ends_at>candidate.starts_at",
                Integer.class,
                slot,
                patient
            ) > 0
        ) return null;
        long appointment = insert(
            "insert into appointments(appointment_code,patient_id,slot_id,status,symptoms," +
                "booked_price,queue_number,booked_at,checked_in_at,cancellation_reason) values(?,?,?,?,?,?,?,?,?,?)",
            "TMP-MOCK-" + slot,
            patient,
            slot,
            status,
            "Nhu cầu khám minh họa",
            doctor.price(),
            queue,
            bookedAt,
            checkedIn,
            status.equals("CANCELLED") ? "Bệnh nhân đổi kế hoạch (demo)" : null
        );
        String code = "MOCK-LH%010d".formatted(appointment);
        jdbc.update("update appointments set appointment_code=? where id=?", code, appointment);
        if (!List.of("CANCELLED", "NEEDS_RESCHEDULE").contains(status)) {
            jdbc.update(
                "insert into slot_reservations(slot_id,appointment_id) values(?,?)",
                slot,
                appointment
            );
        }
        jdbc.update(
            "insert into appointment_changes(appointment_id,new_slot_id,actor_user_id,action,occurred_at) " +
                "select ?,?,user_id,'BOOK',? from patient_profiles where id=?",
            appointment,
            slot,
            bookedAt,
            patient
        );
        if (!status.equals("CONFIRMED")) {
            String action = switch (status) {
                case "CANCELLED" -> "CANCEL";
                case "CHECKED_IN", "COMPLETED" -> "CHECK_IN";
                default -> status;
            };
            jdbc.update(
                "insert into appointment_changes(appointment_id,old_slot_id,action,occurred_at) values(?,?,?,?)",
                appointment,
                slot,
                action,
                checkedIn == null ? bookedAt : checkedIn
            );
        }
        jdbc.update(
            "insert into notifications(user_id,appointment_id,title,body,created_at) " +
                "select user_id,?,'Lịch hẹn minh họa',?,? from patient_profiles where id=? and user_id is not null",
            appointment,
            code + " — " + status + " (dữ liệu demo)",
            bookedAt,
            patient
        );
        return appointment;
    }

    private void createVisit(
        Doctor doctor,
        long patient,
        long appointment,
        long nurse,
        long cashier,
        long medication,
        String status,
        String invoiceStatus,
        Timestamp checkedIn,
        Timestamp started,
        Timestamp completed
    ) {
        long encounter = insert(
            "insert into encounters(appointment_id,patient_id,doctor_staff_id,nurse_staff_id," +
                "status,chief_complaint,medical_history,allergies,vitals,diagnosis,conclusion,instructions," +
                "follow_up_date,started_at,completed_at,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            appointment,
            patient,
            doctor.staffId(),
            nurse,
            status,
            "Triệu chứng minh họa",
            "Tiền sử minh họa",
            "Chưa ghi nhận (minh họa)",
            "Mạch: 75; huyết áp: 120/80; nhiệt độ: 36.8 (mẫu)",
            started == null ? null : "Chẩn đoán minh họa",
            completed == null ? null : "Hoàn tất lượt khám demo",
            "Nội dung mẫu để kiểm tra giao diện, không phải hướng dẫn điều trị.",
            completed == null ? null : LocalDate.now(clock).plusDays(7),
            started,
            completed,
            checkedIn
        );
        BigDecimal amount = invoiceStatus.equals("OPEN")
            ? BigDecimal.ZERO
            : invoiceStatus.equals("PARTIAL")
              ? doctor.price().divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.DOWN)
              : doctor.price();
        String actualInvoiceStatus =
            amount.compareTo(doctor.price()) == 0
                ? "PAID"
                : amount.signum() == 0
                  ? "OPEN"
                  : "PARTIAL";
        long invoice = insert(
            "insert into invoices(invoice_code,appointment_id,patient_id,status,created_at) values(?,?,?,?,?)",
            "MOCK-HD%010d".formatted(appointment),
            appointment,
            patient,
            actualInvoiceStatus,
            checkedIn
        );
        jdbc.update(
            "insert into invoice_lines(invoice_id,source_type,source_id,description_snapshot,unit_snapshot,quantity,unit_price) " +
                "values(?,'EXAMINATION',?,?,?,1,?)",
            invoice,
            appointment,
            doctor.serviceName(),
            doctor.unit(),
            doctor.price()
        );
        if (amount.signum() > 0) {
            jdbc.update(
                "insert into receipts(receipt_code,invoice_id,cashier_user_id,amount,method,recorded_at) values(?,?,?,?,?,?)",
                "MOCK-PT%010d".formatted(appointment),
                invoice,
                cashier,
                amount,
                invoiceStatus.equals("PARTIAL") ? "TRANSFER" : "CASH",
                started == null ? checkedIn : started
            );
        }
        if (started != null) {
            long prescription = insert(
                "insert into prescriptions(encounter_id,version,status,author_user_id,created_at,finalized_at) " +
                    "values(?,1,?,?,?,?)",
                encounter,
                completed == null ? "DRAFT" : "FINAL",
                doctor.userId(),
                started,
                completed
            );
            jdbc.update(
                "insert into prescription_lines(prescription_id,medication_id,name_snapshot,strength_snapshot,unit_snapshot," +
                    "dose,route,frequency,days,quantity,instructions) " +
                    "select ?,id,name,strength,unit,'Liều minh họa','Đường dùng mẫu','Tần suất mẫu',1,1,? " +
                    "from medications where id=?",
                prescription,
                "Đơn thuốc demo, không dùng để điều trị.",
                medication
            );
        }
        if (completed != null) {
            jdbc.update(
                "insert into appointment_changes(appointment_id,actor_user_id,action,occurred_at) values(?,?,'COMPLETE',?)",
                appointment,
                doctor.userId(),
                completed
            );
        }
    }

    private long insert(String sql, Object... args) {
        return ClinicJdbc.insert(jdbc, sql, args);
    }

    private Timestamp at(LocalDate date, LocalTime time) {
        return Timestamp.from(date.atTime(time).atZone(clock.getZone()).toInstant());
    }
}
