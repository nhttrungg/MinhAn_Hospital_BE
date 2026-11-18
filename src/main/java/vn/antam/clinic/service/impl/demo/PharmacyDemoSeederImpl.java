package vn.antam.clinic.service.impl.demo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.demo.PharmacyDemoSeeder;
import vn.antam.clinic.service.support.ClinicJdbc;

/** Opt-in pharmacy fixtures. Prices, patients and workflows are fictional demo data. */
@Service
public class PharmacyDemoSeederImpl implements PharmacyDemoSeeder {

    static final String DATASET = "pharmacy-demo-v1";
    private static final String NOTE = "Dữ liệu quầy thuốc demo; không dùng để điều trị.";
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PasswordEncoder passwords;

    record Medication(
        String name,
        String ingredient,
        String strength,
        String form,
        String unit,
        int price
    ) {}

    private record Stock(long medication, long batch, int price) {}

    private record Counter(long id, long pharmacist, long cashier, List<Stock> stock) {}

    static final List<Medication> MEDICATIONS = List.of(
        new Medication("Paracetamol 500", "Paracetamol", "500 mg", "Viên nén", "viên", 1000),
        new Medication("Paracetamol trẻ em", "Paracetamol", "150 mg", "Bột pha uống", "gói", 2500),
        new Medication("Ibuprofen 200", "Ibuprofen", "200 mg", "Viên nén", "viên", 1500),
        new Medication("Diclofenac gel", "Diclofenac", "1% · 20 g", "Gel bôi", "tuýp", 35000),
        new Medication("Amoxicillin 500", "Amoxicillin", "500 mg", "Viên nang", "viên", 2500),
        new Medication(
            "Amoxicillin / Clavulanate",
            "Amoxicillin + acid clavulanic",
            "500 mg + 125 mg",
            "Viên nén",
            "viên",
            6500
        ),
        new Medication("Cefuroxime 250", "Cefuroxime", "250 mg", "Viên nén", "viên", 7000),
        new Medication("Azithromycin 250", "Azithromycin", "250 mg", "Viên nang", "viên", 8000),
        new Medication("Cetirizine 10", "Cetirizine", "10 mg", "Viên nén", "viên", 1200),
        new Medication("Loratadine 10", "Loratadine", "10 mg", "Viên nén", "viên", 1500),
        new Medication("Fexofenadine 120", "Fexofenadine", "120 mg", "Viên nén", "viên", 3500),
        new Medication(
            "Acetylcysteine 200",
            "Acetylcysteine",
            "200 mg",
            "Bột pha uống",
            "gói",
            2500
        ),
        new Medication("Ambroxol 30", "Ambroxol", "30 mg", "Viên nén", "viên", 1500),
        new Medication(
            "Salbutamol bình hít",
            "Salbutamol",
            "100 mcg/liều · 200 liều",
            "Bình xịt định liều",
            "bình",
            85000
        ),
        new Medication("Omeprazole 20", "Omeprazole", "20 mg", "Viên nang", "viên", 1800),
        new Medication("Esomeprazole 20", "Esomeprazole", "20 mg", "Viên nén", "viên", 5000),
        new Medication("Diosmectite 3", "Diosmectite", "3 g", "Bột pha uống", "gói", 4500),
        new Medication(
            "Oresol",
            "Glucose + điện giải",
            "Gói pha 200 ml",
            "Bột pha uống",
            "gói",
            2000
        ),
        new Medication("Lactulose", "Lactulose", "10 g/15 ml", "Dung dịch uống", "gói", 6000),
        new Medication("Amlodipine 5", "Amlodipine", "5 mg", "Viên nén", "viên", 2000),
        new Medication("Losartan 50", "Losartan", "50 mg", "Viên nén", "viên", 3000),
        new Medication("Bisoprolol 2.5", "Bisoprolol", "2,5 mg", "Viên nén", "viên", 3500),
        new Medication("Atorvastatin 10", "Atorvastatin", "10 mg", "Viên nén", "viên", 4000),
        new Medication("Metformin 500", "Metformin", "500 mg", "Viên nén", "viên", 1500),
        new Medication(
            "Gliclazide MR 30",
            "Gliclazide",
            "30 mg",
            "Viên phóng thích biến đổi",
            "viên",
            3500
        ),
        new Medication("Levothyroxine 50", "Levothyroxine", "50 mcg", "Viên nén", "viên", 2000),
        new Medication("Vitamin C 500", "Acid ascorbic", "500 mg", "Viên nén", "viên", 1200),
        new Medication(
            "Vitamin D3 1000",
            "Cholecalciferol",
            "1.000 IU",
            "Viên nang mềm",
            "viên",
            2500
        ),
        new Medication(
            "Calcium / Vitamin D3",
            "Calcium + cholecalciferol",
            "500 mg + 200 IU",
            "Viên nén",
            "viên",
            3000
        ),
        new Medication(
            "Sắt / Acid folic",
            "Sắt + acid folic",
            "60 mg + 400 mcg",
            "Viên nang",
            "viên",
            2500
        ),
        new Medication("Kẽm 10", "Kẽm", "10 mg", "Viên nén", "viên", 1500),
        new Medication(
            "Natri clorid nhỏ mắt",
            "Natri clorid",
            "0,9% · 10 ml",
            "Dung dịch nhỏ mắt",
            "lọ",
            6000
        ),
        new Medication(
            "Natri clorid rửa mũi",
            "Natri clorid",
            "0,9% · 100 ml",
            "Dung dịch rửa",
            "chai",
            12000
        ),
        new Medication("Clotrimazole kem", "Clotrimazole", "1% · 10 g", "Kem bôi", "tuýp", 20000),
        new Medication(
            "Povidone iodine",
            "Povidone iodine",
            "10% · 30 ml",
            "Dung dịch dùng ngoài",
            "chai",
            18000
        ),
        new Medication(
            "Hydrocortisone kem",
            "Hydrocortisone",
            "1% · 10 g",
            "Kem bôi",
            "tuýp",
            25000
        )
    );

    public PharmacyDemoSeederImpl(JdbcTemplate jdbc, Clock clock, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.passwords = passwords;
    }

    @Override
    @Transactional
    public boolean initialize() {
        LocalDate today = LocalDate.now(clock);
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

        List<Stock> medications = new ArrayList<>();
        for (int i = 0; i < MEDICATIONS.size(); i++) {
            Medication m = MEDICATIONS.get(i);
            long id = insert(
                "insert into medications(code,name,ingredient,strength,form,unit) values(?,?,?,?,?,?)",
                "MOCK-DUOC-TH%03d".formatted(i + 1),
                m.name(),
                m.ingredient(),
                m.strength(),
                m.form(),
                m.unit()
            );
            medications.add(new Stock(id, 0, m.price()));
        }
        // Also make the six original demo medications usable without changing their catalog entries or prescriptions.
        jdbc.queryForList(
            "select id from medications where code in ('MOCK-TH001','MOCK-TH002','MOCK-TH003','MOCK-TH004','MOCK-TH005','MOCK-TH006') order by code"
        ).forEach(row -> medications.add(new Stock(((Number) row.get("id")).longValue(), 0, 1000)));

        long department = id("select id from departments where code='DUOC'");
        long finance = id("select id from departments where code='TC'");
        long admin = id("select id from users where username='admin'");
        String hash = passwords.encode("Demo@12345");
        String[] names = {
            "Quầy thuốc Ngoại trú",
            "Quầy thuốc Chuyên khoa",
            "Quầy thuốc Nhi - Gia đình",
        };
        String[] pharmacists = { "Nguyễn Ngọc Trâm", "Trần Minh Đức", "Lê Thanh Thảo" };
        String[] cashiers = { "Phạm Thùy Dương", "Hoàng Hải Nam", "Võ Ngọc Hân" };
        List<Counter> counters = new ArrayList<>();
        for (int c = 0; c < names.length; c++) {
            int n = c + 1;
            long room = insert(
                "insert into rooms(department_id,code,name,room_type,description) values(?,?,?,'PHARMACY',?)",
                department,
                "MOCK-DUOC-P%02d".formatted(n),
                names[c],
                "Tiếp nhận đơn đã chốt, đối chiếu thuốc, thu tiền và giao thuốc; dữ liệu demo."
            );
            // No fictional bank account is supplied: cash payments work and an admin can configure a real QR account later.
            long counter = insert(
                "insert into pharmacy_counters(code,name,room_id) values(?,?,?)",
                "MOCK-QT%02d".formatted(n),
                names[c],
                room
            );
            long pharmacist = staff(
                "mockds%03d".formatted(n),
                "MOCK-DS%03d".formatted(n),
                pharmacists[c],
                "PHARMACIST",
                department,
                "Dược sĩ",
                hash
            );
            long cashier = staff(
                "mocktn%03d".formatted(n),
                "MOCK-TN%03d".formatted(n),
                cashiers[c],
                "CASHIER",
                finance,
                "Thu ngân quầy thuốc",
                hash
            );
            jdbc.update(
                "insert into counter_staff(counter_id,staff_id) select ?,id from staff where user_id in (?,?)",
                counter,
                pharmacist,
                cashier
            );
            jdbc.update(
                "insert into counter_staff(counter_id,staff_id) select ?,s.id from staff s join users u on u.id=s.user_id where u.username in ('duocsi01','thungan01')",
                counter
            );
            event("COUNTER", counter, "CONFIGURE", admin, counter);
            List<Stock> stock = new ArrayList<>();
            for (int i = 0; i < medications.size(); i++) {
                Stock m = medications.get(i);
                int price = m.price() + c * 100;
                jdbc.update(
                    "insert into medication_prices(counter_id,medication_id,price,effective_at) values(?,?,?,?)",
                    counter,
                    m.medication(),
                    price,
                    at(today.minusDays(30), LocalTime.MIDNIGHT)
                );
                long first = batch(
                    counter,
                    m.medication(),
                    "MOCK-L%02d-%03d-A".formatted(n, i + 1),
                    today.plusMonths(6),
                    200,
                    true,
                    pharmacist
                );
                batch(
                    counter,
                    m.medication(),
                    "MOCK-L%02d-%03d-B".formatted(n, i + 1),
                    today.plusMonths(18),
                    400,
                    true,
                    pharmacist
                );
                stock.add(new Stock(m.medication(), first, price));
            }
            counters.add(new Counter(counter, pharmacist, cashier, stock));
        }
        Counter first = counters.getFirst();
        long transferActor = id("select id from users where username='duocsi01'");
        long med = first.stock().getFirst().medication();
        batch(
            first.id(),
            med,
            "MOCK-L-SAP-HET-HAN",
            today.plusDays(20),
            15,
            true,
            first.pharmacist()
        );
        batch(first.id(), med, "MOCK-L-HET-HAN", today.minusDays(10), 10, true, first.pharmacist());
        batch(
            first.id(),
            med,
            "MOCK-L-TAM-NGUNG",
            today.plusYears(1),
            25,
            false,
            first.pharmacist()
        );
        batch(first.id(), med, "MOCK-L-HET-TON", today.plusYears(1), 0, true, first.pharmacist());
        movement(
            first.stock().get(1).batch(),
            -5,
            0,
            "ADJUST",
            null,
            first.pharmacist(),
            "Kiểm kê demo"
        );
        movement(
            first.stock().get(2).batch(),
            -20,
            0,
            "TRANSFER_OUT",
            null,
            transferActor,
            "Chuyển kho demo sang quầy chuyên khoa"
        );
        Stock transferred = first.stock().get(2);
        long received = batch(
            counters.get(1).id(),
            transferred.medication(),
            "MOCK-L01-003-A",
            today.plusMonths(6),
            0,
            true,
            transferActor
        );
        movement(
            received,
            20,
            0,
            "TRANSFER_IN",
            null,
            transferActor,
            "Nhận chuyển kho demo từ quầy ngoại trú"
        );
        event("COUNTER", first.id(), "STOCK_ADJUST", first.pharmacist(), first.id());
        event("COUNTER", first.id(), "STOCK_TRANSFER", transferActor, first.id());

        // Dedicated patients/visits keep existing medical records, orders and payments untouched.
        String[] states = {
            "DRAFT",
            "OPEN",
            "PARTIAL",
            "PAID",
            "DELIVERED",
            "CANCELLED",
            "SHORTAGE",
            "CLOSED",
            "UNCLAIMED",
        };
        for (int i = 0; i < states.length; i++) scenario(
            i + 1,
            states[i],
            counters.get(i % counters.size()),
            today
        );
        jdbc.update(
            "insert into audit_logs(action,entity_type,entity_id,detail) values('MOCK_INIT','DEMO',?,?)",
            DATASET,
            "Bổ sung 36 thuốc, 3 quầy, dược sĩ/thu ngân, giá, lô tồn và 9 tình huống cấp thuốc; không gửi email."
        );
        jdbc.update(
            "update mock_data_runs set completed_at=? where dataset=?",
            Timestamp.from(clock.instant()),
            DATASET
        );
        return true;
    }

    private long staff(
        String username,
        String code,
        String name,
        String role,
        long department,
        String title,
        String hash
    ) {
        long user = insert(
            "insert into users(username,password_hash,full_name,email,user_type,status) values(?,?,?,?,'STAFF','ACTIVE')",
            username,
            hash,
            name,
            username + "@example.com"
        );
        jdbc.update(
            "insert into user_roles(user_id,role_id) select ?,id from roles where code=?",
            user,
            role
        );
        insert(
            "insert into staff(user_id,employee_code,department_id,title) values(?,?,?,?)",
            user,
            code,
            department,
            title
        );
        return user;
    }

    private long batch(
        long counter,
        long medication,
        String code,
        LocalDate expiry,
        int quantity,
        boolean active,
        long actor
    ) {
        long batch = insert(
            "insert into medication_batches(counter_id,medication_id,batch_code,expiry_date,active) values(?,?,?,?,?)",
            counter,
            medication,
            code,
            expiry,
            active
        );
        if (quantity > 0) movement(
            batch,
            quantity,
            0,
            "RECEIVE",
            null,
            actor,
            "Nhập tồn đầu kỳ demo"
        );
        return batch;
    }

    private void movement(
        long batch,
        int delta,
        int reservedDelta,
        String action,
        Long dispensing,
        long actor,
        String reason
    ) {
        jdbc.queryForObject(
            "select id from medication_batches where id=? for update",
            Long.class,
            batch
        );
        jdbc.update(
            "update medication_batches set quantity=quantity+?,reserved=reserved+?,revision=revision+1 where id=?",
            delta,
            reservedDelta,
            batch
        );
        jdbc.update(
            "insert into stock_movements(batch_id,action,quantity_delta,reserved_delta,quantity_after,reserved_after,dispensing_id,actor_user_id,reason,occurred_at) " +
                "select id,?,?,?,quantity,reserved,?,?,?,? from medication_batches where id=?",
            action,
            delta,
            reservedDelta,
            dispensing,
            actor,
            reason,
            Timestamp.from(clock.instant()),
            batch
        );
    }

    private void scenario(int n, String state, Counter counter, LocalDate today) {
        long patient = insert(
            "insert into patient_profiles(patient_code,full_name,phone,date_of_birth,gender,address,allergies,medical_history) values(?,?,?,?,?,?,?,?)",
            "MOCK-DUOC-BN%03d".formatted(n),
            "Khách demo quầy thuốc " + n,
            "090800%04d".formatted(n),
            today.minusYears(25 + n),
            n % 2 == 0 ? "FEMALE" : "MALE",
            "TP. Hồ Chí Minh (demo)",
            "Chưa ghi nhận (demo)",
            NOTE
        );
        long doctor = id(
            "select s.id from staff s join users u on u.id=s.user_id where u.username='bacsi01'"
        );
        long author = id("select user_id from staff where id=?", doctor);
        long room = id("select id from rooms where code='P101'");
        long service = id("select id from services where code='KB-NOI'");
        jdbc.queryForObject("select id from staff where id=? for update", Long.class, doctor);
        jdbc.queryForObject("select id from rooms where id=? for update", Long.class, room);
        LocalDate date = today.minusDays(n);
        Timestamp start = at(date, LocalTime.of(18, 0));
        Timestamp end = at(date, LocalTime.of(18, 30));
        while (
            jdbc.queryForObject(
                "select count(*) from appointment_slots where (doctor_staff_id=? or room_id=?) and status in ('DRAFT','SUBMITTED','PUBLISHED','CLOSE_REQUESTED') and starts_at<? and ends_at>?",
                Integer.class,
                doctor,
                room,
                end,
                start
            ) > 0
        ) {
            date = date.minusDays(10);
            start = at(date, LocalTime.of(18, 0));
            end = at(date, LocalTime.of(18, 30));
        }
        long slot = insert(
            "insert into appointment_slots(doctor_staff_id,room_id,service_id,starts_at,ends_at,status,note) values(?,?,?,?,?,'PUBLISHED',?)",
            doctor,
            room,
            service,
            start,
            end,
            NOTE
        );
        long appointment = insert(
            "insert into appointments(appointment_code,patient_id,slot_id,status,symptoms,booked_price,booked_at,checked_in_at) values(?,?,?,'COMPLETED',?,150000,?,?)",
            "MOCK-DUOC-LH%03d".formatted(n),
            patient,
            slot,
            NOTE,
            start,
            start
        );
        jdbc.update(
            "insert into slot_reservations(slot_id,appointment_id) values(?,?)",
            slot,
            appointment
        );
        jdbc.update(
            "insert into appointment_changes(appointment_id,actor_user_id,action,occurred_at) values(?,?,'COMPLETE',?)",
            appointment,
            author,
            end
        );
        long encounter = insert(
            "insert into encounters(appointment_id,patient_id,doctor_staff_id,status,chief_complaint,diagnosis,conclusion,instructions,started_at,completed_at,created_at) values(?,?,?,'COMPLETED',?,?,?,?,?,?,?)",
            appointment,
            patient,
            doctor,
            NOTE,
            "Chẩn đoán minh họa",
            "Hoàn tất khám demo: " + state,
            NOTE,
            start,
            end,
            start
        );
        long examInvoice = insert(
            "insert into invoices(invoice_code,appointment_id,patient_id,status,source_type,source_id,created_at) values(?,?,?,'PAID','EXAMINATION',?,?)",
            "MOCK-DUOC-KB%03d".formatted(n),
            appointment,
            patient,
            appointment,
            start
        );
        jdbc.update(
            "insert into invoice_lines(invoice_id,source_type,source_id,description_snapshot,unit_snapshot,quantity,unit_price) values(?,'EXAMINATION',?,'Khám nội tổng quát','lượt',1,150000)",
            examInvoice,
            appointment
        );
        receipt(
            examInvoice,
            counter.cashier(),
            BigDecimal.valueOf(150000),
            "MOCK-DUOC-KBPT%03d".formatted(n)
        );
        long prescription = insert(
            "insert into prescriptions(encounter_id,version,status,revision,author_user_id,created_at,finalized_at) values(?,1,'FINAL',1,?,?,?)",
            encounter,
            author,
            start,
            end
        );
        List<Long> lines = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Stock m = counter.stock().get((n * 3 + i) % MEDICATIONS.size());
            lines.add(
                insert(
                    "insert into prescription_lines(prescription_id,medication_id,name_snapshot,strength_snapshot,unit_snapshot,dose,route,frequency,days,quantity,instructions) " +
                        "select ?,id,name,strength,unit,'Liều minh họa','Đường dùng minh họa','Tần suất minh họa',1,10,? from medications where id=?",
                    prescription,
                    NOTE,
                    m.medication()
                )
            );
        }
        if (state.equals("UNCLAIMED")) return;
        jdbc.update(
            "insert into pharmacy_orders(encounter_id,prescription_id,counter_id,status,revision,close_reason) values(?,?,?,?,?,?)",
            encounter,
            prescription,
            counter.id(),
            state.equals("CLOSED") ? "CLOSED" : "OPEN",
            state.equals("CLOSED") ? 1 : 0,
            state.equals("CLOSED") ? "Đã cấp đủ thuốc (demo)" : null
        );
        event("PHARMACY_ORDER", encounter, "CLAIM", counter.pharmacist(), counter.id());
        String status = state.equals("DRAFT")
            ? "DRAFT"
            : state.equals("CANCELLED")
              ? "CANCELLED"
              : List.of("DELIVERED", "SHORTAGE", "CLOSED").contains(state)
                ? "DELIVERED"
                : "READY";
        boolean committed = !status.equals("DRAFT");
        boolean delivered = status.equals("DELIVERED");
        long dispensing = insert(
            "insert into dispensings(encounter_id,prescription_id,counter_id,status,revision,shortage_reason,prepared_by,prepared_at,delivered_by,delivered_at) values(?,?,?,?,?,?,?,?,?,?)",
            encounter,
            prescription,
            counter.id(),
            status,
            status.equals("DRAFT") ? 0 : status.equals("READY") ? 1 : 2,
            state.equals("SHORTAGE")
                ? "Cấp trước 5/10 mỗi thuốc; hẹn nhận phần còn lại (demo)."
                : state.equals("CANCELLED")
                  ? "Khách đổi kế hoạch (demo)"
                  : null,
            counter.pharmacist(),
            committed ? Timestamp.from(clock.instant()) : null,
            delivered ? counter.pharmacist() : null,
            delivered ? Timestamp.from(clock.instant()) : null
        );
        event("DISPENSING", dispensing, "SAVE_DRAFT", counter.pharmacist(), counter.id());
        for (int i = 0; i < lines.size(); i++) {
            Stock m = counter.stock().get((n * 3 + i) % MEDICATIONS.size());
            int quantity = state.equals("SHORTAGE") ? 5 : 10;
            jdbc.update(
                "insert into dispensing_lines(dispensing_id,prescription_line_id,batch_id,quantity,unit_price,checked) values(?,?,?,?,?,1)",
                dispensing,
                lines.get(i),
                m.batch(),
                quantity,
                committed ? m.price() : null
            );
            if (committed) {
                movement(
                    m.batch(),
                    0,
                    quantity,
                    "RESERVE",
                    dispensing,
                    counter.pharmacist(),
                    "Chốt soạn demo"
                );
                if (delivered) movement(
                    m.batch(),
                    -quantity,
                    -quantity,
                    "DELIVER",
                    dispensing,
                    counter.pharmacist(),
                    "Giao thuốc demo đã thanh toán"
                );
                if (status.equals("CANCELLED")) movement(
                    m.batch(),
                    0,
                    -quantity,
                    "RELEASE",
                    dispensing,
                    counter.pharmacist(),
                    "Hủy phiếu demo chưa thanh toán"
                );
            }
        }
        if (!committed) return;
        event("DISPENSING", dispensing, "COMMIT", counter.pharmacist(), counter.id());
        String invoiceStatus = status.equals("CANCELLED")
            ? "VOID"
            : state.equals("OPEN")
              ? "OPEN"
              : state.equals("PARTIAL")
                ? "PARTIAL"
                : "PAID";
        long invoice = insert(
            "insert into invoices(invoice_code,appointment_id,patient_id,status,source_type,source_id,counter_id,bank_snapshot) values(?,?,?,?,'PHARMACY',?,?,JSON_OBJECT('name',?))",
            "MOCK-DUOC-HD%03d".formatted(n),
            appointment,
            patient,
            invoiceStatus,
            dispensing,
            counter.id(),
            jdbc.queryForObject(
                "select name from pharmacy_counters where id=?",
                String.class,
                counter.id()
            )
        );
        jdbc.update(
            "insert into invoice_lines(invoice_id,source_type,source_id,description_snapshot,unit_snapshot,quantity,unit_price,status,void_reason) " +
                "select ?,'MEDICATION',dl.id,concat(pl.name_snapshot,' ',pl.strength_snapshot),pl.unit_snapshot,dl.quantity,dl.unit_price,?,? from dispensing_lines dl join prescription_lines pl on pl.id=dl.prescription_line_id where dl.dispensing_id=?",
            invoice,
            status.equals("CANCELLED") ? "VOID" : "ACTIVE",
            status.equals("CANCELLED") ? "Hủy phiếu demo" : null,
            dispensing
        );
        if (invoiceStatus.equals("PAID") || invoiceStatus.equals("PARTIAL")) {
            BigDecimal total = jdbc.queryForObject(
                "select sum(amount) from invoice_lines where invoice_id=?",
                BigDecimal.class,
                invoice
            );
            receipt(
                invoice,
                counter.cashier(),
                invoiceStatus.equals("PARTIAL") ? total.divide(BigDecimal.valueOf(2)) : total,
                "MOCK-DUOC-PT%03d".formatted(n)
            );
            event("INVOICE", invoice, "PAYMENT", counter.cashier(), counter.id());
        }
        if (delivered || status.equals("CANCELLED")) event(
            "DISPENSING",
            dispensing,
            delivered ? "DELIVER" : "CANCEL",
            counter.pharmacist(),
            counter.id()
        );
        if (state.equals("CLOSED")) event(
            "PHARMACY_ORDER",
            encounter,
            "CLOSE",
            counter.pharmacist(),
            counter.id()
        );
    }

    private void receipt(long invoice, long cashier, BigDecimal amount, String code) {
        jdbc.update(
            "insert into receipts(receipt_code,invoice_id,cashier_user_id,amount,method,note,recorded_at) values(?,?,?,?,'CASH',?,?)",
            code,
            invoice,
            cashier,
            amount,
            NOTE,
            Timestamp.from(clock.instant())
        );
    }

    private void event(String type, long id, String action, long actor, long counter) {
        jdbc.update(
            "insert into workflow_events(entity_type,entity_id,action,actor_user_id,counter_id,details,occurred_at) values(?,?,?,?,?,JSON_OBJECT('note',?),?)",
            type,
            id,
            action,
            actor,
            counter,
            NOTE,
            Timestamp.from(clock.instant())
        );
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long insert(String sql, Object... args) {
        return ClinicJdbc.insert(jdbc, sql, args);
    }

    private Timestamp at(LocalDate date, LocalTime time) {
        return Timestamp.from(date.atTime(time).atZone(clock.getZone()).toInstant());
    }
}
