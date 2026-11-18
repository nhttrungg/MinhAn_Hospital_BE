package vn.antam.clinic.service.impl.demo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.demo.VolumeDemoSeeder;
import vn.antam.clinic.service.support.ClinicJdbc;

/** Fictional, additive fixtures for pagination, reports and the complete outpatient workflow. */
@Service
public class VolumeDemoSeederImpl implements VolumeDemoSeeder {

    private static final String NOTE = "Dữ liệu mẫu mở rộng, không dùng điều trị";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String[] SURNAMES = { "Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Võ" };
    private static final String[] NAMES = {
        "Minh Anh",
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
        "Hải Nam",
        "Ngọc Hân",
        "Thanh Trúc",
    };
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PasswordEncoder passwords;

    public VolumeDemoSeederImpl(JdbcTemplate jdbc, Clock clock, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.passwords = passwords;
    }

    private record Patient(long id, long owner) {}

    private record Doctor(
        long id,
        long user,
        long room,
        long service,
        long specialty,
        String serviceName,
        BigDecimal price,
        List<LocalTime> times
    ) {}

    private record Lab(long room, long service, long technician, long nurse, long nurseUser) {}

    @Override
    @Transactional
    public boolean initialize() {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
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
        var doctors = doctors(today);
        if (doctors.size() != 54) throw new IllegalStateException(
            "Run the base mock directory before the volume dataset; 54 active fixture doctors are required."
        );
        var patients = patients(today);
        // Follow booking's patient -> slot lock order, including the existing demo account.
        for (long id : patients.stream().map(Patient::id).sorted().toList())
            jdbc.queryForObject(
                "select id from patient_profiles where id=? for update",
                Long.class,
                id
            );
        Lab lab = lab(today);
        long cashier = id("select id from users where username='thungan01'");
        var medications = jdbc.queryForList(
            "select id from medications where active=1 and code like 'MOCK-TH%' order by code",
            Long.class
        );
        var counters = jdbc.queryForList(
            "select id from pharmacy_counters where active=1 and code like 'MOCK-QT%' order by code",
            Long.class
        );
        if (medications.size() < 2 || counters.size() != 3) throw new IllegalStateException(
            "Base medication and pharmacy fixtures are required."
        );
        String icd = jdbc.queryForObject(
            "select code from icd10_codes where active=1 and code='Z00.00'",
            String.class
        );

        for (int d = 0; d < doctors.size(); d++) {
            Doctor doctor = doctors.get(d);
            jdbc.queryForObject(
                "select id from staff where id=? for update",
                Long.class,
                doctor.id()
            );
            jdbc.queryForObject(
                "select id from rooms where id=? for update",
                Long.class,
                doctor.room()
            );
            // Fill gaps only. Slots owned by earlier data are neither reused nor modified.
            for (int day = 1; day <= 30; day++) {
                for (int time = 0; time < doctor.times().size(); time++) {
                    LocalDate date = today.plusDays(day);
                    Long slot = slot(doctor, date, doctor.times().get(time), "PUBLISHED");
                    if (slot == null || time != 0 || day < 25 || day > 27) continue;
                    Patient patient = patients.get(d);
                    String status =
                        day == 25 ? "CONFIRMED" : day == 26 ? "CANCELLED" : "NEEDS_RESCHEDULE";
                    if (day == 27) jdbc.update(
                        "update appointment_slots set status='CLOSED' where id=?",
                        slot
                    );
                    appointment(
                        doctor,
                        patient,
                        slot,
                        status,
                        null,
                        at(date.minusDays(2), LocalTime.of(16, 0)),
                        null
                    );
                }
            }
            // Twelve visits per doctor, spread over twelve weeks and all eighteen specialties.
            for (int visit = 0; visit < 12; visit++) {
                LocalDate date = today.minusDays(7L + visit * 7L);
                LocalTime time = doctor.times().getFirst();
                Long slot = slot(doctor, date, time, "PUBLISHED");
                if (slot == null) continue;
                Patient patient = patients.get((d * 17 + visit * 13) % patients.size());
                Timestamp received = at(date, time.minusMinutes(10));
                Long appointment = appointment(
                    doctor,
                    patient,
                    slot,
                    "COMPLETED",
                    nextQueue(doctor.id(), date),
                    at(date.minusDays(2), LocalTime.of(16, 0)),
                    received
                );
                if (appointment == null) continue;
                encounter(
                    doctor,
                    patient,
                    appointment,
                    lab,
                    cashier,
                    medications,
                    counters,
                    icd,
                    "COMPLETED",
                    "PAID",
                    date,
                    time,
                    d * 12 + visit,
                    visit % 3 == 0
                );
            }
            // Noon gaps do not collide with the directory's morning/afternoon templates.
            for (int visit = 0; visit < 2; visit++) {
                LocalTime time = LocalTime.of(11 + (d % 3), visit * 30);
                Long slot = slot(doctor, today, time, "PUBLISHED");
                if (slot == null) continue;
                Patient patient = patients.get(d * 2 + visit);
                Long appointment = appointment(
                    doctor,
                    patient,
                    slot,
                    "CHECKED_IN",
                    nextQueue(doctor.id(), today),
                    at(today.minusDays(1), LocalTime.of(16, 0)),
                    at(today, time.minusMinutes(10))
                );
                if (appointment == null) continue;
                boolean occupied =
                    id(
                        "select count(*) from encounters where doctor_staff_id=? and status='IN_PROGRESS'",
                        doctor.id()
                    ) > 0;
                String status =
                    visit == 0
                        ? "WAITING"
                        : d % 3 == 0 && !occupied
                          ? "IN_PROGRESS"
                          : d % 3 == 1
                            ? "WAITING_RESULTS"
                            : "WAITING";
                String paid = visit == 0 ? (d % 2 == 0 ? "OPEN" : "PARTIAL") : "PAID";
                encounter(
                    doctor,
                    patient,
                    appointment,
                    lab,
                    cashier,
                    medications,
                    counters,
                    icd,
                    status,
                    paid,
                    today,
                    time,
                    648 + d * 2 + visit,
                    status.equals("WAITING_RESULTS")
                );
                if (visit == 1 && !status.equals("WAITING")) jdbc.update(
                    "update appointments set called_at=?,call_count=1 where id=?",
                    at(today, time),
                    appointment
                );
            }
            // Past no-show cases do not block current queues or pretend a future patient is absent.
            Long missed = slot(
                doctor,
                today.minusDays(3),
                LocalTime.of(11 + (d % 3), 0),
                "PUBLISHED"
            );
            if (missed != null) appointment(
                doctor,
                patients.get(60 + d),
                missed,
                "NO_SHOW",
                null,
                at(today.minusDays(5), LocalTime.of(16, 0)),
                null
            );
        }
        waitlist(today, doctors, patients);
        for (int i = 1; i <= 8; i++) jdbc.update(
            "insert into online_payment_transactions(provider_txn_id,request_hash,amount,status,received_at) values(?,?,?,'UNMATCHED',?)",
            "VOL-DEMO-UNMATCHED-%03d".formatted(i),
            "0".repeat(64),
            BigDecimal.valueOf(100000L + i * 25000L),
            at(today.minusDays(i % 4), LocalTime.of(10, i))
        );
        jdbc.update(
            "insert into audit_logs(action,entity_type,entity_id,detail) values('MOCK_INIT','DEMO',?,?)",
            DATASET,
            "120 tài khoản, 60 người thân, lịch 30 ngày, lịch sử 12 tuần, thu phí, xét nghiệm, đánh giá và danh sách chờ hư cấu; không gửi email."
        );
        jdbc.update("update mock_data_runs set completed_at=? where dataset=?", now(), DATASET);
        return true;
    }

    private List<Doctor> doctors(LocalDate today) {
        return jdbc.query(
            """
            select st.id,st.user_id,dp.specialty_id,t.room_id,t.service_id,sv.name,
                   (select unit_price from service_prices where service_id=sv.id and effective_from<=?
                    and (effective_to is null or effective_to>=?) order by effective_from desc,id desc limit 1) price
            from staff st join users u on u.id=st.user_id join doctor_profiles dp on dp.staff_id=st.id
            join specialties sp on sp.id=dp.specialty_id and sp.active=1
            join departments dep on dep.id=sp.department_id and dep.active=1
            join weekly_slot_templates t on t.id=(select min(id) from weekly_slot_templates where doctor_staff_id=st.id)
            join rooms rm on rm.id=t.room_id and rm.active=1 join services sv on sv.id=t.service_id and sv.active=1
            where u.username regexp '^mockbs[0-9]{3}$' and u.status='ACTIVE' and st.active=1
            and exists(select 1 from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=u.id and r.code='DOCTOR')
            order by u.username
            """,
            (rs, row) -> {
                long doctor = rs.getLong("id"),
                    room = rs.getLong("room_id"),
                    service = rs.getLong("service_id");
                var times = jdbc.query(
                    "select distinct starts_time from weekly_slot_templates where doctor_staff_id=? and room_id=? and service_id=? order by starts_time",
                    (t, n) -> t.getTime(1).toLocalTime(),
                    doctor,
                    room,
                    service
                );
                BigDecimal price = rs.getBigDecimal("price");
                if (price == null || times.isEmpty()) throw new IllegalStateException(
                    "Fixture doctor needs a current price and templates."
                );
                return new Doctor(
                    doctor,
                    rs.getLong("user_id"),
                    room,
                    service,
                    rs.getLong("specialty_id"),
                    rs.getString("name"),
                    price,
                    times
                );
            },
            today,
            today
        );
    }

    private List<Patient> patients(LocalDate today) {
        List<Patient> result = new ArrayList<>();
        result.add(
            jdbc.queryForObject(
                "select p.id,u.id owner from patient_profiles p join users u on u.id=p.user_id where u.username='benhnhan01' and p.active=1",
                (rs, n) -> new Patient(rs.getLong("id"), rs.getLong("owner"))
            )
        );
        String hash = passwords.encode("Demo@12345");
        long role = id("select id from roles where code='PATIENT'");
        List<Patient> families = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            String username = "volbn%03d".formatted(i);
            String name = SURNAMES[(i - 1) / 20] + " " + NAMES[(i - 1) % 20];
            long user = insert(
                "insert into users(username,password_hash,full_name,email,user_type,status) values(?,?,?,?,'PATIENT','ACTIVE')",
                username,
                hash,
                name,
                username + "@example.invalid"
            );
            jdbc.update("insert into user_roles(user_id,role_id) values(?,?)", user, role);
            long patient = insert(
                "insert into patient_profiles(patient_code,user_id,full_name,email,date_of_birth,gender,address,allergies,medical_history,emergency_contact) values(?,?,?,?,?,?,?,?,?,?)",
                "VOL-BN%03d".formatted(i),
                user,
                name,
                username + "@example.invalid",
                today.minusYears(18 + (i % 60)).minusDays(i * 2L),
                i % 2 == 0 ? "FEMALE" : "MALE",
                "Phường " + ((i % 12) + 1) + ", TP. Hồ Chí Minh (hồ sơ hư cấu)",
                "Chưa ghi nhận",
                NOTE,
                "Người thân (mẫu)"
            );
            result.add(new Patient(patient, user));
            if (i % 10 == 0) jdbc.update(
                "insert into patient_allergies(patient_id,ingredient,reaction,severity) values(?,'Amoxicillin','Phản ứng minh họa','MODERATE')",
                patient
            );
            if (i <= 30) for (int child = 1; child <= 2; child++) {
                long family = insert(
                    "insert into patient_profiles(patient_code,owner_user_id,full_name,date_of_birth,gender,relationship,medical_history) values(?,?,?,?,?,'CHILD',?)",
                    "VOL-GD%03d-%d".formatted(i, child),
                    user,
                    SURNAMES[(i - 1) / 20] + " " + NAMES[(i + child) % 20],
                    today.minusYears(4 + (i % 11) + child),
                    child == 1 ? "MALE" : "FEMALE",
                    NOTE
                );
                families.add(new Patient(family, user));
            }
        }
        result.addAll(families);
        return result;
    }

    private Lab lab(LocalDate today) {
        long room = insert(
            "insert into rooms(department_id,code,name,room_type,description) select id,'VOL-XN01','Phòng xét nghiệm tổng quát','LAB',? from departments where code='XN'",
            NOTE
        );
        long service = insert(
            "insert into services(code,name,category,unit,description) values('VOL-XN-TM','Bộ chỉ số huyết học mẫu','LAB','mẫu',?)",
            NOTE
        );
        jdbc.update(
            "insert into service_prices(service_id,unit_price,effective_from) values(?,80000,?)",
            service,
            today.minusDays(120)
        );
        long technician = id("select id from users where username='ktv01'");
        long nurse = id(
            "select s.id from staff s join users u on u.id=s.user_id where u.username='dieuduong01'"
        );
        long nurseUser = id("select id from users where username='dieuduong01'");
        jdbc.update(
            "insert into diagnostic_staff(room_id,staff_id,can_review) select ?,id,1 from staff where user_id=?",
            room,
            technician
        );
        for (String name : List.of("Hồng cầu (mẫu)", "Bạch cầu (mẫu)", "Tiểu cầu (mẫu)"))
            jdbc.update(
                "insert into diagnostic_definitions(service_id,name,unit,reference_range,sort_order) values(?,?,'đơn vị mẫu','Khoảng tham chiếu minh họa',?)",
                service,
                name,
                name.startsWith("Hồng") ? 1 : name.startsWith("Bạch") ? 2 : 3
            );
        return new Lab(room, service, technician, nurse, nurseUser);
    }

    private Long slot(Doctor doctor, LocalDate date, LocalTime time, String status) {
        Timestamp start = at(date, time),
            end = at(date, time.plusMinutes(30));
        if (
            id(
                "select count(*) from appointment_slots where status in ('DRAFT','SUBMITTED','PUBLISHED','CLOSE_REQUESTED') and (doctor_staff_id=? or room_id=?) and starts_at<? and ends_at>?",
                doctor.id(),
                doctor.room(),
                end,
                start
            ) > 0
        ) return null;
        return insert(
            "insert into appointment_slots(doctor_staff_id,room_id,service_id,starts_at,ends_at,status,note) values(?,?,?,?,?,?,?)",
            doctor.id(),
            doctor.room(),
            doctor.service(),
            start,
            end,
            status,
            DATASET
        );
    }

    private int nextQueue(long doctor, LocalDate date) {
        return Math.toIntExact(
            id(
                "select coalesce(max(a.queue_number),0)+1 from appointments a join appointment_slots s on s.id=a.slot_id where s.doctor_staff_id=? and s.starts_at>=? and s.starts_at<?",
                doctor,
                at(date, LocalTime.MIDNIGHT),
                at(date.plusDays(1), LocalTime.MIDNIGHT)
            )
        );
    }

    private Long appointment(
        Doctor doctor,
        Patient patient,
        long slot,
        String status,
        Integer queue,
        Timestamp booked,
        Timestamp checked
    ) {
        if (
            !List.of("CANCELLED", "NEEDS_RESCHEDULE").contains(status) &&
            id(
                "select count(*) from slot_reservations r join appointments a on a.id=r.appointment_id join appointment_slots s on s.id=r.slot_id join appointment_slots candidate on candidate.id=? where a.patient_id=? and s.starts_at<candidate.ends_at and s.ends_at>candidate.starts_at",
                slot,
                patient.id()
            ) > 0
        ) return null;
        long appointment = insert(
            "insert into appointments(appointment_code,patient_id,slot_id,status,symptoms,booked_price,queue_number,booked_at,checked_in_at,cancellation_reason,reminder_sent_at) values(?,?,?,?,?,?,?,?,?,?,?)",
            "VOL-TMP-" + slot,
            patient.id(),
            slot,
            status,
            NOTE,
            doctor.price(),
            queue,
            booked,
            checked,
            status.equals("CANCELLED") ? "Đổi kế hoạch (mẫu)" : null,
            now()
        );
        jdbc.update(
            "update appointments set appointment_code=? where id=?",
            "VOL-LH%010d".formatted(appointment),
            appointment
        );
        if (!List.of("CANCELLED", "NEEDS_RESCHEDULE").contains(status)) jdbc.update(
            "insert into slot_reservations(slot_id,appointment_id) values(?,?)",
            slot,
            appointment
        );
        jdbc.update(
            "insert into appointment_changes(appointment_id,new_slot_id,actor_user_id,action,occurred_at) values(?,?,?,'BOOK',?)",
            appointment,
            slot,
            patient.owner(),
            booked
        );
        if (!status.equals("CONFIRMED")) jdbc.update(
            "insert into appointment_changes(appointment_id,old_slot_id,actor_user_id,action,occurred_at) values(?,?,?,?,?)",
            appointment,
            slot,
            status.equals("CANCELLED") ? patient.owner() : doctor.user(),
            status.equals("COMPLETED") || status.equals("CHECKED_IN")
                ? "CHECK_IN"
                : status.equals("CANCELLED")
                  ? "CANCEL"
                  : status,
            checked == null ? booked : checked
        );
        notification(
            patient,
            appointment,
            "APPOINTMENT",
            "Lịch hẹn: " + status,
            "Lịch khám minh họa; kiểm tra ngày, giờ và người khám trong Lịch hẹn.",
            "/portal",
            booked,
            "VOL-A:" + appointment
        );
        return appointment;
    }

    private void encounter(
        Doctor doctor,
        Patient patient,
        long appointment,
        Lab lab,
        long cashier,
        List<Long> medications,
        List<Long> counters,
        String icd,
        String status,
        String invoiceStatus,
        LocalDate date,
        LocalTime time,
        int sequence,
        boolean diagnostic
    ) {
        boolean started = !status.equals("WAITING"),
            completed = status.equals("COMPLETED");
        Timestamp received = at(date, time.minusMinutes(10)),
            start = at(date, time),
            end = at(date, time.plusMinutes(25));
        long encounter = insert(
            "insert into encounters(appointment_id,patient_id,doctor_staff_id,nurse_staff_id,status,chief_complaint,medical_history,allergies,vitals,diagnosis,conclusion,instructions,follow_up_date,started_at,completed_at,created_at,follow_up_reminded_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            appointment,
            patient.id(),
            doctor.id(),
            lab.nurse(),
            status,
            sequence % 2 == 0 ? "Khám theo lịch (mẫu)" : "Theo dõi định kỳ (mẫu)",
            NOTE,
            "Chưa ghi nhận",
            "Mạch 76; nhiệt độ 36,7; huyết áp 120/80 (mẫu)",
            started ? "Khám tổng quát (dữ liệu mẫu)" : null,
            completed ? "Đã hoàn tất lượt khám minh họa" : null,
            NOTE,
            completed && sequence % 4 == 0
                ? LocalDate.now(clock.withZone(ZONE)).plusDays(3 + (sequence % 12))
                : null,
            started ? start : null,
            completed ? end : null,
            received,
            now()
        );
        if (started) jdbc.update(
            "insert into encounter_diagnoses(encounter_id,icd_code,is_primary) values(?,?,1)",
            encounter,
            icd
        );
        long invoice = insert(
            "insert into invoices(invoice_code,appointment_id,patient_id,status,created_at,source_id) values(?,?,?,?,?,?)",
            "VOL-HD%010d".formatted(appointment),
            appointment,
            patient.id(),
            invoiceStatus,
            received,
            appointment
        );
        jdbc.update(
            "insert into invoice_lines(invoice_id,source_type,source_id,description_snapshot,unit_snapshot,quantity,unit_price) values(?,'EXAMINATION',?,?,'lượt',1,?)",
            invoice,
            appointment,
            doctor.serviceName(),
            doctor.price()
        );
        if (diagnostic) diagnostic(
            doctor,
            lab,
            encounter,
            appointment,
            patient,
            cashier,
            date,
            time,
            completed,
            sequence
        );
        BigDecimal total = doctor.price();
        if (!invoiceStatus.equals("OPEN")) {
            BigDecimal amount = invoiceStatus.equals("PARTIAL")
                ? total.divide(BigDecimal.valueOf(2))
                : total;
            if (completed && sequence % 4 == 0) {
                receipt(
                    invoice,
                    appointment,
                    cashier,
                    total.divide(BigDecimal.valueOf(2)),
                    "CASH",
                    start,
                    1
                );
                receipt(
                    invoice,
                    appointment,
                    cashier,
                    total.divide(BigDecimal.valueOf(2)),
                    "TRANSFER",
                    end,
                    2
                );
            } else receipt(
                invoice,
                appointment,
                cashier,
                amount,
                sequence % 2 == 0 ? "CASH" : "TRANSFER",
                start,
                1
            );
        }
        if (started) {
            long prescription = insert(
                "insert into prescriptions(encounter_id,version,status,author_user_id,created_at,finalized_at) values(?,1,?,?,?,?)",
                encounter,
                completed ? "FINAL" : "DRAFT",
                doctor.user(),
                start,
                completed ? end : null
            );
            for (int line = 0; line < 2; line++) jdbc.update(
                "insert into prescription_lines(prescription_id,medication_id,name_snapshot,strength_snapshot,unit_snapshot,dose,route,frequency,days,quantity,instructions) select ?,id,name,strength,unit,'Liều mẫu','Đường dùng mẫu','Tần suất mẫu',3,3,? from medications where id=?",
                prescription,
                NOTE,
                medications.get((sequence + line) % medications.size())
            );
            if (completed && sequence % 5 == 0) jdbc.update(
                "insert into pharmacy_orders(encounter_id,prescription_id,counter_id) values(?,?,?)",
                encounter,
                prescription,
                counters.get(sequence % 3)
            );
        }
        if (completed) {
            jdbc.update(
                "insert into appointment_changes(appointment_id,actor_user_id,action,occurred_at) values(?,?,'COMPLETE',?)",
                appointment,
                doctor.user(),
                end
            );
            notification(
                patient,
                appointment,
                "REVIEW_INVITE",
                "Đánh giá buổi khám",
                "Lượt khám đã hoàn tất; mở bệnh án để gửi đánh giá trong thời hạn cho phép.",
                "/portal/records/" + encounter,
                end,
                "VOL-R:" + encounter
            );
            if (sequence % 3 != 2) jdbc.update(
                "insert into encounter_reviews(encounter_id,patient_id,doctor_staff_id,rating,comment,visible,created_at) values(?,?,?,?,?,?,?)",
                encounter,
                patient.id(),
                doctor.id(),
                sequence % 17 == 0 ? 2 : 3 + ((sequence / 3) % 3),
                sequence % 3 == 0
                    ? "Được hướng dẫn các bước khá rõ ràng (mẫu)."
                    : "Bác sĩ giải thích dễ hiểu, nhân viên hỗ trợ tốt (mẫu).",
                sequence % 11 != 0,
                end
            );
        }
    }

    private void diagnostic(
        Doctor doctor,
        Lab lab,
        long encounter,
        long appointment,
        Patient patient,
        long cashier,
        LocalDate date,
        LocalTime time,
        boolean completed,
        int sequence
    ) {
        String status = completed
            ? "COMPLETED"
            : List.of("ISSUED", "ACCEPTED", "IN_PROGRESS").get((sequence / 6) % 3);
        Timestamp start = at(date, time),
            accepted = status.equals("ISSUED") ? null : start;
        long order = insert(
            "insert into diagnostic_orders(encounter_id,service_id,room_id,category,status,notes,issued_by,issued_at,accepted_at) values(?,?,?,'LAB',?,?,?,?,?)",
            encounter,
            lab.service(),
            lab.room(),
            status,
            NOTE,
            doctor.user(),
            start,
            accepted
        );
        long invoice = insert(
            "insert into invoices(invoice_code,appointment_id,patient_id,status,source_type,source_id,created_at) values(?,?,?,'PAID','DIAGNOSTIC',?,?)",
            "VOL-XN%010d".formatted(order),
            appointment,
            patient.id(),
            order,
            start
        );
        jdbc.update(
            "insert into invoice_lines(invoice_id,source_type,source_id,description_snapshot,unit_snapshot,quantity,unit_price) values(?,'DIAGNOSTIC',?,'Bộ chỉ số huyết học mẫu','mẫu',1,80000)",
            invoice,
            order
        );
        jdbc.update(
            "insert into receipts(receipt_code,invoice_id,cashier_user_id,amount,method,recorded_at) values(?,?,?,80000,'CASH',?)",
            "VOL-PX%010d".formatted(order),
            invoice,
            cashier,
            start
        );
        jdbc.update(
            "insert into workflow_events(entity_type,entity_id,action,actor_user_id,details,occurred_at) values('DIAGNOSTIC',?,'ISSUE',?,JSON_OBJECT('dataset',?),?)",
            order,
            doctor.user(),
            DATASET,
            start
        );
        if (accepted != null) jdbc.update(
            "insert into workflow_events(entity_type,entity_id,action,actor_user_id,details,occurred_at) values('DIAGNOSTIC',?,'ACCEPT',?,JSON_OBJECT('dataset',?),?)",
            order,
            lab.technician(),
            DATASET,
            accepted
        );
        if (!status.equals("ISSUED")) {
            long sample = insert(
                "insert into diagnostic_samples(order_id,collected_by,collected_at) values(?,?,?)",
                order,
                lab.nurseUser(),
                at(date, time.plusMinutes(5))
            );
            String measurements =
                "[{\"name\":\"Hồng cầu (mẫu)\",\"unit\":\"đơn vị mẫu\",\"referenceRange\":\"Khoảng minh họa\",\"value\":\"4.8\",\"flag\":\"\"},{\"name\":\"Bạch cầu (mẫu)\",\"unit\":\"đơn vị mẫu\",\"referenceRange\":\"Khoảng minh họa\",\"value\":\"7.2\",\"flag\":\"\"},{\"name\":\"Tiểu cầu (mẫu)\",\"unit\":\"đơn vị mẫu\",\"referenceRange\":\"Khoảng minh họa\",\"value\":\"250\",\"flag\":\"\"}]";
            insert(
                "insert into diagnostic_results(order_id,version,status,measurements,description,conclusion,author_id,reviewer_id,published_at,sample_id) values(?,1,?,?,?,?,?,?,?,?)",
                order,
                completed ? "PUBLISHED" : (sequence / 6) % 2 == 0 ? "DRAFT" : "SUBMITTED",
                measurements,
                NOTE,
                "Kết quả giả lập để kiểm tra giao diện",
                lab.technician(),
                completed ? lab.technician() : null,
                completed ? at(date, time.plusMinutes(20)) : null,
                sample
            );
            if (completed) jdbc.update(
                "insert into workflow_events(entity_type,entity_id,action,actor_user_id,details,occurred_at) values('DIAGNOSTIC',?,'PUBLISH',?,JSON_OBJECT('dataset',?),?)",
                order,
                lab.technician(),
                DATASET,
                at(date, time.plusMinutes(20))
            );
        }
    }

    private void receipt(
        long invoice,
        long appointment,
        long cashier,
        BigDecimal amount,
        String method,
        Timestamp time,
        int part
    ) {
        jdbc.update(
            "insert into receipts(receipt_code,invoice_id,cashier_user_id,amount,method,recorded_at) values(?,?,?,?,?,?)",
            "VOL-PT%010d-%d".formatted(appointment, part),
            invoice,
            cashier,
            amount,
            method,
            time
        );
    }

    private void waitlist(LocalDate today, List<Doctor> doctors, List<Patient> patients) {
        for (int i = 0; i < 30; i++) {
            Doctor doctor = doctors.get(i);
            Patient patient = patients.get(i);
            String status = List.of("ACTIVE", "NOTIFIED", "BOOKED", "EXPIRED", "CANCELLED").get(
                i % 5
            );
            Long slot = status.equals("NOTIFIED")
                ? jdbc.queryForObject(
                      "select min(id) from appointment_slots where doctor_staff_id=? and note=? and status='PUBLISHED' and starts_at>=? and starts_at<? and not exists(select 1 from slot_reservations r where r.slot_id=appointment_slots.id)",
                      Long.class,
                      doctor.id(),
                      DATASET,
                      at(today.plusDays(28), LocalTime.MIDNIGHT),
                      at(today.plusDays(29), LocalTime.MIDNIGHT)
                  )
                : null;
            if (
                status.equals("BOOKED") &&
                id(
                    "select count(*) from appointments a join appointment_slots s on s.id=a.slot_id where a.patient_id=? and s.doctor_staff_id=? and a.status='CONFIRMED' and a.appointment_code like 'VOL-LH%'",
                    patient.id(),
                    doctor.id()
                ) == 0
            ) continue;
            if (status.equals("NOTIFIED") && slot == null) continue;
            long waitlist = insert(
                "insert into waitlist_entries(patient_id,service_id,specialty_id,doctor_staff_id,date_from,date_to,status,notified_at,notified_slot_id,created_at) values(?,?,?,?,?,?,?,?,?,?)",
                patient.id(),
                doctor.service(),
                doctor.specialty(),
                doctor.id(),
                status.equals("EXPIRED") ? today.minusDays(8) : today.plusDays(25),
                status.equals("EXPIRED") ? today.minusDays(2) : today.plusDays(30),
                status,
                status.equals("NOTIFIED") ? now() : null,
                slot,
                at(today.minusDays(1), LocalTime.of(9, 0))
            );
            if (status.equals("NOTIFIED")) notification(
                patient,
                null,
                "WAITLIST",
                "Có khoảng khám phù hợp",
                "Giờ khám mới chỉ được thông báo; bạn cần đặt lịch để xác nhận.",
                "/portal/waitlist",
                now(),
                "VOL-W:" + waitlist
            );
        }
    }

    private void notification(
        Patient patient,
        Long appointment,
        String kind,
        String title,
        String body,
        String link,
        Timestamp time,
        String key
    ) {
        jdbc.update(
            "insert into notifications(user_id,appointment_id,kind,title,body,link,dedupe_key,created_at,read_at) values(?,?,?,?,?,?,?,?,?)",
            patient.owner(),
            appointment,
            kind,
            title,
            body,
            link,
            key,
            time,
            patient.id() % 3 == 0 ? time : null
        );
    }

    private long insert(String sql, Object... args) {
        return ClinicJdbc.insert(jdbc, sql, args);
    }

    private long id(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    private Timestamp at(LocalDate date, LocalTime time) {
        return Timestamp.from(date.atTime(time).atZone(ZONE).toInstant());
    }
}
