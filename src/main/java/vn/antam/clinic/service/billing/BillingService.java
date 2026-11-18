package vn.antam.clinic.service.billing;

import java.math.BigDecimal;
import java.util.List;
import vn.antam.clinic.service.dto.billing.BillingDtos.InvoiceView;
import vn.antam.clinic.service.dto.billing.BillingDtos.PaymentRequest;
import vn.antam.clinic.service.dto.billing.BillingDtos.ReceiptView;

/** Business contract; implemented by the Spring-managed BillingServiceImpl. */
public interface BillingService {
    public List<InvoiceView> openInvoices();

    public List<InvoiceView> myInvoices();

    public InvoiceView invoice(long id);

    public InvoiceView appointmentInvoice(long appointmentId);

    public ReceiptView pay(long invoiceId, PaymentRequest request, String key);

    public ReceiptView payOnline(long invoiceId, BigDecimal amount, String transaction, String key);

    public ReceiptView receiptById(long id);
}
