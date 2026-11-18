package vn.antam.clinic.service.impl.qr;

import static vn.antam.clinic.service.support.ClinicJdbc.*;
import static vn.antam.clinic.service.support.QrImages.*;
import static vn.antam.clinic.service.support.WorkflowSupport.*;

import com.google.zxing.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.booking.BookingService;
import vn.antam.clinic.service.diagnostic.DiagnosticService;
import vn.antam.clinic.service.pharmacy.PharmacyService;
import vn.antam.clinic.service.qr.QrService;
import vn.antam.clinic.service.support.WorkflowSupport;

@Service
public class QrServiceImpl implements QrService {

    private final WorkflowSupport w;
    private final BookingService booking;
    private final PharmacyService pharmacy;
    private final DiagnosticService diagnostics;

    public QrServiceImpl(
        WorkflowSupport w,
        BookingService booking,
        PharmacyService pharmacy,
        DiagnosticService diagnostics
    ) {
        this.w = w;
        this.booking = booking;
        this.pharmacy = pharmacy;
        this.diagnostics = diagnostics;
    }

    @Override
    public Object access(String kind, long id) {
        return switch (kind) {
            case "APPOINTMENT" -> booking.viewAppointment(id);
            case "PRESCRIPTION" -> pharmacy.prescription(id);
            case "DIAGNOSTIC" -> diagnostics.order(id);
            case "SAMPLE" -> {
                var s = w.row(
                    "select s.*,o.room_id from diagnostic_samples s join diagnostic_orders o on o.id=s.order_id where s.id=?",
                    id
                );
                w.diagnostic(number(s, "room_id"), false);
                yield s;
            }
            case "BATCH" -> {
                var b = w.row(
                    "select b.*,m.name,m.strength,m.unit from medication_batches b join medications m on m.id=b.medication_id where b.id=?",
                    id
                );
                w.counterRead(number(b, "counter_id"));
                yield b;
            }
            default -> throw bad("Loại QR không hợp lệ.");
        };
    }

    private int sourceRevision(String kind, long id) {
        return switch (kind) {
            case "APPOINTMENT" -> Math.toIntExact(
                w.db().queryForObject("select slot_id from appointments where id=?", Long.class, id)
            );
            case "PRESCRIPTION", "DIAGNOSTIC", "SAMPLE", "BATCH" -> 0;
            default -> throw bad("Loại QR không hợp lệ.");
        };
    }

    @Override
    @Transactional
    public Map<String, Object> issue(String kind, long id) {
        access(kind, id);
        int rev = sourceRevision(kind, id);
        String token =
            UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "");
        w.db().update(
            "insert into business_qr(token,kind,source_id,source_revision) values(?,?,?,?) on duplicate key update token=token",
            token,
            kind,
            id,
            rev
        );
        var q = w.row(
            "select * from business_qr where kind=? and source_id=? and source_revision=?",
            kind,
            id,
            rev
        );
        if (yes(q, "revoked")) throw conflict("Mã đã thu hồi.");
        String payload = "ANTAM:" + kind + ":" + text(q, "token");
        return Map.of("kind", kind, "sourceId", id, "payload", payload, "image", image(payload));
    }

    @Override
    public Map<String, Object> resolve(String payload) {
        if (payload == null || payload.length() > 200) throw bad("QR không hợp lệ.");
        String[] parts = payload.trim().split(":");
        if (parts.length != 3 || !parts[0].equals("ANTAM")) throw bad(
            "Đây không phải mã nghiệp vụ Minh An."
        );
        var q = w.row("select * from business_qr where token=? and kind=?", parts[2], parts[1]);
        String kind = text(q, "kind");
        long id = number(q, "source_id");
        if (
            yes(q, "revoked") || number(q, "source_revision") != sourceRevision(kind, id)
        ) throw conflict("QR đã thu hồi hoặc lịch hẹn đã đổi.");
        Object data = access(kind, id);
        if (kind.equals("APPOINTMENT")) {
            String state = w
                .db()
                .queryForObject("select status from appointments where id=?", String.class, id);
            if (Set.of("CANCELLED", "NO_SHOW", "NEEDS_RESCHEDULE").contains(state)) throw conflict(
                "Lịch hẹn đã hủy, vắng hoặc cần đổi lịch."
            );
        }
        return Map.of("kind", kind, "sourceId", id, "data", data);
    }

    @Override
    @Transactional
    public Map<String, Object> revoke(String kind, long id) {
        require("ADMIN");
        access(kind, id);
        w.db().update("update business_qr set revoked=1 where kind=? and source_id=?", kind, id);
        w.event("QR", id, "REVOKE", null, Map.of("kind", kind));
        return Map.of("revoked", true);
    }
}
