package vn.antam.clinic.service.impl.pharmacy;

import static vn.antam.clinic.service.support.ClinicJdbc.*;
import static vn.antam.clinic.service.support.WorkflowSupport.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.pharmacy.OnlinePharmacyService;
import vn.antam.clinic.service.pharmacy.PharmacyService;
import vn.antam.clinic.service.support.Money;
import vn.antam.clinic.service.support.WorkflowSupport;

@Service
public class OnlinePharmacyServiceImpl implements OnlinePharmacyService {

    private final WorkflowSupport w;
    private final PharmacyService pharmacy;
    private final Clock clock;
    private final String hospitalBin, hospitalAccount;

    public OnlinePharmacyServiceImpl(
        WorkflowSupport w,
        PharmacyService pharmacy,
        Clock clock,
        @Value("${app.payments.bank-bin:}") String hospitalBin,
        @Value("${app.payments.account-no:}") String hospitalAccount
    ) {
        this.w = w;
        this.pharmacy = pharmacy;
        this.clock = clock;
        this.hospitalBin = hospitalBin;
        this.hospitalAccount = hospitalAccount;
    }

    private JdbcTemplate db() {
        return w.db();
    }

    @Override
    public Map<String, Object> encounterOrder(long encounter) {
        w.patientEncounter(encounter);
        Long prescription = db().queryForObject(
            "select max(id) from prescriptions where encounter_id=? and status='FINAL'",
            Long.class,
            encounter
        );
        var orders = db().queryForList(
            "select * from pharmacy_online_orders where encounter_id=? order by id desc limit 1",
            encounter
        );
        var result = new LinkedHashMap<String, Object>();
        result.put("prescriptionId", prescription);
        result.put("order", orders.isEmpty() ? null : view(number(orders.getFirst(), "id")));
        return result;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Override
    public Map<String, Object> place(long prescription, String key) {
        require("PATIENT");
        return w.once(
            "ONLINE_PHARMACY:" + prescription,
            key,
            Map.of("prescriptionId", prescription),
            () -> {
                var p = w.row("select * from prescriptions where id=?", prescription);
                long encounter = number(p, "encounter_id");
                w.patientEncounter(encounter);
                w.row("select id from encounters where id=? for update", encounter);
                if (
                    !hospitalBin.matches("\\d{6}") || !hospitalAccount.matches("\\d{6,19}")
                ) throw conflict("Bệnh viện chưa cấu hình tài khoản thu thuốc online.");
                var prescriptionView = pharmacy.prescription(prescription);
                if (!Boolean.TRUE.equals(prescriptionView.get("latest"))) throw conflict(
                    "Đơn thuốc đã được thay bằng phiên bản mới."
                );
                var existing = db().queryForList(
                    "select id from pharmacy_online_orders where encounter_id=? and status='OPEN' order by id desc",
                    encounter
                );
                if (!existing.isEmpty()) throw conflict(
                    "Lượt khám đã có đơn thuốc online. Xem đơn hiện tại trước khi đặt lại."
                );
                if (
                    db().queryForObject(
                        "select count(*) from dispensings where encounter_id=? and status='DRAFT'",
                        Integer.class,
                        encounter
                    ) > 0
                ) throw conflict("Quầy đang soạn đơn này. Liên hệ quầy trước khi đặt online.");
                var remaining = new ArrayList<Map<String, Object>>();
                for (var line : lines(prescriptionView, "lines")) {
                    var quantity = decimal(line, "quantity")
                        .subtract(decimal(line, "delivered"))
                        .subtract(decimal(line, "reserved"));
                    if (quantity.signum() > 0) {
                        var copy = new LinkedHashMap<>(line);
                        copy.put("needed", quantity);
                        remaining.add(copy);
                    }
                }
                if (remaining.isEmpty()) throw conflict(
                    "Đơn đã được cấp hoặc đang được giữ thuốc đầy đủ."
                );
                var medicationIds = remaining
                    .stream()
                    .map(l -> number(l, "medication_id"))
                    .distinct()
                    .toList();
                String placeholders = String.join(
                    ",",
                    Collections.nCopies(medicationIds.size(), "?")
                );
                var params = new ArrayList<Object>(medicationIds);
                params.add(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))));
                // Stable locking order protects the complete allocation from concurrent sales.
                var batches = db().queryForList(
                    "select b.*,(select mp.price from medication_prices mp where mp.counter_id=b.counter_id and mp.medication_id=b.medication_id and mp.effective_at<=UTC_TIMESTAMP(6) order by mp.effective_at desc,mp.id desc limit 1) price from medication_batches b join pharmacy_counters c on c.id=b.counter_id join medications m on m.id=b.medication_id where b.medication_id in (" +
                        placeholders +
                        ") and c.active=1 and m.active=1 and b.active=1 and b.expiry_date>? and b.quantity>b.reserved and exists(select 1 from counter_staff cs join staff s on s.id=cs.staff_id join users u on u.id=s.user_id join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id where cs.counter_id=c.id and s.active=1 and u.status='ACTIVE' and r.code='PHARMACIST') order by b.id for update",
                    params.toArray()
                );
                batches.sort(
                    Comparator.comparing((Map<String, Object> b) -> text(b, "expiry_date"))
                        .thenComparingLong(b -> number(b, "counter_id"))
                        .thenComparingLong(b -> number(b, "id"))
                );
                var plan = new ArrayList<Map<String, Object>>();
                var available = new HashMap<Long, BigDecimal>();
                for (var b : batches)
                    available.put(
                        number(b, "id"),
                        decimal(b, "quantity").subtract(decimal(b, "reserved"))
                    );
                for (var line : remaining) {
                    BigDecimal needed = decimal(line, "needed");
                    for (var b : batches) {
                        if (
                            number(b, "medication_id") != number(line, "medication_id") ||
                            b.get("price") == null
                        ) continue;
                        BigDecimal price = decimal(b, "price"),
                            free = available.get(number(b, "id"));
                        if (
                            price.signum() < 0 ||
                            price.stripTrailingZeros().scale() > 0 ||
                            free.signum() <= 0
                        ) continue;
                        BigDecimal picked = needed.min(free);
                        plan.add(
                            Map.of("line", line, "batch", b, "quantity", picked, "price", price)
                        );
                        available.put(number(b, "id"), free.subtract(picked));
                        needed = needed.subtract(picked);
                        if (needed.signum() == 0) break;
                    }
                    if (needed.signum() > 0) throw conflict(
                        "Các quầy chưa đủ tồn khả dụng hoặc giá hợp lệ cho " +
                            text(line, "name_snapshot") +
                            ". Chưa tạo đơn hay giữ thuốc."
                    );
                }
                long order = w.id(
                    "insert into pharmacy_online_orders(prescription_id,encounter_id,created_by,expires_at) values(?,?,?,?)",
                    prescription,
                    encounter,
                    user(),
                    Timestamp.from(clock.instant().plusSeconds(1800))
                );
                var dispensingIds = new LinkedHashMap<Long, Long>();
                var invoiceIds = new HashMap<Long, Long>();
                for (var pick : plan) {
                    @SuppressWarnings("unchecked")
                    var b = (Map<String, Object>) pick.get("batch");
                    @SuppressWarnings("unchecked")
                    var line = (Map<String, Object>) pick.get("line");
                    long counter = number(b, "counter_id");
                    if (!dispensingIds.containsKey(counter)) {
                        var counterData = w.row(
                            "select * from pharmacy_counters where id=?",
                            counter
                        );
                        long dispensing = w.id(
                            "insert into dispensings(encounter_id,prescription_id,counter_id,status,prepared_by) values(?,?,?,'READY',?)",
                            encounter,
                            prescription,
                            counter,
                            user()
                        );
                        long invoice = w.id(
                            "insert into invoices(invoice_code,appointment_id,patient_id,source_type,source_id,counter_id,bank_snapshot,online_order_id) select ?,e.appointment_id,e.patient_id,'PHARMACY',?,?,?,? from encounters e where e.id=?",
                            "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                            dispensing,
                            counter,
                            w.encode(counterData),
                            order,
                            encounter
                        );
                        db().update(
                            "update invoices set invoice_code=? where id=?",
                            "HD" + String.format("%010d", invoice),
                            invoice
                        );
                        dispensingIds.put(counter, dispensing);
                        invoiceIds.put(counter, invoice);
                    }
                    long dispensing = dispensingIds.get(counter);
                    BigDecimal quantity = (BigDecimal) pick.get("quantity"),
                        price = (BigDecimal) pick.get("price");
                    long dispensingLine = w.id(
                        "insert into dispensing_lines(dispensing_id,prescription_line_id,batch_id,quantity,unit_price,checked) values(?,?,?,?,?,0)",
                        dispensing,
                        number(line, "id"),
                        number(b, "id"),
                        quantity,
                        price
                    );
                    db().update(
                        "insert into invoice_lines(invoice_id,source_type,source_id,description_snapshot,unit_snapshot,quantity,unit_price) values(?,'MEDICATION',?,?,?,?,?)",
                        invoiceIds.get(counter),
                        dispensingLine,
                        text(line, "name_snapshot") + " " + text(line, "strength_snapshot"),
                        text(line, "unit_snapshot"),
                        quantity,
                        price
                    );
                    pharmacy.movement(
                        number(b, "id"),
                        BigDecimal.ZERO,
                        quantity,
                        "RESERVE",
                        dispensing,
                        "Giữ thuốc đặt online #" + order
                    );
                    w.event(
                        "DISPENSING",
                        dispensing,
                        "ONLINE_ALLOCATE",
                        counter,
                        Map.of("orderId", order)
                    );
                }
                for (long invoice : invoiceIds.values()) {
                    BigDecimal total = db().queryForObject(
                        "select coalesce(sum(amount),0) from invoice_lines where invoice_id=?",
                        BigDecimal.class,
                        invoice
                    );
                    if (total.stripTrailingZeros().scale() > 0) throw conflict(
                        "Số tiền thuốc online phải là số VND nguyên."
                    );
                    if (total.signum() == 0) db().update(
                        "update invoices set status='PAID' where id=?",
                        invoice
                    );
                }
                w.event(
                    "ONLINE_PHARMACY",
                    order,
                    "PLACE",
                    null,
                    Map.of("prescriptionId", prescription)
                );
                return view(order);
            }
        );
    }

    private Map<String, Object> view(long order) {
        var result = w.row("select * from pharmacy_online_orders where id=?", order);
        var allocations = db().queryForList(
            "select i.id invoiceId,i.invoice_code invoiceCode,c.id counterId,c.name counterName,d.id dispensingId,d.status dispensingStatus,i.status invoiceStatus,coalesce((select sum(amount) from invoice_lines where invoice_id=i.id),0) total,coalesce((select sum(amount) from receipts where invoice_id=i.id),0) collected,coalesce((select sum(amount) from refunds where invoice_id=i.id),0) refunded from invoices i join pharmacy_counters c on c.id=i.counter_id join dispensings d on d.id=i.source_id where i.online_order_id=? order by c.id",
            order
        );
        for (var allocation : allocations)
            allocation.put(
                "lines",
                db().queryForList(
                    "select description_snapshot name,quantity,unit_snapshot unit,unit_price price,amount from invoice_lines where invoice_id=? order by id",
                    allocation.get("invoiceId")
                )
            );
        result.put("allocations", allocations);
        result.put(
            "total",
            allocations
                .stream()
                .map(a -> new BigDecimal(a.get("total").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
        return result;
    }

    @Transactional
    @Override
    public Map<String, Object> cancel(long order, String key) {
        require("PATIENT");
        var base = w.row("select encounter_id from pharmacy_online_orders where id=?", order);
        w.patientEncounter(number(base, "encounter_id"));
        return w.once("ONLINE_CANCEL:" + order, key, Map.of("orderId", order), () -> {
            release(order, "CANCELLED", user());
            return view(order);
        });
    }

    private boolean release(long order, String state, long actor) {
        var base = w.row("select encounter_id from pharmacy_online_orders where id=?", order);
        w.row("select id from encounters where id=? for update", number(base, "encounter_id"));
        var saved = w.row("select * from pharmacy_online_orders where id=? for update", order);
        if (!text(saved, "status").equals("OPEN")) return true;
        var invoices = db().queryForList(
            "select i.*,d.status dispensing_status from invoices i join dispensings d on d.id=i.source_id where online_order_id=? order by i.id for update",
            order
        );
        for (var invoice : invoices) {
            BigDecimal net = db().queryForObject(
                "select coalesce((select sum(amount) from receipts where invoice_id=?),0)-coalesce((select sum(amount) from refunds where invoice_id=?),0)",
                BigDecimal.class,
                number(invoice, "id"),
                number(invoice, "id")
            );
            if (net.signum() > 0 || text(invoice, "dispensing_status").equals("DELIVERED")) {
                if (state.equals("EXPIRED")) return false;
                throw conflict(
                    "Đơn đã có thanh toán hoặc đã giao. Liên hệ thu ngân để xử lý hoàn tiền."
                );
            }
        }
        for (var invoice : invoices) {
            long dispensing = number(invoice, "source_id");
            if (
                text(invoice, "dispensing_status").equals("READY")
            ) for (var line : db().queryForList(
                "select * from dispensing_lines where dispensing_id=? order by batch_id",
                dispensing
            )) {
                var b = w.row(
                    "select * from medication_batches where id=? for update",
                    number(line, "batch_id")
                );
                BigDecimal quantity = decimal(line, "quantity"),
                    reserved = decimal(b, "reserved").subtract(quantity);
                if (reserved.signum() < 0) throw conflict("Tồn giữ đã thay đổi; cần đối soát kho.");
                db().update(
                    "update medication_batches set reserved=?,revision=revision+1 where id=?",
                    reserved,
                    number(b, "id")
                );
                db().update(
                    "insert into stock_movements(batch_id,action,quantity_delta,reserved_delta,quantity_after,reserved_after,dispensing_id,actor_user_id,reason) values(?,'RELEASE',0,?,?,?,?,?,?)",
                    number(b, "id"),
                    quantity.negate(),
                    b.get("quantity"),
                    reserved,
                    dispensing,
                    actor,
                    "Hủy/hết hạn đơn online #" + order
                );
            }
            db().update(
                "update invoices set status='VOID',revision=revision+1 where id=?",
                number(invoice, "id")
            );
            db().update(
                "update payment_intents set status='EXPIRED' where invoice_id=? and status='PENDING'",
                number(invoice, "id")
            );
            db().update(
                "update dispensings set status='CANCELLED',revision=revision+1 where id=? and status='READY'",
                dispensing
            );
        }
        db().update("update pharmacy_online_orders set status=? where id=?", state, order);
        return true;
    }

    @Scheduled(fixedDelayString = "${app.pharmacy.online-expiry-delay-ms:60000}")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "pharmacy-online-expiry",
        lockAtMostFor = "10m"
    )
    @Transactional
    @Override
    public void expire() {
        for (var order : db().queryForList(
            "select o.id,o.created_by from pharmacy_online_orders o where o.status='OPEN' and o.expires_at<=? and not exists(select 1 from invoices i join dispensings d on d.id=i.source_id where i.online_order_id=o.id and (d.status='DELIVERED' or coalesce((select sum(amount) from receipts where invoice_id=i.id),0)-coalesce((select sum(amount) from refunds where invoice_id=i.id),0)>0)) order by o.id limit 100",
            Timestamp.from(clock.instant())
        ))
            release(number(order, "id"), "EXPIRED", number(order, "created_by"));
    }

    @Transactional
    @Override
    public Map<String, Object> verify(long dispensing, String key) {
        var base = w.row("select * from dispensings where id=?", dispensing);
        long counter = number(base, "counter_id");
        w.counter(counter, "PHARMACIST");
        return w.once(
            "ONLINE_VERIFY:" + dispensing,
            key,
            Map.of("dispensingId", dispensing),
            () -> {
                w.row(
                    "select id from encounters where id=? for update",
                    number(base, "encounter_id")
                );
                var saved = w.row("select * from dispensings where id=? for update", dispensing);
                w.row(
                    "select id from invoices where source_type='PHARMACY' and source_id=? and online_order_id is not null and status<>'VOID'",
                    dispensing
                );
                if (!text(saved, "status").equals("READY")) throw conflict(
                    "Phiếu online không còn chờ kiểm thuốc."
                );
                var p = pharmacy.prescription(number(saved, "prescription_id"));
                if (!Boolean.TRUE.equals(p.get("latest"))) throw conflict(
                    "Đơn thuốc đã được thay thế; cần đối soát trước khi giao."
                );
                db().update(
                    "update dispensing_lines set checked=1 where dispensing_id=?",
                    dispensing
                );
                db().update(
                    "update dispensings set prepared_by=?,prepared_at=UTC_TIMESTAMP(6),revision=revision+1 where id=?",
                    user(),
                    dispensing
                );
                w.event("DISPENSING", dispensing, "ONLINE_VERIFY", counter, Map.of());
                return pharmacy.dispensing(dispensing);
            }
        );
    }

    @Override
    public Map<String, Object> finances(long counter) {
        w.counterRead(counter);
        BigDecimal collected = db().queryForObject(
            "select coalesce(sum(r.amount),0) from receipts r join invoices i on i.id=r.invoice_id where i.counter_id=? and i.online_order_id is not null and r.method='BANK_TRANSFER_ONLINE'",
            BigDecimal.class,
            counter
        );
        BigDecimal refunded = db().queryForObject(
            "select coalesce(sum(r.amount),0) from refunds r join invoices i on i.id=r.invoice_id where i.counter_id=? and i.online_order_id is not null",
            BigDecimal.class,
            counter
        );
        BigDecimal transferred = db().queryForObject(
            "select coalesce(sum(amount),0) from pharmacy_counter_settlements where counter_id=?",
            BigDecimal.class,
            counter
        );
        BigDecimal balance = collected.subtract(refunded).subtract(transferred);
        return Map.of(
            "collected",
            collected,
            "refunded",
            refunded,
            "transferred",
            transferred,
            "outstanding",
            balance.max(BigDecimal.ZERO),
            "recoverable",
            balance.negate().max(BigDecimal.ZERO),
            "invoices",
            db().queryForList(
                "select i.id,i.invoice_code invoiceCode,i.status,counter_id counterId,coalesce((select sum(amount) from receipts where invoice_id=i.id and method='BANK_TRANSFER_ONLINE'),0) collected,coalesce((select sum(amount) from refunds where invoice_id=i.id),0) refunded from invoices i where counter_id=? and online_order_id is not null order by i.id desc limit 200",
                counter
            ),
            "settlements",
            db().queryForList(
                "select id,amount,transaction_reference transactionReference,note,recorded_at recordedAt from pharmacy_counter_settlements where counter_id=? order by id desc limit 200",
                counter
            )
        );
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Override
    public Map<String, Object> recordSettlement(
        long counter,
        Map<String, Object> input,
        String key
    ) {
        require("ADMIN");
        return w.once("COUNTER_SETTLEMENT:" + counter, key, input, () -> {
            var c = w.row("select * from pharmacy_counters where id=? for update", counter);
            BigDecimal amount = Money.parseVnd(required(input, "amount", 30));
            var finance = finances(counter);
            if (
                amount.signum() <= 0 ||
                amount.compareTo((BigDecimal) finance.get("outstanding")) > 0
            ) throw conflict("Số tiền chuyển trả vượt khoản online còn phải trả quầy.");
            if (
                !text(c, "bank_bin").matches("\\d{6}") ||
                !text(c, "account_no").matches("\\d{6,19}") ||
                text(c, "account_name").isBlank()
            ) throw conflict("Quầy chưa cấu hình tài khoản nhận tiền chuyển trả.");
            String reference = required(input, "transactionReference", 100);
            if (
                db().queryForObject(
                    "select count(*) from pharmacy_counter_settlements where transaction_reference=?",
                    Integer.class,
                    reference
                ) > 0
            ) throw conflict("Mã giao dịch chuyển trả đã được ghi nhận.");
            w.id(
                "insert into pharmacy_counter_settlements(counter_id,amount,transaction_reference,note,bank_bin,account_no,account_name,recorded_by) values(?,?,?,?,?,?,?,?)",
                counter,
                amount,
                reference,
                required(input, "note", 500),
                text(c, "bank_bin"),
                text(c, "account_no"),
                text(c, "account_name"),
                user()
            );
            w.event("COUNTER", counter, "ONLINE_SETTLEMENT", counter, input);
            return finances(counter);
        });
    }
}
