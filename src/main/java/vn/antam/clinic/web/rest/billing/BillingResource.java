package vn.antam.clinic.web.rest.billing;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.billing.BillingService;
import vn.antam.clinic.service.dto.billing.BillingDtos.*;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingResource {

    private final BillingService billing;

    public BillingResource(BillingService billing) {
        this.billing = billing;
    }

    @GetMapping("/invoices")
    public List<InvoiceView> openInvoices() {
        return billing.openInvoices();
    }

    @GetMapping("/invoices/me")
    public List<InvoiceView> myInvoices() {
        return billing.myInvoices();
    }

    @GetMapping("/invoices/{id}")
    public InvoiceView invoice(@PathVariable long id) {
        return billing.invoice(id);
    }

    @GetMapping("/appointments/{appointmentId}/invoice")
    public InvoiceView appointmentInvoice(@PathVariable long appointmentId) {
        return billing.appointmentInvoice(appointmentId);
    }

    @GetMapping("/receipts/{id}")
    public ReceiptView receipt(@PathVariable long id) {
        return billing.receiptById(id);
    }

    @PostMapping("/invoices/{id}/payments")
    public ReceiptView pay(
        @PathVariable long id,
        @Valid @RequestBody PaymentRequest request,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return billing.pay(id, request, key);
    }
}
