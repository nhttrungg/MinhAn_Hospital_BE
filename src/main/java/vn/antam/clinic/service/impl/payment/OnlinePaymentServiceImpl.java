package vn.antam.clinic.service.impl.payment;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.domain.event.ClinicEvent;
import vn.antam.clinic.security.PatientAccess;
import vn.antam.clinic.service.billing.BillingService;
import vn.antam.clinic.service.impl.identity.TotpServiceImpl;
import vn.antam.clinic.service.notification.AppointmentNotificationService;
import vn.antam.clinic.service.payment.OnlinePaymentService;
import vn.antam.clinic.service.support.Money;
import vn.antam.clinic.service.support.QrImages;
import vn.antam.clinic.service.support.payment.EmvQrBuilder;

@Service
public class OnlinePaymentServiceImpl implements OnlinePaymentService {

    private final JdbcTemplate jdbc;
    private final BillingService billing;
    private final PatientAccess access;
    private final EmvQrBuilder qr;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final AppointmentNotificationService notices;
    private final boolean simulator;
    private final int ttl;
    private final String bankBin, accountNo, accountName;
    private final ObjectMapper json = new ObjectMapper();
    private static final Pattern REFERENCE = Pattern.compile(
        "(?i)(?<![A-Z0-9])AT[A-Z0-9]{8}(?![A-Z0-9])"
    );

    public OnlinePaymentServiceImpl(
        JdbcTemplate jdbc,
        BillingService billing,
        PatientAccess access,
        EmvQrBuilder qr,
        Clock clock,
        ApplicationEventPublisher events,
        AppointmentNotificationService notices,
        @Value("${app.payments.simulator:false}") boolean simulator,
        @Value("${app.payments.intent-ttl-minutes:15}") int ttl,
        @Value("${app.payments.bank-bin:}") String bankBin,
        @Value("${app.payments.account-no:}") String accountNo,
        @Value("${app.payments.account-name:MINH AN CLINIC}") String accountName
    ) {
        this.jdbc = jdbc;
        this.billing = billing;
        this.access = access;
        this.qr = qr;
        this.clock = clock;
        this.events = events;
        this.notices = notices;
        this.simulator = simulator;
        this.ttl = Math.max(1, Math.min(ttl, 60));
        this.bankBin = bankBin;
        this.accountNo = accountNo;
        this.accountName = accountName;
    }

    @Override
    @Transactional
    public Intent create(long invoiceId) {
        require("PATIENT");
        var invoice = invoice(invoiceId);
        access.requireOwn(user(), ((Number) invoice.get("patient_id")).longValue());
        var view = billing.invoice(invoiceId);
        if (
            !Set.of("OPEN", "PARTIAL").contains(view.status()) ||
            new BigDecimal(view.balance()).signum() <= 0
        ) throw conflict("Hóa đơn không còn khoản phải thu.");
        if (
            jdbc.queryForObject(
                "select count(*) from refunds where invoice_id=?",
                Integer.class,
                invoiceId
            ) > 0
        ) throw conflict("Hóa đơn đang hoàn/hủy.");
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update(
            "update payment_intents set status='EXPIRED' where invoice_id=? and status='PENDING' and (expires_at<=? or amount<>?)",
            invoiceId,
            now,
            new BigDecimal(view.balance())
        );
        var active = jdbc.queryForList(
            "select * from payment_intents where invoice_id=? and created_by=? and status='PENDING' order by id desc limit 1",
            invoiceId,
            user()
        );
        if (!active.isEmpty()) return map(active.getFirst());
        Map<String, String> bank = bank(invoice);
        String reference =
            "AT" +
            UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String payload = qr.build(
            bank.get("bin"),
            bank.get("account"),
            bank.get("name"),
            new BigDecimal(view.balance()),
            reference
        );
        long id = insert(
            jdbc,
            "insert into payment_intents(invoice_id,reference_code,amount,expires_at,created_by,qr_payload,bank_account_no) values(?,?,?,?,?,?,?)",
            invoiceId,
            reference,
            new BigDecimal(view.balance()),
            Timestamp.from(clock.instant().plusSeconds(ttl * 60L)),
            user(),
            payload,
            bank.get("account")
        );
        return map(row(id));
    }

    @Override
    @Transactional
    public Intent get(long id) {
        var row = row(id);
        authorize(row);
        jdbc.update(
            "update payment_intents set status='EXPIRED' where id=? and status='PENDING' and expires_at<=?",
            id,
            Timestamp.from(clock.instant())
        );
        return map(row(id));
    }

    private void authorize(Map<String, Object> row) {
        if (((Number) row.get("created_by")).longValue() != user()) throw forbidden();
        Long patient = jdbc.queryForObject(
            "select patient_id from invoices where id=?",
            Long.class,
            row.get("invoice_id")
        );
        access.requireOwn(user(), patient);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Intent simulate(long id) {
        if (!simulator) throw forbidden();
        var row = row(id);
        authorize(row);
        if ("PAID".equals(row.get("status"))) return map(row);
        settle(
            new Transfer(
                "SIM-" + id,
                (String) row.get("reference_code"),
                (BigDecimal) row.get("amount"),
                null
            )
        );
        return map(row(id));
    }

    @Override
    public Transfer parse(byte[] body) {
        try {
            var root = json.readTree(body);
            String txn = root.path("transactionId").asText(root.path("id").asText());
            String direction = root.path("transferType").asText("in");
            if (!direction.equalsIgnoreCase("in")) throw bad("Chỉ xử lý tiền chuyển vào.");
            String content = root.path("content").asText();
            var amount = root.has("transferAmount")
                ? root.get("transferAmount")
                : root.get("amount");
            if (amount == null || (!amount.isNumber() && !amount.isTextual())) throw bad(
                "Thiếu số tiền chuyển khoản."
            );
            return new Transfer(
                txn,
                content,
                new BigDecimal(amount.asText()),
                root.path("accountNumber").asText(null)
            );
        } catch (vn.antam.clinic.web.rest.errors.ApiException e) {
            throw e;
        } catch (Exception e) {
            throw bad("Thông báo ngân hàng không hợp lệ.");
        }
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @vn.antam.clinic.service.support.RetryOnLockConflict
    public Map<String, Object> settle(Transfer transfer) {
        if (
            transfer.transactionId() == null ||
            transfer.transactionId().isBlank() ||
            transfer.transactionId().length() > 100 ||
            (transfer.transactionId().startsWith("SIM-") && !simulator)
        ) throw bad("Mã giao dịch không hợp lệ.");
        if (
            transfer.content() == null ||
            transfer.content().length() > 2000 ||
            transfer.amount() == null ||
            transfer.amount().signum() <= 0 ||
            transfer.amount().stripTrailingZeros().scale() > 0 ||
            transfer.amount().compareTo(new BigDecimal("9999999999999")) > 0
        ) throw bad("Số tiền hoặc nội dung chuyển khoản không hợp lệ.");
        var matcher = REFERENCE.matcher(transfer.content());
        String reference = matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
        if (matcher.find()) throw bad("Nội dung có nhiều mã thanh toán.");
        var candidates =
            reference == null
                ? List.<Map<String, Object>>of()
                : jdbc.queryForList(
                      "select * from payment_intents where reference_code=?",
                      reference
                  );
        Map<String, Object> intent = candidates.isEmpty() ? null : candidates.getFirst();
        Map<String, Object> invoice =
            intent == null ? null : invoice(((Number) intent.get("invoice_id")).longValue());
        // Lock invoice before intent, consistently with manual payment and intent creation.
        if (intent != null) intent = jdbc
            .queryForList("select * from payment_intents where id=? for update", intent.get("id"))
            .getFirst();
        String hash = TotpServiceImpl.hash(
            transfer.transactionId() +
                ":" +
                transfer.amount().stripTrailingZeros().toPlainString() +
                ":" +
                transfer.content() +
                ":" +
                transfer.accountNumber()
        );
        jdbc.update(
            "insert into online_payment_transactions(provider_txn_id,request_hash,intent_id,amount) values(?,?,?,?) on duplicate key update provider_txn_id=provider_txn_id",
            transfer.transactionId(),
            hash,
            intent == null ? null : intent.get("id"),
            transfer.amount()
        );
        var txn = jdbc
            .queryForList(
                "select * from online_payment_transactions where provider_txn_id=? for update",
                transfer.transactionId()
            )
            .getFirst();
        if (!hash.equals(txn.get("request_hash"))) throw conflict(
            "Mã ngân hàng đã dùng cho nội dung khác."
        );
        if (!"RECEIVED".equals(txn.get("status"))) return Map.of(
            "status",
            txn.get("status"),
            "duplicate",
            true
        );
        if (intent == null) {
            jdbc.update(
                "update online_payment_transactions set status='UNMATCHED' where provider_txn_id=?",
                transfer.transactionId()
            );
            return Map.of("status", "UNMATCHED");
        }
        long id = ((Number) intent.get("id")).longValue(),
            invoiceId = ((Number) intent.get("invoice_id")).longValue();
        boolean expired = !instant(intent.get("expires_at")).isAfter(clock.instant());
        var balance = jdbc.queryForObject(
            "select coalesce((select sum(amount) from invoice_lines where invoice_id=? and status='ACTIVE'),0)-coalesce((select sum(amount) from receipts where invoice_id=?),0) from invoices where id=?",
            BigDecimal.class,
            invoiceId,
            invoiceId,
            invoiceId
        );
        String expectedAccount = Objects.toString(intent.get("bank_account_no"), "");
        if (expectedAccount.isBlank()) expectedAccount = bank(invoice).get("account");
        boolean mismatch =
            !"PENDING".equals(intent.get("status")) ||
            expired ||
            !Set.of("OPEN", "PARTIAL").contains(invoice.get("status")) ||
            transfer.amount().compareTo((BigDecimal) intent.get("amount")) != 0 ||
            transfer.amount().compareTo(balance) != 0 ||
            (transfer.accountNumber() != null &&
                !transfer.accountNumber().equals(expectedAccount)) ||
            jdbc.queryForObject(
                "select count(*) from refunds where invoice_id=?",
                Integer.class,
                invoiceId
            ) > 0;
        if (mismatch) {
            jdbc.update(
                "update online_payment_transactions set status='MISMATCH' where provider_txn_id=?",
                transfer.transactionId()
            );
            if ("PENDING".equals(intent.get("status"))) jdbc.update(
                "update payment_intents set status=?,provider_txn_id=? where id=?",
                expired ? "EXPIRED" : "MISMATCH",
                transfer.transactionId(),
                id
            );
            publish(
                id,
                ((Number) intent.get("created_by")).longValue(),
                expired ? "EXPIRED" : "MISMATCH"
            );
            return Map.of("status", "MISMATCH");
        }
        var receipt = billing.payOnline(
            invoiceId,
            transfer.amount(),
            transfer.transactionId(),
            "ONLINE:" + id
        );
        jdbc.update(
            "update payment_intents set status='PAID',provider_txn_id=?,receipt_id=?,paid_at=? where id=?",
            transfer.transactionId(),
            receipt.id(),
            Timestamp.from(clock.instant()),
            id
        );
        jdbc.update(
            "update online_payment_transactions set status='PAID' where provider_txn_id=?",
            transfer.transactionId()
        );
        Long recipient = access.recipientUserId(((Number) invoice.get("patient_id")).longValue());
        if (recipient != null) {
            String title = "Đã thanh toán hóa đơn " + invoice.get("invoice_code");
            String body =
                "Đã thu " +
                Money.format(transfer.amount()) +
                " VND. Biên lai " +
                receipt.code() +
                ". Xem tại cổng bệnh nhân.";
            notices.notify(
                recipient,
                "PAYMENT",
                title,
                body,
                "/portal/invoices",
                "ONLINE_PAYMENT:" + id
            );
        }
        publish(id, ((Number) intent.get("created_by")).longValue(), "PAID");
        return Map.of("status", "PAID", "receiptId", receipt.id());
    }

    private void publish(long id, long owner, String status) {
        events.publishEvent(
            new ClinicEvent(
                "payment.updated",
                null,
                null,
                owner,
                Map.of("intentId", id, "status", status)
            )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mismatches() {
        require("CASHIER", "ADMIN");
        boolean admin = role("ADMIN");
        return jdbc.queryForList(
            "select t.provider_txn_id,t.amount received_amount,t.status,t.received_at,p.id intent_id,p.amount expected_amount,p.reference_code,i.invoice_code from online_payment_transactions t left join payment_intents p on p.id=t.intent_id left join invoices i on i.id=p.invoice_id where t.status in ('MISMATCH','UNMATCHED') and (?=1 or i.counter_id is null or exists(select 1 from counter_staff cs join staff s on s.id=cs.staff_id where cs.counter_id=i.counter_id and s.user_id=? and s.active=1)) order by t.received_at desc limit 100",
            admin ? 1 : 0,
            user()
        );
    }

    @Override
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "payment-expiry",
        lockAtMostFor = "30m",
        lockAtLeastFor = "2s"
    )
    @Scheduled(fixedDelayString = "${app.payments.expiry-delay-ms:60000}")
    @Transactional
    public void expire() {
        jdbc.update(
            "update payment_intents set status='EXPIRED' where status='PENDING' and expires_at<=?",
            Timestamp.from(clock.instant())
        );
    }

    private Map<String, Object> invoice(long id) {
        var rows = jdbc.queryForList("select * from invoices where id=? for update", id);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    private Map<String, Object> row(long id) {
        var rows = jdbc.queryForList("select * from payment_intents where id=?", id);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    private Map<String, String> bank(Map<String, Object> invoice) {
        if (invoice.get("online_order_id") != null) return Map.of(
            "bin",
            bankBin,
            "account",
            accountNo,
            "name",
            accountName
        );
        boolean counterInvoice = invoice.get("counter_id") != null;
        String bin = counterInvoice ? "" : bankBin,
            account = counterInvoice ? "" : accountNo,
            name = counterInvoice ? "" : accountName;
        Object snapshot = invoice.get("bank_snapshot");
        if (snapshot != null) try {
            var b = json.readTree(
                snapshot instanceof byte[] bytes
                    ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                    : snapshot.toString()
            );
            bin = b.path("bank_bin").asText(bin);
            account = b.path("account_no").asText(account);
            name = b.path("account_name").asText(name);
        } catch (Exception e) {
            throw conflict("Thông tin ngân hàng của hóa đơn không hợp lệ.");
        }
        return Map.of("bin", bin, "account", account, "name", name);
    }

    private Intent map(Map<String, Object> r) {
        return new Intent(
            ((Number) r.get("id")).longValue(),
            ((Number) r.get("invoice_id")).longValue(),
            (String) r.get("reference_code"),
            Money.format((BigDecimal) r.get("amount")),
            (String) r.get("status"),
            instant(r.get("expires_at")).toString(),
            r.get("receipt_id") == null ? null : ((Number) r.get("receipt_id")).longValue(),
            QrImages.image((String) r.get("qr_payload")),
            simulator
        );
    }
}
