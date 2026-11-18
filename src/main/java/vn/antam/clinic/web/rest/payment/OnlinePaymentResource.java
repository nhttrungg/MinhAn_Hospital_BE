package vn.antam.clinic.web.rest.payment;

import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.service.audit.*;
import vn.antam.clinic.service.billing.*;
import vn.antam.clinic.service.booking.*;
import vn.antam.clinic.service.catalog.*;
import vn.antam.clinic.service.chatbot.*;
import vn.antam.clinic.service.clinical.*;
import vn.antam.clinic.service.dashboard.*;
import vn.antam.clinic.service.demo.*;
import vn.antam.clinic.service.diagnostic.*;
import vn.antam.clinic.service.document.*;
import vn.antam.clinic.service.identity.*;
import vn.antam.clinic.service.notification.*;
import vn.antam.clinic.service.organization.*;
import vn.antam.clinic.service.outpatient.*;
import vn.antam.clinic.service.payment.*;
import vn.antam.clinic.service.pharmacy.*;
import vn.antam.clinic.service.qr.*;
import vn.antam.clinic.service.realtime.*;
import vn.antam.clinic.service.reports.*;
import vn.antam.clinic.service.shared.*;
import vn.antam.clinic.service.staff.*;
import vn.antam.clinic.service.support.payment.*;

@RestController
public class OnlinePaymentResource {

    private final OnlinePaymentService payments;
    private final PaymentWebhookSignature signatures;

    public OnlinePaymentResource(
        OnlinePaymentService payments,
        PaymentWebhookSignature signatures
    ) {
        this.payments = payments;
        this.signatures = signatures;
    }

    @PostMapping("/api/v1/billing/invoices/{id}/payment-intents")
    public Object create(@PathVariable long id) {
        return payments.create(id);
    }

    @GetMapping("/api/v1/payment-intents/{id}")
    public Object get(@PathVariable long id) {
        return payments.get(id);
    }

    @PostMapping("/api/v1/payment-intents/{id}/simulate")
    public Object simulate(@PathVariable long id) {
        return payments.simulate(id);
    }

    @PostMapping("/api/v1/public/payments/webhook")
    public Object webhook(
        @RequestBody byte[] body,
        @RequestHeader(value = "X-Payment-Signature", required = false) String signature
    ) {
        signatures.verify(body, signature);
        return payments.settle(payments.parse(body));
    }

    @GetMapping("/api/v1/payments/mismatches")
    public Object mismatches() {
        return payments.mismatches();
    }
}
