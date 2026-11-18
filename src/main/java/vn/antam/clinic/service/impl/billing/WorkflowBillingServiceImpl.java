package vn.antam.clinic.service.impl.billing;

import static vn.antam.clinic.service.support.ClinicJdbc.*;
import static vn.antam.clinic.service.support.WorkflowSupport.*;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.antam.clinic.service.billing.BillingService;
import vn.antam.clinic.service.billing.WorkflowBillingService;
import vn.antam.clinic.service.support.QrImages;
import vn.antam.clinic.service.support.WorkflowSupport;
import vn.antam.clinic.service.support.billing.VietQrGateway;

@Service
public class WorkflowBillingServiceImpl implements WorkflowBillingService {

    private final WorkflowSupport w;
    private final BillingService billing;
    private final VietQrGateway gateway;

    public WorkflowBillingServiceImpl(
        WorkflowSupport w,
        BillingService billing,
        VietQrGateway gateway
    ) {
        this.w = w;
        this.billing = billing;
        this.gateway = gateway;
    }

    @Override
    public Map<String, Object> details(long id) {
        var view = billing.invoice(id);
        var invoice = w.row("select * from invoices where id=?", id);
        invoice.put("paid", view.paid());
        invoice.put("balance", view.balance());
        invoice.put(
            "refunds",
            w.db().queryForList("select * from refunds where invoice_id=? order by id", id)
        );
        return invoice;
    }

    @Override
    public Map<String, Object> paymentQr(long id) {
        require("CASHIER", "PATIENT");
        var raw = w.row("select * from invoices where id=?", id);
        var invoice = billing.invoice(id);
        if (raw.get("online_order_id") != null) throw conflict(
            "Đơn thuốc online dùng QR tài khoản chung bệnh viện tại cổng bệnh nhân."
        );
        if (raw.get("counter_id") == null) throw bad("Hóa đơn này không thuộc quầy thuốc.");
        if (role("CASHIER")) w.counter(number(raw, "counter_id"), "CASHIER");
        if (!Set.of("OPEN", "PARTIAL").contains(invoice.status())) throw conflict(
            "Hóa đơn không còn chờ thu."
        );
        if (
            w
                .db()
                .queryForObject(
                    "select count(*) from refunds where invoice_id=?",
                    Integer.class,
                    id
                ) > 0
        ) throw conflict("Phiếu đang hoàn/hủy; không tạo QR thu tiếp.");
        var bank = w.decode(text(raw, "bank_snapshot"));
        String bin = text(bank, "bank_bin"),
            account = text(bank, "account_no"),
            name = text(bank, "account_name");
        if (!bin.matches("\\d{6}") || !account.matches("\\d{6,19}")) throw conflict(
            "Quầy chưa cấu hình tài khoản ngân hàng."
        );
        BigDecimal amount = new BigDecimal(invoice.balance());
        if (
            amount.stripTrailingZeros().scale() > 0 ||
            amount.signum() <= 0 ||
            amount.compareTo(new BigDecimal("9999999999999")) > 0
        ) throw bad("QR ngân hàng chỉ nhận số tiền VND nguyên.");
        String payload = gateway.generate(bank, amount.longValueExact(), invoice.code());
        var current = w.row("select revision,status from invoices where id=?", id);
        if (
            revision(current) != revision(raw) ||
            !Set.of("OPEN", "PARTIAL").contains(text(current, "status"))
        ) throw conflict("Hóa đơn đã thay đổi trong khi tạo QR. Tải lại trước khi thu.");
        return Map.of(
            "image",
            QrImages.image(payload),
            "amount",
            invoice.balance(),
            "reference",
            invoice.code(),
            "bankBin",
            bin,
            "accountNo",
            account,
            "accountName",
            name,
            "counterName",
            text(bank, "name"),
            "revision",
            raw.get("revision")
        );
    }

    @Override
    public Object refund(long id, Map<String, Object> in, String requestKey) {
        require("CASHIER");
        var pre = w.row("select * from invoices where id=?", id);
        if (pre.get("counter_id") != null) w.counter(number(pre, "counter_id"), "CASHIER");
        return w.once("REFUND:" + id, requestKey, in, () -> {
            var i = w.row("select * from invoices where id=? for update", id);
            w.expect(i, in);
            String source = text(i, "source_type");
            if (source.equals("PHARMACY")) {
                var d = w.row(
                    "select * from dispensings where id=? for update",
                    number(i, "source_id")
                );
                if (!text(d, "status").equals("READY")) throw conflict("Chỉ hoàn phiếu chưa giao.");
            } else if (source.equals("DIAGNOSTIC")) {
                var order = w.row(
                    "select * from diagnostic_orders where id=? for update",
                    number(i, "source_id")
                );
                if (text(order, "status").equals("COMPLETED")) throw conflict(
                    "Chỉ định đã có kết quả công bố."
                );
            } else throw bad("Hoàn tiền luồng này chỉ áp dụng thuốc/chỉ định.");
            BigDecimal net = w
                    .db()
                    .queryForObject(
                        "select coalesce((select sum(amount) from receipts where invoice_id=?),0)-coalesce((select sum(amount) from refunds where invoice_id=?),0)",
                        BigDecimal.class,
                        id,
                        id
                    ),
                amount = decimal(in, "amount");
            if (amount.signum() <= 0 || amount.compareTo(net) > 0) throw conflict(
                "Số hoàn vượt khoản thực thu còn lại."
            );
            String method = required(in, "method", 20);
            if (!Set.of("CASH", "TRANSFER").contains(method)) throw bad(
                "Hình thức hoàn không hợp lệ."
            );
            if (method.equals("TRANSFER")) required(in, "transactionReference", 100);
            long refund = w.id(
                "insert into refunds(invoice_id,amount,method,reason,transaction_reference,actor_user_id) values(?,?,?,?,?,?)",
                id,
                amount,
                method,
                required(in, "reason", 500),
                text(in, "transactionReference"),
                user()
            );
            w.db().update("update invoices set status='OPEN',revision=revision+1 where id=?", id);
            w.event(
                "INVOICE",
                id,
                "REFUND",
                i.get("counter_id") == null ? null : number(i, "counter_id"),
                in
            );
            return w.row("select * from refunds where id=?", refund);
        });
    }
}
