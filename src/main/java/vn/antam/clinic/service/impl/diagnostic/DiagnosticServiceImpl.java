package vn.antam.clinic.service.impl.diagnostic;

import static vn.antam.clinic.service.support.ClinicJdbc.*;
import static vn.antam.clinic.service.support.WorkflowSupport.*;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.antam.clinic.service.diagnostic.DiagnosticService;
import vn.antam.clinic.service.support.WorkflowSupport;

@Service
public class DiagnosticServiceImpl implements DiagnosticService {

    private final WorkflowSupport w;

    public DiagnosticServiceImpl(WorkflowSupport w) {
        this.w = w;
    }

    private void readEncounter(long id) {
        if (
            role("DOCTOR") &&
            w
                .db()
                .queryForObject(
                    "select count(*) from encounters source where source.id=? and source.status='COMPLETED' and exists(select 1 from encounters current join appointments a on a.id=current.appointment_id where current.patient_id=source.patient_id and current.doctor_staff_id=? and a.status='CHECKED_IN' and current.status in ('WAITING','IN_PROGRESS','WAITING_RESULTS'))",
                    Integer.class,
                    id,
                    staff(w.db())
                ) > 0
        ) return;
        w.encounter(id, false);
    }

    @Override
    public List<Map<String, Object>> rooms() {
        require("ADMIN", "LAB_TECH", "DOCTOR");
        return w
            .db()
            .queryForList(
                "select r.*,exists(select 1 from diagnostic_staff ds join staff st on st.id=ds.staff_id where ds.room_id=r.id and st.user_id=" +
                    user() +
                    " and ds.can_review=1 and st.active=1) can_review from rooms r where r.room_type='LAB' and r.active=1 and (?=1 or exists(select 1 from diagnostic_staff ds join staff st on st.id=ds.staff_id where ds.room_id=r.id and st.user_id=? and st.active=1)) order by r.name",
                role("LAB_TECH") ? 0 : 1,
                user()
            );
    }

    @Override
    public Object config(long room) {
        require("ADMIN");
        return w
            .db()
            .queryForList(
                "select ds.*,u.full_name from diagnostic_staff ds join staff s on s.id=ds.staff_id join users u on u.id=s.user_id where ds.room_id=?",
                room
            );
    }

    @Override
    @Transactional
    public Map<String, Object> configure(long room, Map<String, Object> in) {
        require("ADMIN");
        w.row("select id from rooms where id=? and room_type='LAB' and active=1", room);
        w.db().update("delete from diagnostic_staff where room_id=?", room);
        for (var item : lines(in, "staff")) {
            long staff = number(item, "staffId");
            w.row(
                "select s.id from staff s join user_roles ur on ur.user_id=s.user_id join roles r on r.id=ur.role_id where s.id=? and s.active=1 and r.code='LAB_TECH'",
                staff
            );
            w.db().update(
                "insert into diagnostic_staff(room_id,staff_id,can_review) values(?,?,?)",
                room,
                staff,
                yes(item, "canReview")
            );
        }
        w.event("DIAGNOSTIC_ROOM", room, "ASSIGN", null, in);
        return Map.of("roomId", room, "staff", config(room));
    }

    @Override
    public Object definitions(long service) {
        require("ADMIN", "LAB_TECH", "DOCTOR");
        return w
            .db()
            .queryForList(
                "select * from diagnostic_definitions where service_id=? order by sort_order,id",
                service
            );
    }

    @Override
    @Transactional
    public Map<String, Object> definitions(long service, Map<String, Object> in) {
        require("ADMIN");
        w.row("select id from services where id=? and category in ('LAB','IMAGING')", service);
        w.db().update("delete from diagnostic_definitions where service_id=?", service);
        int index = 0;
        for (var item : lines(in, "measurements"))
            w.db().update(
                "insert into diagnostic_definitions(service_id,name,unit,reference_range,sort_order) values(?,?,?,?,?)",
                service,
                required(item, "name", 200),
                text(item, "unit"),
                text(item, "referenceRange"),
                index++
            );
        return Map.of("serviceId", service, "measurements", definitions(service));
    }

    @Override
    public Map<String, Object> order(long id) {
        var o = w.row(
            "select o.*,p.full_name patient_name,s.name service_name,r.name room_name,e.patient_id,e.doctor_staff_id from diagnostic_orders o join encounters e on e.id=o.encounter_id join patient_profiles p on p.id=e.patient_id join services s on s.id=o.service_id join rooms r on r.id=o.room_id where o.id=?",
            id
        );
        if (role("ADMIN")) {
        } else if (role("LAB_TECH")) w.diagnostic(number(o, "room_id"), false);
        else if (role("PATIENT")) w.patientEncounter(number(o, "encounter_id"));
        else readEncounter(number(o, "encounter_id"));
        o.put(
            "samples",
            role("PATIENT")
                ? List.of()
                : w
                      .db()
                      .queryForList(
                          "select * from diagnostic_samples where order_id=? order by id desc",
                          id
                      )
        );
        var results = w
            .db()
            .queryForList(
                "select r.*,u.full_name author_name,rv.full_name reviewer_name from diagnostic_results r join users u on u.id=r.author_id left join users rv on rv.id=r.reviewer_id where r.order_id=? and (?=0 or r.status='PUBLISHED') order by r.version desc",
                id,
                role("PATIENT") || (role("DOCTOR") && number(o, "doctor_staff_id") != staff(w.db()))
                    ? 1
                    : 0
            );
        for (var r : results) {
            try {
                r.put("measurements", w.json().readValue(text(r, "measurements"), List.class));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            r.put(
                "attachments",
                w
                    .db()
                    .queryForList(
                        "select id,filename,content_type from diagnostic_attachments where result_id=?",
                        number(r, "id")
                    )
            );
        }
        o.put("results", results);
        o.put(
            "invoice",
            w.row(
                "select id,status,revision from invoices where source_type='DIAGNOSTIC' and source_id=?",
                id
            )
        );
        return o;
    }

    @Override
    public Object list(Long encounter, Long room) {
        require("ADMIN", "LAB_TECH", "DOCTOR", "PATIENT");
        if (encounter != null) {
            if (role("PATIENT")) w.patientEncounter(encounter);
            else readEncounter(encounter);
            return w
                .db()
                .queryForList(
                    "select id from diagnostic_orders where encounter_id=? order by id desc",
                    encounter
                )
                .stream()
                .map(x -> order(number(x, "id")))
                .toList();
        }
        if (role("PATIENT")) return w
            .db()
            .queryForList(
                "select o.id from diagnostic_orders o join encounters e on e.id=o.encounter_id join patient_profiles p on p.id=e.patient_id where " +
                    vn.antam.clinic.security.PatientAccess.ownedSql("p") +
                    " and exists(select 1 from diagnostic_results r where r.order_id=o.id and r.status='PUBLISHED') order by o.id desc limit 100",
                user(),
                user()
            )
            .stream()
            .map(x -> order(number(x, "id")))
            .toList();
        if (role("LAB_TECH")) {
            if (room == null) throw bad("Chọn phòng thực hiện.");
            w.diagnostic(room, false);
        } else require("ADMIN");
        return w
            .db()
            .queryForList(
                "select id from diagnostic_orders where (? is null or room_id=?) order by id desc limit 100",
                room,
                room
            )
            .stream()
            .map(x -> order(number(x, "id")))
            .toList();
    }

    @Override
    @Transactional
    public Map<String, Object> create(Map<String, Object> in) {
        long encounter = number(in, "encounterId");
        w.row("select id from encounters where id=? for update", encounter);
        var e = w.encounter(encounter, true);
        if (!Set.of("IN_PROGRESS", "WAITING_RESULTS").contains(text(e, "status"))) throw conflict(
            "Lượt khám không nhận chỉ định mới."
        );
        w.expect(e, in);
        long service = number(in, "serviceId"),
            room = number(in, "roomId");
        var s = w.row(
            "select * from services where id=? and active=1 and category in ('LAB','IMAGING')",
            service
        );
        w.row("select id from rooms where id=? and room_type='LAB' and active=1", room);
        BigDecimal price = w
            .db()
            .queryForObject(
                "select unit_price from service_prices where service_id=? and effective_from<=CURRENT_DATE() and (effective_to is null or effective_to>=CURRENT_DATE()) order by effective_from desc,id desc limit 1",
                BigDecimal.class,
                service
            );
        long id = w.id(
            "insert into diagnostic_orders(encounter_id,service_id,room_id,category,notes,issued_by) values(?,?,?,?,?,?)",
            encounter,
            service,
            room,
            text(s, "category"),
            text(in, "notes"),
            user()
        );
        long invoice = w.id(
            "insert into invoices(invoice_code,appointment_id,patient_id,source_type,source_id) values(?,?,?,'DIAGNOSTIC',?)",
            "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
            e.get("appointment_id"),
            e.get("patient_id"),
            id
        );
        w.db().update(
            "update invoices set invoice_code=?,status=? where id=?",
            "HD" + String.format("%010d", invoice),
            price.signum() == 0 ? "PAID" : "OPEN",
            invoice
        );
        w.db().update(
            "insert into invoice_lines(invoice_id,source_type,source_id,description_snapshot,unit_snapshot,quantity,unit_price) values(?,'DIAGNOSTIC',?,?,?,?,?)",
            invoice,
            id,
            s.get("name"),
            s.get("unit"),
            BigDecimal.ONE,
            price
        );
        w.db().update("update encounters set revision=revision+1 where id=?", encounter);
        w.event("DIAGNOSTIC", id, "ISSUE", null, in);
        return order(id);
    }

    @Override
    @Transactional
    public Map<String, Object> action(long id, String action, Map<String, Object> in) {
        var base = w.row("select * from diagnostic_orders where id=?", id);
        w.row("select id from encounters where id=? for update", number(base, "encounter_id"));
        var o = w.row("select * from diagnostic_orders where id=? for update", id);
        w.expect(o, in);
        String state = text(o, "status");
        long room = number(o, "room_id");
        if (action.equals("cancel")) {
            w.encounter(number(o, "encounter_id"), true);
            required(in, "reason", 500);
            if (state.equals("COMPLETED") || state.equals("CANCELLED")) throw conflict(
                "Chỉ định đã hoàn tất/hủy."
            );
            var invoice = w.row(
                "select * from invoices where source_type='DIAGNOSTIC' and source_id=? for update",
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
            if (net.signum() > 0) throw conflict("Thu ngân cần hoàn khoản đã thu trước khi hủy.");
            w.db().update(
                "update invoices set status='VOID',revision=revision+1 where id=?",
                number(invoice, "id")
            );
            w.db().update(
                "update diagnostic_orders set status='CANCELLED',revision=revision+1 where id=?",
                id
            );
        } else {
            w.diagnostic(room, false);
            if (state.equals("CANCELLED") || state.equals("COMPLETED")) throw conflict(
                "Chỉ định đã đóng."
            );
            if (action.equals("accept")) {
                if (!state.equals("ISSUED")) throw conflict("Đã tiếp nhận chỉ định.");
                if (
                    !text(
                        w.row(
                            "select status from invoices where source_type='DIAGNOSTIC' and source_id=? for update",
                            id
                        ),
                        "status"
                    ).equals("PAID")
                ) throw conflict("Chỉ định chưa thanh toán đủ.");
                w.db().update(
                    "update diagnostic_orders set status='ACCEPTED',accepted_at=UTC_TIMESTAMP(6),revision=revision+1 where id=?",
                    id
                );
            } else if (action.equals("collect")) {
                if (
                    !text(o, "category").equals("LAB") ||
                    !Set.of("ACCEPTED", "IN_PROGRESS").contains(state)
                ) throw conflict("Không thể lấy mẫu ở trạng thái này.");
                if (
                    w
                        .db()
                        .queryForObject(
                            "select count(*) from diagnostic_samples where order_id=? and status='COLLECTED'",
                            Integer.class,
                            id
                        ) > 0
                ) throw conflict("Đã có mẫu hợp lệ; cần từ chối mẫu cũ trước.");
                w.id(
                    "insert into diagnostic_samples(order_id,collected_by) values(?,?)",
                    id,
                    user()
                );
                w.db().update(
                    "update diagnostic_orders set status='IN_PROGRESS',revision=revision+1 where id=?",
                    id
                );
            } else if (action.equals("reject-sample")) {
                long sample = number(in, "sampleId");
                String reason = required(in, "reason", 500);
                if (
                    w
                        .db()
                        .queryForObject(
                            "select count(*) from diagnostic_results where order_id=? and status in ('SUBMITTED','PUBLISHED')",
                            Integer.class,
                            id
                        ) > 0
                ) throw conflict("Mẫu đã có kết quả gửi duyệt/công bố.");
                if (
                    w
                        .db()
                        .update(
                            "update diagnostic_samples set status='REJECTED',rejected_reason=? where id=? and order_id=? and status='COLLECTED'",
                            reason,
                            sample,
                            id
                        ) != 1
                ) throw conflict("Mẫu đã đổi trạng thái.");
                w.db().update("update diagnostic_orders set revision=revision+1 where id=?", id);
            } else throw bad("Thao tác chỉ định không hợp lệ.");
        }
        w.event("DIAGNOSTIC", id, action.toUpperCase(), null, in);
        return order(id);
    }

    @Override
    @Transactional
    public Map<String, Object> saveResult(long id, Map<String, Object> in) {
        var o = w.row("select * from diagnostic_orders where id=? for update", id);
        w.diagnostic(number(o, "room_id"), false);
        if (
            !Set.of("ACCEPTED", "IN_PROGRESS", "COMPLETED").contains(text(o, "status"))
        ) throw conflict("Chưa tiếp nhận chỉ định.");
        if (
            text(o, "category").equals("LAB") &&
            w
                .db()
                .queryForObject(
                    "select count(*) from diagnostic_samples where order_id=? and status='COLLECTED'",
                    Integer.class,
                    id
                ) == 0
        ) throw conflict("Cần mẫu hợp lệ trước khi nhập kết quả.");
        Long sampleId = text(o, "category").equals("LAB")
            ? w
                  .db()
                  .queryForObject(
                      "select max(id) from diagnostic_samples where order_id=? and status='COLLECTED'",
                      Long.class,
                      id
                  )
            : null;
        var existing = w
            .db()
            .queryForList(
                "select * from diagnostic_results where order_id=? order by version desc limit 1",
                id
            );
        long result;
        int version = existing.isEmpty() ? 1 : (int) number(existing.getFirst(), "version");
        var values = lines(in, "measurements");
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (var item : values) {
            var def = w.row(
                "select * from diagnostic_definitions where id=? and service_id=?",
                number(item, "definitionId"),
                number(o, "service_id")
            );
            snapshots.add(
                Map.of(
                    "definitionId",
                    number(def, "id"),
                    "name",
                    text(def, "name"),
                    "unit",
                    text(def, "unit"),
                    "referenceRange",
                    text(def, "reference_range"),
                    "value",
                    required(item, "value", 200),
                    "flag",
                    text(item, "flag")
                )
            );
        }
        if (!existing.isEmpty() && text(existing.getFirst(), "status").equals("DRAFT")) {
            var old = existing.getFirst();
            w.expect(old, in);
            result = number(old, "id");
            w.db().update(
                "update diagnostic_results set measurements=?,description=?,conclusion=?,sample_id=?,revision=revision+1 where id=?",
                w.encode(snapshots),
                text(in, "description"),
                text(in, "conclusion"),
                sampleId,
                result
            );
        } else {
            if (
                !existing.isEmpty() && !text(existing.getFirst(), "status").equals("PUBLISHED")
            ) throw conflict("Kết quả đang chờ duyệt.");
            if (!existing.isEmpty()) {
                required(in, "reason", 500);
                if (number(in, "previousVersion") != version) throw conflict(
                    "Đã có phiên bản kết quả mới."
                );
                version++;
            }
            result = w.id(
                "insert into diagnostic_results(order_id,version,measurements,description,conclusion,reason,author_id,sample_id) values(?,?,?,?,?,?,?,?)",
                id,
                version,
                w.encode(snapshots),
                text(in, "description"),
                text(in, "conclusion"),
                text(in, "reason"),
                user(),
                sampleId
            );
        }
        w.event("DIAGNOSTIC", id, "SAVE_RESULT", null, in);
        return order(id);
    }

    @Override
    @Transactional
    public Map<String, Object> resultAction(long result, String action, Map<String, Object> in) {
        var r = w.row(
            "select r.*,o.room_id,o.encounter_id,o.category,o.status order_status from diagnostic_results r join diagnostic_orders o on o.id=r.order_id where r.id=? for update",
            result
        );
        w.diagnostic(number(r, "room_id"), action.equals("publish"));
        w.expect(r, in);
        long order = number(r, "order_id");
        if (text(r, "order_status").equals("CANCELLED")) throw conflict(
            "Chỉ định đã hủy; không gửi duyệt/công bố kết quả."
        );
        if (
            !action.equals("return") &&
            text(r, "category").equals("LAB") &&
            (r.get("sample_id") == null ||
                w
                    .db()
                    .queryForObject(
                        "select count(*) from diagnostic_samples where id=? and order_id=? and status='COLLECTED'",
                        Integer.class,
                        r.get("sample_id"),
                        order
                    ) == 0)
        ) throw conflict(
            "Mẫu của bản kết quả này không còn hợp lệ; cần lấy lại và cập nhật kết quả."
        );
        if (action.equals("submit")) {
            if (!text(r, "status").equals("DRAFT")) throw conflict("Kết quả không còn nháp.");
            if (
                text(r, "conclusion").isBlank() &&
                text(r, "description").isBlank() &&
                text(r, "measurements").equals("[]") &&
                w
                    .db()
                    .queryForObject(
                        "select count(*) from diagnostic_attachments where result_id=?",
                        Integer.class,
                        result
                    ) == 0
            ) throw bad("Kết quả chưa có nội dung.");
            w.db().update(
                "update diagnostic_results set status='SUBMITTED',revision=revision+1 where id=?",
                result
            );
        } else if (action.equals("publish")) {
            if (!text(r, "status").equals("SUBMITTED")) throw conflict("Kết quả chưa gửi duyệt.");
            w.db().update(
                "update diagnostic_results set status='PUBLISHED',reviewer_id=?,published_at=UTC_TIMESTAMP(6),revision=revision+1 where id=?",
                user(),
                result
            );
            w.db().update(
                "update diagnostic_orders set status='COMPLETED',revision=revision+1 where id=?",
                order
            );
        } else if (action.equals("return")) {
            w.diagnostic(number(r, "room_id"), true);
            required(in, "reason", 500);
            if (!text(r, "status").equals("SUBMITTED")) throw conflict("Kết quả chưa gửi duyệt.");
            w.db().update(
                "update diagnostic_results set status='DRAFT',revision=revision+1 where id=?",
                result
            );
        } else throw bad("Thao tác kết quả không hợp lệ.");
        w.event("DIAGNOSTIC", order, action.toUpperCase(), null, in);
        return order(order);
    }

    @Override
    @Transactional
    public Map<String, Object> attach(long result, MultipartFile file, int revision) {
        var r = w.row(
            "select r.*,o.room_id from diagnostic_results r join diagnostic_orders o on o.id=r.order_id where r.id=? for update",
            result
        );
        w.diagnostic(number(r, "room_id"), false);
        if (!text(r, "status").equals("DRAFT") || number(r, "revision") != revision) throw conflict(
            "Chỉ đính kèm vào bản nháp hiện tại."
        );
        if (file.isEmpty() || file.getSize() > 10_000_000) throw bad("Tệp tối đa 10 MB.");
        try {
            byte[] bytes = file.getBytes();
            String type;
            if (
                bytes.length > 4 &&
                bytes[0] == '%' &&
                bytes[1] == 'P' &&
                bytes[2] == 'D' &&
                bytes[3] == 'F'
            ) type = "application/pdf";
            else if (
                bytes.length > 8 &&
                bytes[0] == (byte) 0x89 &&
                bytes[1] == 'P' &&
                bytes[2] == 'N' &&
                bytes[3] == 'G'
            ) type = "image/png";
            else if (
                bytes.length > 3 &&
                bytes[0] == (byte) 0xff &&
                bytes[1] == (byte) 0xd8 &&
                bytes[2] == (byte) 0xff
            ) type = "image/jpeg";
            else throw bad("Chỉ nhận PDF, PNG hoặc JPEG hợp lệ.");
            String name = Optional.ofNullable(file.getOriginalFilename())
                .orElse("ket-qua")
                .replaceAll("[^\\p{L}\\p{N}._ -]", "_");
            if (name.length() > 200) name = name.substring(0, 200);
            long id = w.id(
                "insert into diagnostic_attachments(result_id,filename,content_type,content,uploaded_by) values(?,?,?,?,?)",
                result,
                name,
                type,
                bytes,
                user()
            );
            w.db().update("update diagnostic_results set revision=revision+1 where id=?", result);
            w.event(
                "DIAGNOSTIC",
                number(r, "order_id"),
                "ATTACH",
                null,
                Map.of("attachmentId", id, "filename", name)
            );
            return order(number(r, "order_id"));
        } catch (java.io.IOException ex) {
            throw bad("Không đọc được tệp.");
        }
    }

    @Override
    public Map<String, Object> attachment(long id) {
        var a = w.row(
            "select a.*,r.order_id,r.status from diagnostic_attachments a join diagnostic_results r on r.id=a.result_id where a.id=?",
            id
        );
        var source = order(number(a, "order_id"));
        if (
            (role("PATIENT") ||
                (role("DOCTOR") && number(source, "doctor_staff_id") != staff(w.db()))) &&
            !text(a, "status").equals("PUBLISHED")
        ) throw forbidden();
        return a;
    }
}
