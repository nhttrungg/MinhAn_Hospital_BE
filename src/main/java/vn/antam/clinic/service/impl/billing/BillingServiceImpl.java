package vn.antam.clinic.service.impl.billing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.ClinicUserPrincipal;
import vn.antam.clinic.security.PatientAccess;
import vn.antam.clinic.security.SecurityUtils;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.billing.BillingService;
import vn.antam.clinic.service.dto.billing.BillingDtos.InvoiceView;
import vn.antam.clinic.service.dto.billing.BillingDtos.LineView;
import vn.antam.clinic.service.dto.billing.BillingDtos.PaymentRequest;
import vn.antam.clinic.service.dto.billing.BillingDtos.ReceiptView;
import vn.antam.clinic.service.support.Money;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
public class BillingServiceImpl implements BillingService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final PatientAccess access;

    public BillingServiceImpl(JdbcTemplate jdbc, AuditService audit, PatientAccess access) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.access = access;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceView> openInvoices() {
        requireCashier();
        return findMany(
            "i.status in ('OPEN','PARTIAL') and (i.counter_id is null or exists(select 1 from counter_staff cs join staff s on s.id=cs.staff_id where cs.counter_id=i.counter_id and s.user_id=? and s.active=1))",
            SecurityUtils.currentPrincipal().getId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceView> myInvoices() {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "PATIENT")) throw forbidden();
        return findMany(PatientAccess.ownedSql("p"), actor.getId(), actor.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceView invoice(long id) {
        InvoiceView view = find(id);
        authorizeCounter(id, false);
        authorize(view);
        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceView appointmentInvoice(long appointmentId) {
        List<Long> ids = jdbc.query(
            "select id from invoices where appointment_id=? and source_type='EXAMINATION'",
            (rs, row) -> rs.getLong(1),
            appointmentId
        );
        if (ids.isEmpty()) throw notFound();
        return invoice(ids.getFirst());
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @vn.antam.clinic.service.support.RetryOnLockConflict
    public ReceiptView pay(long invoiceId, PaymentRequest request, String key) {
        ClinicUserPrincipal actor = requireCashier();
        authorizeCounter(invoiceId, true);
        if (
            jdbc.queryForObject(
                "select count(*) from invoices where id=? and online_order_id is not null",
                Integer.class,
                invoiceId
            ) > 0
        ) throw bad(
            "Đơn thuốc online thu vào tài khoản chung bệnh viện; không thu trực tiếp tại quầy."
        );
        if (
            request.method().equals("TRANSFER") &&
            jdbc.queryForObject(
                "select count(*) from invoices where id=? and counter_id is not null",
                Integer.class,
                invoiceId
            ) > 0 &&
            (request.transactionReference() == null || request.transactionReference().isBlank())
        ) throw bad("Cần mã giao dịch đối soát khi thu chuyển khoản tại quầy.");
        if (key == null || key.isBlank() || key.length() > 100) throw bad(
            "Cần mã chống gửi lặp hợp lệ."
        );
        if (!request.method().equals("CASH") && !request.method().equals("TRANSFER")) throw bad(
            "Chỉ nhận tiền mặt hoặc chuyển khoản thủ công."
        );
        return recordPayment(invoiceId, request, key, actor.getId());
    }

    /** Trusted internal settlement entry point; HTTP controllers cannot select this cashier. */
    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ReceiptView payOnline(
        long invoiceId,
        BigDecimal amount,
        String transaction,
        String key
    ) {
        Long systemId = jdbc.queryForObject(
            "select id from users where username='online-payment' and status='DISABLED'",
            Long.class
        );
        if (key == null || key.isBlank() || key.length() > 100) throw bad(
            "Mã giao dịch không hợp lệ."
        );
        return recordPayment(
            invoiceId,
            new PaymentRequest(
                Money.format(amount),
                "BANK_TRANSFER_ONLINE",
                transaction,
                "Thanh toán online"
            ),
            key,
            systemId
        );
    }

    private ReceiptView recordPayment(
        long invoiceId,
        PaymentRequest request,
        String key,
        long cashierId
    ) {
        BigDecimal amount = Money.parseVnd(request.amount());
        if (amount.signum() <= 0) throw bad("Số tiền thu phải lớn hơn 0.");
        List<Long> locked = jdbc.query(
            "select id from invoices where id=? for update",
            (rs, row) -> rs.getLong(1),
            invoiceId
        );
        if (locked.isEmpty()) throw notFound();
        String hash = digest(
            invoiceId +
                ":" +
                Money.format(amount) +
                ":" +
                request.method() +
                ":" +
                request.transactionReference() +
                ":" +
                request.note()
        );
        List<Object[]> previous = jdbc.query(
            "select request_hash,result_id from idempotency_results " +
                "where actor_user_id=? and operation='PAYMENT' and request_key=?",
            (rs, row) -> new Object[] { rs.getString(1), rs.getLong(2) },
            cashierId,
            key
        );
        if (!previous.isEmpty()) {
            if (!hash.equals(previous.getFirst()[0])) throw conflict(
                "Mã chống gửi lặp đã dùng cho yêu cầu khác."
            );
            return receipt((Long) previous.getFirst()[1]);
        }
        InvoiceView invoice = find(invoiceId);
        if (invoice.status().equals("VOID")) throw conflict("Hóa đơn đã hủy.");
        if (
            jdbc.queryForObject(
                "select count(*) from refunds where invoice_id=?",
                Integer.class,
                invoiceId
            ) > 0
        ) throw conflict("Phiếu đang hoàn/hủy; không thu tiếp.");
        if (
            invoice.status().equals("PAID") ||
            amount.compareTo(new BigDecimal(invoice.balance())) > 0
        ) throw conflict("Số tiền vượt công nợ còn lại hoặc hóa đơn đã thanh toán.");
        long receiptId = insert(
            "insert into receipts(receipt_code,invoice_id,cashier_user_id,amount,method) " +
                "values(?,?,?,?,?)",
            "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 22),
            invoiceId,
            cashierId,
            amount,
            request.method()
        );
        jdbc.update(
            "update receipts set receipt_code=?,transaction_reference=?,note=? where id=?",
            "PT" + String.format("%010d", receiptId),
            request.transactionReference(),
            request.note(),
            receiptId
        );
        jdbc.update("update invoices set revision=revision+1 where id=?", invoiceId);
        BigDecimal remainder = new BigDecimal(invoice.balance()).subtract(amount);
        jdbc.update(
            "update invoices set status=? where id=?",
            remainder.signum() == 0 ? "PAID" : "PARTIAL",
            invoiceId
        );
        jdbc.update(
            "insert into idempotency_results(actor_user_id,operation,request_key,request_hash,result_id) " +
                "values(?,'PAYMENT',?,?,?)",
            cashierId,
            key,
            hash,
            receiptId
        );
        audit.record("PAYMENT", "INVOICE", String.valueOf(invoiceId), Money.format(amount));
        return receipt(receiptId);
    }

    private InvoiceView find(long id) {
        var rows = findMany("i.id=?", id);
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst();
    }

    /** Three round trips for an entire page, preserving ownership/counter predicates. */
    private List<InvoiceView> findMany(String predicate, Object... arguments) {
        var headers = jdbc.query(
            """
            select i.*,p.full_name patient_name,
            (select coalesce(sum(l.amount),0) from invoice_lines l where l.invoice_id=i.id and l.status='ACTIVE') total,
            (select coalesce(sum(r.amount),0) from receipts r where r.invoice_id=i.id)
            -(select coalesce(sum(f.amount),0) from refunds f where f.invoice_id=i.id) paid
            from invoices i join patient_profiles p on p.id=i.patient_id where
            """ +
                predicate +
                " order by i.id desc limit 100",
            (rs, n) -> {
                BigDecimal total = rs.getBigDecimal("total"),
                    paid = rs.getBigDecimal("paid");
                return new InvoiceView(
                    rs.getLong("id"),
                    rs.getString("invoice_code"),
                    rs.getLong("patient_id"),
                    rs.getString("patient_name"),
                    rs.getObject("appointment_id") == null ? null : rs.getLong("appointment_id"),
                    rs.getString("status"),
                    Money.format(total),
                    Money.format(paid),
                    "VOID".equals(rs.getString("status"))
                        ? "0.00"
                        : Money.format(total.subtract(paid)),
                    List.of(),
                    List.of()
                );
            },
            arguments
        );
        if (headers.isEmpty()) return List.of();
        Object[] ids = headers.stream().map(InvoiceView::id).toArray();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.length, "?"));
        var lines = new java.util.HashMap<Long, java.util.List<LineView>>();
        jdbc.query(
            "select * from invoice_lines where invoice_id in (" + placeholders + ") order by id",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                lines
                    .computeIfAbsent(rs.getLong("invoice_id"), key -> new java.util.ArrayList<>())
                    .add(
                        new LineView(
                            rs.getLong("id"),
                            rs.getString("source_type"),
                            rs.getString("description_snapshot"),
                            rs.getString("unit_snapshot"),
                            Money.format(rs.getBigDecimal("quantity")),
                            Money.format(rs.getBigDecimal("unit_price")),
                            Money.format(rs.getBigDecimal("amount")),
                            rs.getString("status")
                        )
                    );
            },
            ids
        );
        var receipts = new java.util.HashMap<Long, java.util.List<ReceiptView>>();
        jdbc.query(
            "select * from receipts where invoice_id in (" + placeholders + ") order by id",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                receipts
                    .computeIfAbsent(rs.getLong("invoice_id"), key -> new java.util.ArrayList<>())
                    .add(mapReceipt(rs));
            },
            ids
        );
        return headers
            .stream()
            .map(i ->
                new InvoiceView(
                    i.id(),
                    i.code(),
                    i.patientId(),
                    i.patientName(),
                    i.appointmentId(),
                    i.status(),
                    i.total(),
                    i.paid(),
                    i.balance(),
                    List.copyOf(lines.getOrDefault(i.id(), List.of())),
                    List.copyOf(receipts.getOrDefault(i.id(), List.of()))
                )
            )
            .toList();
    }

    private ReceiptView receipt(long id) {
        return jdbc.queryForObject(
            "select * from receipts where id=?",
            (rs, row) -> mapReceipt(rs),
            id
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptView receiptById(long id) {
        List<ReceiptView> rows = jdbc.query(
            "select * from receipts where id=?",
            (r, n) -> mapReceipt(r),
            id
        );
        if (rows.isEmpty()) throw notFound();
        ReceiptView receipt = rows.getFirst();
        authorizeCounter(receipt.invoiceId(), false);
        authorize(find(receipt.invoiceId()));
        return receipt;
    }

    private static ReceiptView mapReceipt(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp recorded = rs.getTimestamp("recorded_at");
        return new ReceiptView(
            rs.getLong("id"),
            rs.getString("receipt_code"),
            rs.getLong("invoice_id"),
            Money.format(rs.getBigDecimal("amount")),
            rs.getString("method"),
            recorded.toInstant().toString()
        );
    }

    private void authorize(InvoiceView view) {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (role(actor, "CASHIER") || role(actor, "RECEPTIONIST") || role(actor, "ADMIN")) return;
        if (role(actor, "PHARMACIST")) {
            authorizeCounter(view.id(), false);
            return;
        }
        if (role(actor, "PATIENT") && access.owns(actor.getId(), view.patientId())) return;
        if (role(actor, "DOCTOR") && view.appointmentId() != null) {
            Integer assigned = jdbc.queryForObject(
                "select count(*) from appointments a " +
                    "join appointment_slots sl on sl.id=a.slot_id join staff st on st.id=sl.doctor_staff_id " +
                    "where a.id=? and st.user_id=?",
                Integer.class,
                view.appointmentId(),
                actor.getId()
            );
            if (assigned != null && assigned > 0) return;
        }
        throw forbidden();
    }

    private void authorizeCounter(long invoiceId, boolean write) {
        var rows = jdbc.queryForList("select counter_id from invoices where id=?", invoiceId);
        if (rows.isEmpty()) throw notFound();
        Object c = rows.getFirst().get("counter_id");
        if (c == null) {
            if (role(SecurityUtils.currentPrincipal(), "PHARMACIST")) throw forbidden();
            return;
        }
        var actor = SecurityUtils.currentPrincipal();
        if (
            !write && (role(actor, "ADMIN") || role(actor, "PATIENT") || role(actor, "DOCTOR"))
        ) return;
        if (!role(actor, "CASHIER") && !role(actor, "PHARMACIST")) throw forbidden();
        if (
            jdbc.queryForObject(
                "select count(*) from counter_staff cs join staff s on s.id=cs.staff_id where cs.counter_id=? and s.user_id=? and s.active=1",
                Integer.class,
                c,
                actor.getId()
            ) == 0
        ) throw forbidden();
    }

    private ClinicUserPrincipal requireCashier() {
        ClinicUserPrincipal actor = SecurityUtils.currentPrincipal();
        if (!role(actor, "CASHIER")) throw forbidden();
        return actor;
    }

    private long insert(String sql, Object... params) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
            );
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static boolean role(ClinicUserPrincipal actor, String name) {
        return actor
            .getAuthorities()
            .stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + name));
    }

    private static ApiException forbidden() {
        return new ApiException(
            HttpStatus.FORBIDDEN,
            "Không có quyền",
            "Bạn không có quyền xem hoặc thu hóa đơn này."
        );
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy", "Hóa đơn không tồn tại.");
    }

    private static ApiException bad(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Thanh toán không hợp lệ", detail);
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Không thu được tiền", detail);
    }
}
