package vn.antam.clinic.service.impl.document;

import static vn.antam.clinic.service.support.ClinicJdbc.*;
import static vn.antam.clinic.service.support.WorkflowSupport.*;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.billing.BillingService;
import vn.antam.clinic.service.booking.BookingService;
import vn.antam.clinic.service.diagnostic.DiagnosticService;
import vn.antam.clinic.service.document.DocumentService;
import vn.antam.clinic.service.pharmacy.PharmacyService;
import vn.antam.clinic.service.qr.QrService;
import vn.antam.clinic.service.support.WorkflowSupport;
import vn.antam.clinic.service.support.document.DocumentRenderer;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
public class DocumentServiceImpl implements DocumentService {

    private final WorkflowSupport w;
    private final DocumentRenderer renderer;
    private final QrService qr;
    private final BookingService booking;
    private final PharmacyService pharmacy;
    private final DiagnosticService diagnostics;
    private final BillingService billing;

    public DocumentServiceImpl(
        WorkflowSupport w,
        DocumentRenderer renderer,
        QrService qr,
        BookingService booking,
        PharmacyService pharmacy,
        DiagnosticService diagnostics,
        BillingService billing
    ) {
        this.w = w;
        this.renderer = renderer;
        this.qr = qr;
        this.booking = booking;
        this.pharmacy = pharmacy;
        this.diagnostics = diagnostics;
        this.billing = billing;
    }

    @Override
    public Map<String, Object> source(String type, long id) {
        return switch (type) {
            case "EXAMINATION_TICKET" -> {
                booking.viewAppointment(id);
                yield w.row("select * from appointments where id=?", id);
            }
            case "EXAMINATION_RESULT" -> w.encounter(id, false);
            case "PRESCRIPTION" -> {
                var p = w.row("select * from prescriptions where id=?", id);
                if (text(p, "status").equals("DRAFT")) {
                    w.encounter(number(p, "encounter_id"), true);
                    yield p;
                }
                yield pharmacy.prescription(id);
            }
            case "DIAGNOSTIC_ORDER" -> diagnostics.order(id);
            case "DIAGNOSTIC_RESULT" -> {
                var r = w.row("select * from diagnostic_results where id=?", id);
                diagnostics.order(number(r, "order_id"));
                if (role("PATIENT") && !text(r, "status").equals("PUBLISHED")) throw forbidden();
                yield r;
            }
            case "PICKING_TICKET", "DISPENSING_TICKET" -> pharmacy.dispensing(id);
            case "RECEIPT" -> {
                billing.receiptById(id);
                yield w.row(
                    "select r.*,u.full_name cashier_name from receipts r join users u on u.id=r.cashier_user_id where r.id=?",
                    id
                );
            }
            default -> throw bad("Loại giấy không hợp lệ.");
        };
    }

    @Override
    @Transactional
    public Map<String, Object> issue(String type, long id) {
        return issue(type, id, false);
    }

    @Override
    @Transactional
    public Map<String, Object> issue(String type, long id, boolean draft) {
        if (draft && !Set.of("PRESCRIPTION", "DIAGNOSTIC_RESULT").contains(type)) throw bad(
            "Loại giấy không hỗ trợ bản nháp."
        );
        var template = renderer.template(type);
        var src = source(type, id);
        String table = switch (type) {
            case "EXAMINATION_TICKET" -> "appointments";
            case "EXAMINATION_RESULT" -> "encounters";
            case "PRESCRIPTION" -> "prescriptions";
            case "DIAGNOSTIC_ORDER" -> "diagnostic_orders";
            case "DIAGNOSTIC_RESULT" -> "diagnostic_results";
            case "PICKING_TICKET", "DISPENSING_TICKET" -> "dispensings";
            case "RECEIPT" -> "receipts";
            default -> throw bad("Loại giấy không hợp lệ.");
        };
        w.row("select id from " + table + " where id=? for update", id);
        src = source(type, id);
        String status = type.equals("RECEIPT") ? "RECORDED" : text(src, "status");
        boolean official = false;
        for (var allowed : template.path("officialSourceStates"))
            if (allowed.asText().equals(status)) official = true;
        if (!official && !draft) throw conflict(
            "Chưa đủ điều kiện phát hành giấy chính thức: " + status
        );
        String version =
            (draft ? "DRAFT:" : "") +
            Objects.toString(src.get("revision"), "0") +
            ":" +
            Objects.toString(src.get("version"), "0");
        if (!draft) version = switch (type) {
            case "EXAMINATION_TICKET" -> "CHECKIN:" +
                Objects.toString(src.get("checked_in_at"), "0");
            case "PICKING_TICKET" -> "PREPARED:" + Objects.toString(src.get("prepared_at"), "0");
            case "DISPENSING_TICKET" -> "DELIVERED:" +
                Objects.toString(src.get("delivered_at"), "0");
            case "DIAGNOSTIC_ORDER" -> "ISSUED:" + Objects.toString(src.get("issued_at"), "0");
            default -> version;
        };
        String templateVersion = template.path("templateVersion").asText();
        var existing = w
            .db()
            .queryForList(
                "select id,type,source_id,source_version,template_version,issued_at from issued_documents where type=? and source_id=? and source_version=? order by id desc limit 1",
                type,
                id,
                version
            );
        if (!existing.isEmpty()) return existing.getFirst();
        Map<String, Object> snapshot = snapshot(type, id, src);
        snapshot.put("draft", draft);
        String prefix = switch (type) {
            case "EXAMINATION_TICKET" -> "PK";
            case "EXAMINATION_RESULT" -> "KQK";
            case "DIAGNOSTIC_ORDER" -> "CD";
            case "DIAGNOSTIC_RESULT" -> "KQ";
            case "PRESCRIPTION" -> "DT";
            case "PICKING_TICKET" -> "ST";
            case "DISPENSING_TICKET" -> "CP";
            default -> "PT";
        };
        snapshot.put("document.code", prefix + String.format("%010d", id) + (draft ? "-NHAP" : ""));
        snapshot.put(
            "document.issuedAt",
            LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            )
        );
        snapshot.put("document.sourceVersion", src.getOrDefault("version", 1));
        byte[] pdf = renderer.render(template, snapshot);
        long doc = w.id(
            "insert into issued_documents(type,source_id,source_version,template_version,patient_id,snapshot,pdf,issued_by) values(?,?,?,?,?,?,?,?)",
            type,
            id,
            version,
            templateVersion,
            snapshot.get("patientId"),
            w.encode(snapshot),
            pdf,
            user()
        );
        w.event(
            "DOCUMENT",
            doc,
            "ISSUE",
            null,
            Map.of("type", type, "sourceId", id, "sourceVersion", version)
        );
        return w.row(
            "select id,type,source_id,source_version,template_version,issued_at from issued_documents where id=?",
            doc
        );
    }

    private void bind(
        Map<String, Object> out,
        String prefix,
        Map<String, Object> row,
        String... mapping
    ) {
        for (int i = 0; i < mapping.length; i += 2) {
            Object value = row.get(mapping[i + 1]);
            if (value instanceof java.sql.Timestamp time) value = time
                .toInstant()
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            else if (value instanceof java.sql.Date date) value = date
                .toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            if (value != null) out.put(prefix + mapping[i], value);
        }
    }

    private Map<String, Object> snapshot(String type, long id, Map<String, Object> src) {
        Map<String, Object> s = new LinkedHashMap<>();
        var clinic = w.row(
            "select *,exists(select 1 from mock_data_runs where completed_at is not null) demo from clinic_settings where id=1"
        );
        bind(
            s,
            "clinic.",
            clinic,
            "name",
            "clinic_name",
            "address",
            "address",
            "phone",
            "phone",
            "email",
            "email"
        );
        s.put("demo", yes(clinic, "demo"));
        long patient;
        Long encounter = null,
            appointment = null;
        if (type.equals("EXAMINATION_TICKET")) {
            appointment = id;
            patient = number(src, "patient_id");
        } else if (type.equals("EXAMINATION_RESULT")) {
            encounter = id;
            patient = number(src, "patient_id");
        } else if (
            type.equals("PRESCRIPTION") ||
            type.equals("DIAGNOSTIC_ORDER") ||
            type.equals("PICKING_TICKET") ||
            type.equals("DISPENSING_TICKET")
        ) {
            encounter = number(src, "encounter_id");
            patient = w
                .db()
                .queryForObject(
                    "select patient_id from encounters where id=?",
                    Long.class,
                    encounter
                );
        } else if (type.equals("DIAGNOSTIC_RESULT")) {
            var order = w.row(
                "select * from diagnostic_orders where id=?",
                number(src, "order_id")
            );
            encounter = number(order, "encounter_id");
            patient = w
                .db()
                .queryForObject(
                    "select patient_id from encounters where id=?",
                    Long.class,
                    encounter
                );
        } else {
            var invoice = w.row("select * from invoices where id=?", number(src, "invoice_id"));
            patient = number(invoice, "patient_id");
            if (invoice.get("appointment_id") != null) appointment = number(
                invoice,
                "appointment_id"
            );
        }
        s.put("patientId", patient);
        var p = w.row("select * from patient_profiles where id=?", patient);
        bind(
            s,
            "patient.",
            p,
            "fullName",
            "full_name",
            "code",
            "patient_code",
            "dateOfBirth",
            "date_of_birth",
            "gender",
            "gender",
            "phone",
            "phone",
            "address",
            "address"
        );
        if (encounter != null) {
            var e = w.row(
                "select e.*,u.full_name doctor_name from encounters e join staff st on st.id=e.doctor_staff_id join users u on u.id=st.user_id where e.id=?",
                encounter
            );
            appointment = number(e, "appointment_id");
            bind(
                s,
                "encounter.",
                e,
                "diagnosis",
                "diagnosis",
                "conclusion",
                "conclusion",
                "chiefComplaint",
                "chief_complaint",
                "vitals",
                "vitals",
                "instructions",
                "instructions",
                "followUpDate",
                "follow_up_date",
                "completedAt",
                "completed_at"
            );
            s.put("doctor.fullName", text(e, "doctor_name"));
            s.put(
                "encounter.icd10",
                String.join(
                    "\n",
                    w
                        .db()
                        .query(
                            "select concat(d.icd_code,' · ',c.name_vi,case when d.is_primary=1 then ' (chính)' else '' end,' [DEMO ',c.source_system,']') from encounter_diagnoses d join icd10_codes c on c.code=d.icd_code where d.encounter_id=? order by d.is_primary desc,d.icd_code",
                            (r, n) -> r.getString(1),
                            encounter
                        )
                )
            );
            s.put(
                "encounter.amendments",
                String.join(
                    "\n",
                    w
                        .db()
                        .query(
                            "select concat(u.full_name,': ',a.content) from encounter_amendments a join users u on u.id=a.author_user_id where a.encounter_id=? order by a.id",
                            (r, n) -> r.getString(1),
                            encounter
                        )
                )
            );
        }
        if (appointment != null) {
            var a = w.row(
                "select a.*,sl.starts_at,u.full_name doctor_name,s.name service_name,r.name room_name from appointments a join appointment_slots sl on sl.id=a.slot_id join staff st on st.id=sl.doctor_staff_id join users u on u.id=st.user_id join services s on s.id=sl.service_id join rooms r on r.id=sl.room_id where a.id=?",
                appointment
            );
            bind(
                s,
                "appointment.",
                a,
                "code",
                "appointment_code",
                "queueNumber",
                "queue_number",
                "scheduledAt",
                "starts_at",
                "checkedInAt",
                "checked_in_at",
                "symptoms",
                "symptoms"
            );
            s.put("encounter.code", text(a, "appointment_code"));
            s.put("doctor.fullName", text(a, "doctor_name"));
            s.put("service.name", text(a, "service_name"));
            s.put("room.name", text(a, "room_name"));
            if (type.equals("EXAMINATION_TICKET")) s.put(
                "document.businessQrImage",
                qr.issue("APPOINTMENT", appointment).get("image")
            );
        }
        if (type.equals("PRESCRIPTION")) {
            bind(
                s,
                "prescription.",
                src,
                "version",
                "version",
                "finalizedAt",
                "finalized_at",
                "reason",
                "reason"
            );
            s.put("prescription.code", "DT" + String.format("%010d", id));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (var l : w
                .db()
                .queryForList(
                    "select * from prescription_lines where prescription_id=? order by id",
                    id
                )) {
                var row = new LinkedHashMap<String, Object>(l);
                row.put("name", text(l, "name_snapshot") + " " + text(l, "strength_snapshot"));
                row.put("quantityWithUnit", text(l, "quantity") + " " + text(l, "unit_snapshot"));
                row.put(
                    "frequencyAndDays",
                    text(l, "frequency") + " · " + text(l, "days") + " ngày"
                );
                rows.add(row);
            }
            s.put("prescription.lines", rows);
            if (text(src, "status").equals("FINAL")) s.put(
                "document.businessQrImage",
                qr.issue("PRESCRIPTION", id).get("image")
            );
        }
        if (type.startsWith("DIAGNOSTIC_")) {
            long orderId = type.equals("DIAGNOSTIC_ORDER") ? id : number(src, "order_id");
            var order = diagnostics.order(orderId);
            s.put("order.code", "CD" + String.format("%010d", orderId));
            s.put("service.name", text(order, "service_name"));
            s.put("room.name", text(order, "room_name"));
            bind(s, "order.", order, "issuedAt", "issued_at", "instructions", "notes");
            s.put(
                "order.items",
                List.of(
                    Map.of(
                        "serviceName",
                        text(order, "service_name"),
                        "quantity",
                        1,
                        "roomName",
                        text(order, "room_name"),
                        "note",
                        text(order, "notes")
                    )
                )
            );
            String invoiceStatus = text((Map<String, Object>) order.get("invoice"), "status");
            s.put(
                "invoice.statusLabel",
                switch (invoiceStatus) {
                    case "PAID" -> "Đã thanh toán đủ";
                    case "PARTIAL" -> "Đã thu một phần";
                    case "VOID" -> "Đã hủy";
                    default -> "Chưa thanh toán";
                }
            );
            s.put("document.businessQrImage", qr.issue("DIAGNOSTIC", orderId).get("image"));
            if (type.equals("DIAGNOSTIC_RESULT")) {
                var r = w.row(
                    "select r.*,u.full_name author_name,rv.full_name reviewer_name from diagnostic_results r join users u on u.id=r.author_id left join users rv on rv.id=r.reviewer_id where r.id=?",
                    id
                );
                bind(
                    s,
                    "result.",
                    r,
                    "description",
                    "description",
                    "conclusion",
                    "conclusion",
                    "amendmentReason",
                    "reason",
                    "publishedAt",
                    "published_at",
                    "authorName",
                    "author_name",
                    "reviewerName",
                    "reviewer_name"
                );
                s.put("result.code", "KQ" + String.format("%010d", id));
                try {
                    s.put(
                        "result.measurements",
                        w.json().readValue(text(r, "measurements"), List.class)
                    );
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                var samples = w
                    .db()
                    .queryForList(
                        "select * from diagnostic_samples where id=?",
                        r.get("sample_id")
                    );
                if (!samples.isEmpty()) {
                    s.put("sample.code", "MAU" + number(samples.getFirst(), "id"));
                    s.put("sample.collectedAt", samples.getFirst().get("collected_at"));
                }
            }
        }
        if (type.equals("PICKING_TICKET") || type.equals("DISPENSING_TICKET")) {
            var d = w.row(
                "select d.*,c.name counter_name,u.full_name prepared_name,du.full_name delivered_name from dispensings d join pharmacy_counters c on c.id=d.counter_id join users u on u.id=d.prepared_by left join users du on du.id=d.delivered_by where d.id=?",
                id
            );
            s.put("counter.name", text(d, "counter_name"));
            s.put("dispensing.code", "CP" + String.format("%010d", id));
            s.put("prescription.code", "DT" + String.format("%010d", number(d, "prescription_id")));
            s.put(
                "prescription.version",
                w
                    .db()
                    .queryForObject(
                        "select version from prescriptions where id=?",
                        Integer.class,
                        number(d, "prescription_id")
                    )
            );
            bind(
                s,
                "dispensing.",
                d,
                "preparedBy",
                "prepared_name",
                "preparedAt",
                "prepared_at",
                "deliveredBy",
                "delivered_name",
                "deliveredAt",
                "delivered_at",
                "shortageReason",
                "shortage_reason"
            );
            List<Map<String, Object>> rows = new ArrayList<>();
            for (var l : w
                .db()
                .queryForList(
                    "select dl.*,pl.name_snapshot,pl.strength_snapshot,pl.unit_snapshot,pl.quantity prescribed,b.batch_code,b.expiry_date from dispensing_lines dl join prescription_lines pl on pl.id=dl.prescription_line_id join medication_batches b on b.id=dl.batch_id where dl.dispensing_id=?",
                    id
                )) {
                var row = new LinkedHashMap<String, Object>();
                row.put("name", text(l, "name_snapshot") + " " + text(l, "strength_snapshot"));
                row.put("batchCode", text(l, "batch_code"));
                row.put("expiryDate", text(l, "expiry_date"));
                row.put("quantityWithUnit", text(l, "quantity") + " " + text(l, "unit_snapshot"));
                row.put("prescribedQuantity", text(l, "prescribed"));
                row.put("unitPrice", text(l, "unit_price"));
                row.put(
                    "amount",
                    decimal(l, "quantity").multiply(decimal(l, "unit_price")).toPlainString()
                );
                row.put(
                    "previouslyDelivered",
                    w
                        .db()
                        .queryForObject(
                            "select coalesce(sum(dl.quantity),0) from dispensing_lines dl join dispensings ds on ds.id=dl.dispensing_id where dl.prescription_line_id=? and ds.status='DELIVERED' and ds.id<>?",
                            java.math.BigDecimal.class,
                            number(l, "prescription_line_id"),
                            id
                        )
                );
                rows.add(row);
            }
            s.put("dispensing.lines", rows);
            List<String> missing = new ArrayList<>();
            for (var line : w
                .db()
                .queryForList(
                    "select pl.*,pl.quantity-(select coalesce(sum(dl.quantity),0) from dispensing_lines dl join dispensings ds on ds.id=dl.dispensing_id where dl.prescription_line_id=pl.id and ds.status='DELIVERED') remaining from prescription_lines pl where pl.prescription_id=? order by pl.id",
                    number(d, "prescription_id")
                )) {
                if (decimal(line, "remaining").signum() > 0) missing.add(
                    text(line, "name_snapshot") +
                        " " +
                        text(line, "strength_snapshot") +
                        ": " +
                        text(line, "remaining") +
                        " " +
                        text(line, "unit_snapshot")
                );
            }
            s.put(
                "dispensing.remainingItems",
                missing.isEmpty()
                    ? "Đã cấp đủ thuốc theo phiên bản đơn này."
                    : String.join("\n", missing)
            );
            s.put("dispensing.followUpInstructions", text(d, "shortage_reason"));
            s.put(
                "receipt.code",
                String.join(
                    ", ",
                    w
                        .db()
                        .query(
                            "select r.receipt_code from receipts r join invoices i on i.id=r.invoice_id where i.source_type='PHARMACY' and i.source_id=? order by r.id",
                            (r, n) -> r.getString(1),
                            id
                        )
                )
            );
        }
        if (type.equals("RECEIPT")) {
            var invoice = billing.invoice(number(src, "invoice_id"));
            bind(
                s,
                "receipt.",
                src,
                "code",
                "receipt_code",
                "recordedAt",
                "recorded_at",
                "cashierName",
                "cashier_name",
                "amount",
                "amount",
                "transactionReference",
                "transaction_reference",
                "note",
                "note"
            );
            s.put(
                "receipt.methodLabel",
                text(src, "method").equals("CASH") ? "Tiền mặt" : "Chuyển khoản"
            );
            s.put("invoice.code", invoice.code());
            s.put("invoice.total", invoice.total());
            s.put("invoice.paid", invoice.paid());
            s.put("invoice.balance", invoice.balance());
            s.put(
                "invoice.lines",
                invoice
                    .lines()
                    .stream()
                    .map(l ->
                        Map.of(
                            "description",
                            l.description(),
                            "quantityWithUnit",
                            l.quantity() + " " + l.unit(),
                            "unitPrice",
                            l.unitPrice(),
                            "amount",
                            l.amount()
                        )
                    )
                    .toList()
            );
            var i = w.row("select counter_id from invoices where id=?", invoice.id());
            if (i.get("counter_id") != null) s.put(
                "counter.name",
                w
                    .db()
                    .queryForObject(
                        "select name from pharmacy_counters where id=?",
                        String.class,
                        number(i, "counter_id")
                    )
            );
        }
        return s;
    }

    @Override
    public List<Map<String, Object>> history(long id) {
        var doc = w.row("select type,source_id from issued_documents where id=?", id);
        source(text(doc, "type"), number(doc, "source_id"));
        return w
            .db()
            .queryForList(
                "select ev.action,ev.occurred_at,u.full_name actor_name from workflow_events ev join users u on u.id=ev.actor_user_id where ev.entity_type='DOCUMENT' and ev.entity_id=? order by ev.id desc limit 100",
                id
            );
    }

    @Override
    @Transactional
    public Map<String, Object> request(long id, String action) {
        var doc = w.row("select type,source_id from issued_documents where id=?", id);
        source(text(doc, "type"), number(doc, "source_id"));
        if (!Set.of("PRINT_REQUEST", "DOWNLOAD_REQUEST").contains(action)) throw bad(
            "Loại yêu cầu chứng từ không hợp lệ."
        );
        w.event("DOCUMENT", id, action, null, Map.of());
        return Map.of("recorded", true);
    }

    @Override
    public List<Map<String, Object>> list(String type, Long source) {
        var rows = w
            .db()
            .queryForList(
                "select id,type,source_id,source_version,template_version,issued_at from issued_documents where (? is null or type=?) and (? is null or source_id=?) order by id desc limit 100",
                type,
                type,
                source,
                source
            );
        List<Map<String, Object>> visible = new ArrayList<>();
        for (var doc : rows) {
            try {
                source(text(doc, "type"), number(doc, "source_id"));
                visible.add(doc);
            } catch (ApiException denied) {
                if (
                    denied.getStatus() != org.springframework.http.HttpStatus.FORBIDDEN
                ) throw denied;
            }
        }
        return visible;
    }

    @Override
    @Transactional
    public byte[] pdf(long id) {
        var doc = w.row("select * from issued_documents where id=?", id);
        source(text(doc, "type"), number(doc, "source_id"));
        w.event("DOCUMENT", id, "DOWNLOAD_REQUEST", null, Map.of());
        return (byte[]) doc.get("pdf");
    }
}
