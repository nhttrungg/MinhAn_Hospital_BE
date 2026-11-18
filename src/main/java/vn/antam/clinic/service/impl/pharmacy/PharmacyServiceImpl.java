package vn.antam.clinic.service.impl.pharmacy;

import static vn.antam.clinic.service.support.ClinicJdbc.*;
import static vn.antam.clinic.service.support.WorkflowSupport.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.pharmacy.PharmacyService;
import vn.antam.clinic.service.support.WorkflowSupport;

@Service
public class PharmacyServiceImpl implements PharmacyService {

    private final WorkflowSupport w;

    public PharmacyServiceImpl(WorkflowSupport w) {
        this.w = w;
    }

    @Override
    public List<Map<String, Object>> counters() {
        require("ADMIN", "PHARMACIST", "CASHIER");
        return w
            .db()
            .queryForList(
                "select c.* from pharmacy_counters c where (?=1 or exists(select 1 from counter_staff cs join staff s on s.id=cs.staff_id where cs.counter_id=c.id and s.user_id=? and s.active=1)) order by c.name",
                role("ADMIN") ? 1 : 0,
                user()
            );
    }

    @Override
    public Map<String, Object> configuration(long id) {
        require("ADMIN");
        var c = w.row("select * from pharmacy_counters where id=?", id);
        c.put(
            "staff",
            w
                .db()
                .queryForList(
                    "select s.id,u.full_name,r.code role from counter_staff cs join staff s on s.id=cs.staff_id join users u on u.id=s.user_id join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id where cs.counter_id=?",
                    id
                )
        );
        return c;
    }

    @Override
    @Transactional
    public Map<String, Object> configure(Long id, Map<String, Object> in) {
        require("ADMIN");
        long room = number(in, "roomId");
        w.row("select id from rooms where id=? and room_type='PHARMACY' and active=1", room);
        String bin = text(in, "bankBin"),
            account = text(in, "accountNo"),
            name = text(in, "accountName");
        if (
            !bin.isBlank() &&
            (!bin.matches("\\d{6}") ||
                !account.matches("\\d{6,19}") ||
                !name.matches("[A-Z0-9 ]{5,50}"))
        ) throw bad("Thông tin ngân hàng không hợp lệ.");
        if (id == null) id = w.id(
            "insert into pharmacy_counters(code,name,room_id,bank_bin,account_no,account_name) values(?,?,?,?,?,?)",
            required(in, "code", 30),
            required(in, "name", 200),
            room,
            bin,
            account,
            name
        );
        else {
            var saved = w.row("select * from pharmacy_counters where id=? for update", id);
            w.expect(saved, in);
            if (
                !yes(in, "active") &&
                w
                    .db()
                    .queryForObject(
                        "select count(*) from dispensings where counter_id=? and status in ('DRAFT','READY')",
                        Integer.class,
                        id
                    ) > 0
            ) throw conflict("Quầy còn phiếu chưa xử lý.");
            w.db().update(
                "update pharmacy_counters set name=?,room_id=?,bank_bin=?,account_no=?,account_name=?,active=?,revision=revision+1 where id=?",
                required(in, "name", 200),
                room,
                bin,
                account,
                name,
                yes(in, "active"),
                id
            );
        }
        w.db().update("delete from counter_staff where counter_id=?", id);
        for (var s : lines(in, "staff")) {
            long sid = number(s, "staffId");
            w.row(
                "select s.id from staff s join users u on u.id=s.user_id join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id where s.id=? and s.active=1 and u.status='ACTIVE' and r.code in ('PHARMACIST','CASHIER')",
                sid
            );
            w.db().update("insert into counter_staff(counter_id,staff_id) values(?,?)", id, sid);
        }
        w.event("COUNTER", id, "CONFIGURE", id, in);
        return configuration(id);
    }

    @Override
    public List<Map<String, Object>> stock(long counter) {
        w.counterRead(counter);
        return w
            .db()
            .queryForList(
                "select b.*,m.code medication_code,m.name,m.strength,m.unit,b.quantity-b.reserved available,(select p.price from medication_prices p where p.counter_id=b.counter_id and p.medication_id=b.medication_id and p.effective_at<=UTC_TIMESTAMP(6) order by p.effective_at desc,p.id desc limit 1) price from medication_batches b join medications m on m.id=b.medication_id where b.counter_id=? order by b.expiry_date,b.id",
                counter
            );
    }

    @Override
    @Transactional
    public Map<String, Object> price(long c, Map<String, Object> in) {
        require("ADMIN");
        w.row("select id from pharmacy_counters where id=?", c);
        long med = number(in, "medicationId");
        w.row("select id from medications where id=? and active=1", med);
        BigDecimal p = decimal(in, "price");
        if (p.signum() < 0) throw bad("Giá không được âm.");
        long id = w.id(
            "insert into medication_prices(counter_id,medication_id,price) values(?,?,?)",
            c,
            med,
            p
        );
        w.event("COUNTER", c, "PRICE", c, in);
        return w.row("select * from medication_prices where id=?", id);
    }

    @Override
    @Transactional
    public Map<String, Object> stockChange(long c, String action, Map<String, Object> in) {
        if (role("ADMIN")) w.counterRead(c);
        else w.counter(c, "PHARMACIST");
        String reason = required(in, "reason", 500);
        if (action.equals("receive")) {
            long med = number(in, "medicationId");
            w.row("select id from medications where id=? and active=1", med);
            LocalDate expiry;
            try {
                expiry = LocalDate.parse(required(in, "expiryDate", 10));
            } catch (Exception e) {
                throw bad("Hạn dùng không hợp lệ.");
            }
            if (!expiry.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) throw bad(
                "Chỉ nhập thuốc chưa hết hạn."
            );
            BigDecimal q = decimal(in, "quantity");
            if (q.signum() <= 0) throw bad("Số lượng phải lớn hơn 0.");
            w.row("select id from pharmacy_counters where id=? for update", c);
            String code = required(in, "batchCode", 100);
            var old = w
                .db()
                .queryForList(
                    "select id from medication_batches where counter_id=? and medication_id=? and batch_code=? and expiry_date=?",
                    c,
                    med,
                    code,
                    expiry
                );
            long b = old.isEmpty()
                ? w.id(
                      "insert into medication_batches(counter_id,medication_id,batch_code,expiry_date) values(?,?,?,?)",
                      c,
                      med,
                      code,
                      expiry
                  )
                : number(old.getFirst(), "id");
            movement(b, q, BigDecimal.ZERO, "RECEIVE", null, reason);
            return w.row("select * from medication_batches where id=?", b);
        }
        long bid = number(in, "batchId");
        var b = w.row(
            "select * from medication_batches where id=? and counter_id=? for update",
            bid,
            c
        );
        w.expect(b, in);
        BigDecimal q = decimal(in, "quantity");
        if (q.signum() < 0) throw bad("Số lượng không được âm.");
        if (action.equals("adjust")) {
            BigDecimal delta = q.subtract(decimal(b, "quantity"));
            movement(bid, delta, BigDecimal.ZERO, "ADJUST", null, reason);
        } else if (action.equals("transfer")) {
            long dest = number(in, "destinationCounterId");
            if (dest == c || q.signum() <= 0) throw bad("Quầy nhận / số lượng không hợp lệ.");
            if (!role("ADMIN")) w.counter(dest, "PHARMACIST");
            else w.counterRead(dest);
            w.row("select id from pharmacy_counters where id=? and active=1 for update", dest);
            movement(bid, q.negate(), BigDecimal.ZERO, "TRANSFER_OUT", null, reason);
            var existing = w
                .db()
                .queryForList(
                    "select id from medication_batches where counter_id=? and medication_id=? and batch_code=? and expiry_date=?",
                    dest,
                    number(b, "medication_id"),
                    text(b, "batch_code"),
                    b.get("expiry_date")
                );
            long target = existing.isEmpty()
                ? w.id(
                      "insert into medication_batches(counter_id,medication_id,batch_code,expiry_date) values(?,?,?,?)",
                      dest,
                      number(b, "medication_id"),
                      text(b, "batch_code"),
                      b.get("expiry_date")
                  )
                : number(existing.getFirst(), "id");
            movement(target, q, BigDecimal.ZERO, "TRANSFER_IN", null, reason);
        } else if (action.equals("status")) {
            if (!yes(in, "active") && decimal(b, "reserved").signum() > 0) throw conflict(
                "Lô đang giữ cho phiếu cấp thuốc."
            );
            w.db().update(
                "update medication_batches set active=?,revision=revision+1 where id=?",
                yes(in, "active"),
                bid
            );
        } else throw bad("Thao tác kho không hợp lệ.");
        w.event("COUNTER", c, "STOCK_" + action.toUpperCase(), c, in);
        return w.row("select * from medication_batches where id=?", bid);
    }

    @Override
    public void movement(
        long batch,
        BigDecimal delta,
        BigDecimal reserve,
        String action,
        Long dispensing,
        String reason
    ) {
        var b = w.row("select * from medication_batches where id=? for update", batch);
        BigDecimal q = decimal(b, "quantity").add(delta),
            r = decimal(b, "reserved").add(reserve);
        if (q.signum() < 0 || r.signum() < 0 || r.compareTo(q) > 0) throw conflict(
            "Lô không đủ tồn khả dụng hoặc đã thay đổi."
        );
        w.db().update(
            "update medication_batches set quantity=?,reserved=?,revision=revision+1 where id=?",
            q,
            r,
            batch
        );
        w.db().update(
            "insert into stock_movements(batch_id,action,quantity_delta,reserved_delta,quantity_after,reserved_after,dispensing_id,actor_user_id,reason) values(?,?,?,?,?,?,?,?,?)",
            batch,
            action,
            delta,
            reserve,
            q,
            r,
            dispensing,
            user(),
            reason
        );
    }

    @Override
    public Map<String, Object> prescription(long id) {
        var p = w.row(
            "select p.*,e.patient_id,pp.full_name patient_name from prescriptions p join encounters e on e.id=p.encounter_id join patient_profiles pp on pp.id=e.patient_id where p.id=?",
            id
        );
        require("PHARMACIST", "DOCTOR", "PATIENT", "ADMIN");
        if (role("DOCTOR") || role("PATIENT")) w.encounter(number(p, "encounter_id"), false);
        else if (!role("ADMIN") && counters().isEmpty()) throw forbidden();
        if (!text(p, "status").equals("FINAL")) throw conflict("Chỉ đối chiếu đơn đã chốt.");
        p.put(
            "lines",
            w
                .db()
                .queryForList(
                    "select l.*,(select coalesce(sum(dl.quantity),0) from dispensing_lines dl join dispensings d on d.id=dl.dispensing_id where dl.prescription_line_id=l.id and d.status='DELIVERED') delivered,(select coalesce(sum(dl.quantity),0) from dispensing_lines dl join dispensings d on d.id=dl.dispensing_id where dl.prescription_line_id=l.id and d.status='READY') reserved from prescription_lines l where prescription_id=? order by l.id",
                    id
                )
        );
        p.put(
            "latest",
            w
                .db()
                .queryForObject(
                    "select max(id) from prescriptions where encounter_id=? and status='FINAL'",
                    Long.class,
                    number(p, "encounter_id")
                ) == id
        );
        p.put(
            "order",
            w
                .db()
                .queryForList(
                    "select * from pharmacy_orders where encounter_id=?",
                    number(p, "encounter_id")
                )
        );
        return p;
    }

    @Override
    @Transactional
    public Map<String, Object> claim(long c, long prescription, Map<String, Object> in) {
        w.counter(c, "PHARMACIST");
        var p = prescription(prescription);
        long encounter = number(p, "encounter_id");
        w.row("select id from encounters where id=? for update", encounter);
        p = prescription(prescription);
        if (!Boolean.TRUE.equals(p.get("latest"))) throw conflict(
            "Đơn đã được thay bằng phiên bản mới."
        );
        var existing = w
            .db()
            .queryForList("select * from pharmacy_orders where encounter_id=?", encounter);
        if (existing.isEmpty()) w.db().update(
            "insert into pharmacy_orders(encounter_id,prescription_id,counter_id) values(?,?,?)",
            encounter,
            prescription,
            c
        );
        else {
            var o = existing.getFirst();
            if (number(o, "counter_id") != c || text(o, "status").equals("CLOSED")) throw conflict(
                "Đơn đã thuộc quầy khác hoặc đã đóng."
            );
            if (number(o, "prescription_id") != prescription) throw conflict(
                "Cần đối chiếu phiên bản đơn mới trước."
            );
        }
        w.event("PHARMACY_ORDER", encounter, "CLAIM", c, in);
        return prescription(prescription);
    }

    @Override
    public List<Map<String, Object>> orders(long c) {
        w.counterRead(c);
        return w
            .db()
            .queryForList(
                "select o.*,p.full_name patient_name,pr.version from pharmacy_orders o join encounters e on e.id=o.encounter_id join patient_profiles p on p.id=e.patient_id join prescriptions pr on pr.id=o.prescription_id where o.counter_id=? order by e.id desc limit 100",
                c
            );
    }

    @Override
    public List<Map<String, Object>> dispensings(long c) {
        w.counterRead(c);
        return w
            .db()
            .queryForList(
                "select d.*,p.full_name patient_name,i.id invoice_id,i.status invoice_status,i.online_order_id,(select count(*) from dispensing_lines dl where dl.dispensing_id=d.id and dl.checked=0) unchecked_lines from dispensings d join encounters e on e.id=d.encounter_id join patient_profiles p on p.id=e.patient_id left join invoices i on i.source_type='PHARMACY' and i.source_id=d.id where d.counter_id=? order by d.id desc limit 100",
                c
            );
    }

    @Override
    public Map<String, Object> dispensing(long id) {
        var d = w.row(
            "select d.*,p.full_name patient_name,i.id invoice_id,i.status invoice_status from dispensings d join encounters e on e.id=d.encounter_id join patient_profiles p on p.id=e.patient_id left join invoices i on i.source_type='PHARMACY' and i.source_id=d.id where d.id=?",
            id
        );
        if (role("PATIENT")) w.encounter(number(d, "encounter_id"), false);
        else w.counterRead(number(d, "counter_id"));
        d.put(
            "lines",
            w
                .db()
                .queryForList(
                    "select l.*,pl.name_snapshot name,pl.strength_snapshot strength,pl.unit_snapshot unit,b.batch_code,b.expiry_date from dispensing_lines l join prescription_lines pl on pl.id=l.prescription_line_id join medication_batches b on b.id=l.batch_id where l.dispensing_id=? order by l.id",
                    id
                )
        );
        return d;
    }

    @Override
    @Transactional
    public Map<String, Object> draft(long c, Long id, Map<String, Object> in) {
        w.counter(c, "PHARMACIST");
        long prescription = number(in, "prescriptionId");
        var p = prescription(prescription);
        long encounter = number(p, "encounter_id");
        w.row("select id from encounters where id=? for update", encounter);
        var order = w.row(
            "select * from pharmacy_orders where encounter_id=? and counter_id=? and status='OPEN'",
            encounter,
            c
        );
        if (
            number(order, "prescription_id") != prescription ||
            !Boolean.TRUE.equals(p.get("latest"))
        ) throw conflict("Đơn đã đổi phiên bản.");
        if (id == null) id = w.id(
            "insert into dispensings(encounter_id,prescription_id,counter_id,prepared_by,shortage_reason) values(?,?,?,?,?)",
            encounter,
            prescription,
            c,
            user(),
            text(in, "shortageReason")
        );
        else {
            var saved = w.row(
                "select * from dispensings where id=? and counter_id=? for update",
                id,
                c
            );
            w.expect(saved, in);
            if (
                !text(saved, "status").equals("DRAFT") ||
                number(saved, "prescription_id") != prescription
            ) throw conflict("Phiếu không còn là nháp của đơn này.");
            w.db().update("delete from dispensing_lines where dispensing_id=?", id);
            w.db().update(
                "update dispensings set revision=revision+1,shortage_reason=? where id=?",
                text(in, "shortageReason"),
                id
            );
        }
        for (var line : lines(in, "lines")) {
            long pl = number(line, "prescriptionLineId"),
                batch = number(line, "batchId");
            var l = w.row(
                "select * from prescription_lines where id=? and prescription_id=?",
                pl,
                prescription
            );
            w.row(
                "select id from medication_batches where id=? and counter_id=? and medication_id=?",
                batch,
                c,
                number(l, "medication_id")
            );
            BigDecimal q = decimal(line, "quantity");
            if (q.signum() <= 0 || !yes(line, "checked")) throw bad(
                "Cần kiểm đúng thuốc và số lượng dương từng dòng."
            );
            w.db().update(
                "insert into dispensing_lines(dispensing_id,prescription_line_id,batch_id,quantity,checked) values(?,?,?,?,1)",
                id,
                pl,
                batch,
                q
            );
        }
        w.event("DISPENSING", id, "SAVE_DRAFT", c, in);
        return dispensing(id);
    }

    @Override
    @Transactional
    public Map<String, Object> act(long id, String action, Map<String, Object> in) {
        var base = w.row("select * from dispensings where id=?", id);
        long c = number(base, "counter_id"),
            encounter = number(base, "encounter_id");
        w.counter(c, "PHARMACIST");
        w.row("select id from encounters where id=? for update", encounter);
        var d = w.row("select * from dispensings where id=? for update", id);
        w.expect(d, in);
        String state = text(d, "status");
        if (action.equals("commit")) {
            if (!state.equals("DRAFT")) throw conflict("Chỉ chốt phiếu nháp.");
            var p = prescription(number(d, "prescription_id"));
            if (!Boolean.TRUE.equals(p.get("latest"))) throw conflict("Đơn đã được thay thế.");
            var ls = w
                .db()
                .queryForList(
                    "select * from dispensing_lines where dispensing_id=? order by batch_id,id",
                    id
                );
            if (ls.isEmpty()) throw bad("Chưa nhặt thuốc.");
            for (var pl : lines(p, "lines")) {
                BigDecimal picked = w
                    .db()
                    .queryForObject(
                        "select coalesce(sum(quantity),0) from dispensing_lines where dispensing_id=? and prescription_line_id=?",
                        BigDecimal.class,
                        id,
                        number(pl, "id")
                    );
                if (
                    decimal(pl, "quantity")
                        .subtract(decimal(pl, "delivered"))
                        .subtract(decimal(pl, "reserved"))
                        .compareTo(picked) > 0 &&
                    text(d, "shortage_reason").isBlank()
                ) throw bad("Cần ghi lý do phần thuốc còn thiếu.");
            }
            for (var l : ls) {
                var b = w.row(
                    "select b.*,m.active medication_active from medication_batches b join medications m on m.id=b.medication_id where b.id=? for update",
                    number(l, "batch_id")
                );
                if (
                    !yes(b, "active") ||
                    !yes(b, "medication_active") ||
                    !LocalDate.parse(text(b, "expiry_date")).isAfter(
                        LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                    )
                ) throw conflict("Lô đã ngừng hoặc hết hạn.");
                var pl = w.row(
                    "select * from prescription_lines where id=?",
                    number(l, "prescription_line_id")
                );
                BigDecimal used = w
                    .db()
                    .queryForObject(
                        "select coalesce(sum(dl.quantity),0) from dispensing_lines dl join dispensings ds on ds.id=dl.dispensing_id where dl.prescription_line_id=? and ds.status in ('READY','DELIVERED')",
                        BigDecimal.class,
                        number(pl, "id")
                    );
                BigDecimal total = w
                    .db()
                    .queryForObject(
                        "select sum(quantity) from dispensing_lines where dispensing_id=? and prescription_line_id=?",
                        BigDecimal.class,
                        id,
                        number(pl, "id")
                    );
                if (used.add(total).compareTo(decimal(pl, "quantity")) > 0) throw conflict(
                    "Số nhặt vượt phần còn lại của đơn."
                );
                var price = w.row(
                    "select price from medication_prices where counter_id=? and medication_id=? and effective_at<=UTC_TIMESTAMP(6) order by effective_at desc,id desc limit 1",
                    c,
                    number(pl, "medication_id")
                );
                w.db().update(
                    "update dispensing_lines set unit_price=? where id=?",
                    price.get("price"),
                    number(l, "id")
                );
                movement(
                    number(l, "batch_id"),
                    BigDecimal.ZERO,
                    decimal(l, "quantity"),
                    "RESERVE",
                    id,
                    "Chốt soạn thuốc"
                );
            }
            var counter = w.row("select * from pharmacy_counters where id=?", c);
            var e = w.row("select patient_id,appointment_id from encounters where id=?", encounter);
            long invoice = w.id(
                "insert into invoices(invoice_code,appointment_id,patient_id,source_type,source_id,counter_id,bank_snapshot) values(?,?,?,'PHARMACY',?,?,?)",
                "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                e.get("appointment_id"),
                e.get("patient_id"),
                id,
                c,
                w.encode(counter)
            );
            w.db().update(
                "update invoices set invoice_code=? where id=?",
                "HD" + String.format("%010d", invoice),
                invoice
            );
            w.db().update(
                "insert into invoice_lines(invoice_id,source_type,source_id,description_snapshot,unit_snapshot,quantity,unit_price) select ?,'MEDICATION',dl.id,concat(pl.name_snapshot,' ',pl.strength_snapshot),pl.unit_snapshot,dl.quantity,dl.unit_price from dispensing_lines dl join prescription_lines pl on pl.id=dl.prescription_line_id where dl.dispensing_id=?",
                invoice,
                id
            );
            if (
                w
                    .db()
                    .queryForObject(
                        "select sum(amount) from invoice_lines where invoice_id=?",
                        BigDecimal.class,
                        invoice
                    )
                    .signum() == 0
            ) w.db().update("update invoices set status='PAID' where id=?", invoice);
            w.db().update(
                "update dispensings set status='READY',revision=revision+1,prepared_at=UTC_TIMESTAMP(6) where id=?",
                id
            );
        } else if (action.equals("deliver")) {
            if (!state.equals("READY")) throw conflict("Phiếu chưa chốt hoặc đã giao.");
            var invoice = w.row(
                "select * from invoices where source_type='PHARMACY' and source_id=? for update",
                id
            );
            if (
                invoice.get("online_order_id") != null &&
                w
                    .db()
                    .queryForObject(
                        "select count(*) from dispensing_lines where dispensing_id=? and checked=0",
                        Integer.class,
                        id
                    ) > 0
            ) throw conflict("Dược sĩ cần xác nhận kiểm thuốc online trước khi giao.");
            if (!text(invoice, "status").equals("PAID")) throw conflict(
                "Thu ngân chưa xác nhận thanh toán đủ."
            );
            for (var l : w
                .db()
                .queryForList(
                    "select * from dispensing_lines where dispensing_id=? order by batch_id",
                    id
                )) {
                var b = w.row(
                    "select b.*,m.active medication_active from medication_batches b join medications m on m.id=b.medication_id where b.id=? for update",
                    number(l, "batch_id")
                );
                if (
                    !yes(b, "active") ||
                    !yes(b, "medication_active") ||
                    !LocalDate.parse(text(b, "expiry_date")).isAfter(
                        LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                    )
                ) throw conflict("Lô không còn được cấp. Cần hoàn tiền/hủy phiếu.");
                movement(
                    number(l, "batch_id"),
                    decimal(l, "quantity").negate(),
                    decimal(l, "quantity").negate(),
                    "DELIVER",
                    id,
                    "Giao thuốc đã thu tiền"
                );
            }
            w.db().update(
                "update dispensings set status='DELIVERED',revision=revision+1,delivered_by=?,delivered_at=UTC_TIMESTAMP(6) where id=?",
                user(),
                id
            );
        } else if (action.equals("cancel")) {
            if (!state.equals("DRAFT") && !state.equals("READY")) throw conflict(
                "Không thể hủy phiếu đã giao/hủy."
            );
            String reason = required(in, "reason", 500);
            if (state.equals("READY")) {
                var invoice = w.row(
                    "select * from invoices where source_type='PHARMACY' and source_id=? for update",
                    id
                );
                BigDecimal net = w
                    .db()
                    .queryForObject(
                        "select coalesce((select sum(amount) from receipts where invoice_id=?),0)-coalesce((select sum(amount) from refunds where invoice_id=?),0)",
                        BigDecimal.class,
                        number(invoice, "id"),
                        number(invoice, "id")
                    );
                if (net.signum() > 0) throw conflict(
                    "Thu ngân phải ghi nhận hoàn tiền trước khi hủy."
                );
                w.db().update(
                    "update invoices set status='VOID',revision=revision+1 where id=?",
                    number(invoice, "id")
                );
                for (var l : w
                    .db()
                    .queryForList(
                        "select * from dispensing_lines where dispensing_id=? order by batch_id",
                        id
                    ))
                    movement(
                        number(l, "batch_id"),
                        BigDecimal.ZERO,
                        decimal(l, "quantity").negate(),
                        "RELEASE",
                        id,
                        reason
                    );
            }
            w.db().update(
                "update dispensings set status='CANCELLED',revision=revision+1 where id=?",
                id
            );
        } else throw bad("Thao tác phiếu không hợp lệ.");
        w.event("DISPENSING", id, action.toUpperCase(), c, in);
        return dispensing(id);
    }

    @Override
    @Transactional
    public Map<String, Object> orderAction(
        long c,
        long encounter,
        String action,
        Map<String, Object> in
    ) {
        w.counter(c, "PHARMACIST");
        w.row("select id from encounters where id=? for update", encounter);
        var o = w.row(
            "select * from pharmacy_orders where encounter_id=? and counter_id=? for update",
            encounter,
            c
        );
        w.expect(o, in);
        String reason = required(in, "reason", 500);
        if (action.equals("transfer")) {
            long destination = number(in, "destinationCounterId");
            if (destination == c) throw bad("Chọn quầy khác.");
            w.row("select id from pharmacy_counters where id=? and active=1", destination);
            w.db().queryForList(
                "select i.id from invoices i join dispensings d on i.source_type='PHARMACY' and i.source_id=d.id where d.encounter_id=? order by i.id for update",
                encounter
            );
            if (
                w
                    .db()
                    .queryForObject(
                        "select count(*) from dispensings d where d.encounter_id=? and (d.status='DELIVERED' or exists(select 1 from invoices i join receipts r on r.invoice_id=i.id where i.source_type='PHARMACY' and i.source_id=d.id))",
                        Integer.class,
                        encounter
                    ) > 0
            ) throw conflict("Đơn đã có thanh toán/giao, phải ở cùng quầy.");
            for (var d : w
                .db()
                .queryForList(
                    "select id,revision from dispensings where encounter_id=? and status in ('DRAFT','READY') order by id for update",
                    encounter
                ))
                act(
                    number(d, "id"),
                    "cancel",
                    Map.of("revision", d.get("revision"), "reason", reason)
                );
            w.db().update(
                "update pharmacy_orders set counter_id=?,revision=revision+1 where encounter_id=?",
                destination,
                encounter
            );
        } else if (action.equals("close")) {
            if (
                w
                    .db()
                    .queryForObject(
                        "select count(*) from dispensings where encounter_id=? and status in ('DRAFT','READY')",
                        Integer.class,
                        encounter
                    ) > 0
            ) throw conflict("Hủy/xử lý phiếu đang soạn trước.");
            w.db().update(
                "update pharmacy_orders set status='CLOSED',close_reason=?,revision=revision+1 where encounter_id=?",
                reason,
                encounter
            );
        } else throw bad("Thao tác không hợp lệ.");
        w.event("PHARMACY_ORDER", encounter, action.toUpperCase(), c, in);
        return w.row("select * from pharmacy_orders where encounter_id=?", encounter);
    }

    @Override
    public Map<String, Object> trace(long c, long encounter) {
        w.counterRead(c);
        w.row(
            "select encounter_id from pharmacy_orders where encounter_id=? and counter_id=?",
            encounter,
            c
        );
        return Map.of(
            "events",
            w
                .db()
                .queryForList(
                    "select ev.*,u.full_name actor_name from workflow_events ev join users u on u.id=ev.actor_user_id where (ev.entity_type='PHARMACY_ORDER' and ev.entity_id=?) or (ev.entity_type='DISPENSING' and ev.entity_id in(select id from dispensings where encounter_id=?)) order by ev.id",
                    encounter,
                    encounter
                ),
            "movements",
            w
                .db()
                .queryForList(
                    "select sm.* from stock_movements sm join dispensings d on d.id=sm.dispensing_id where d.encounter_id=? order by sm.id",
                    encounter
                ),
            "dispensings",
            w
                .db()
                .queryForList(
                    "select * from dispensings where encounter_id=? order by id",
                    encounter
                ),
            "lines",
            w
                .db()
                .queryForList(
                    "select p.version,pl.name_snapshot name,pl.quantity prescribed,dl.quantity picked,b.batch_code,b.expiry_date,d.id dispensing_id,d.status,i.invoice_code,i.status invoice_status,(select coalesce(sum(amount),0) from receipts where invoice_id=i.id) collected,(select coalesce(sum(amount),0) from refunds where invoice_id=i.id) refunded from dispensings d join dispensing_lines dl on dl.dispensing_id=d.id join prescriptions p on p.id=d.prescription_id join prescription_lines pl on pl.id=dl.prescription_line_id join medication_batches b on b.id=dl.batch_id left join invoices i on i.source_type='PHARMACY' and i.source_id=d.id where d.encounter_id=? order by d.id,dl.id",
                    encounter
                )
        );
    }
}
