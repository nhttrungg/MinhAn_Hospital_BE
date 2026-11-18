package vn.antam.clinic.web.rest.billing;

import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.billing.WorkflowBillingService;

@RestController
@RequestMapping("/api/v1/billing")
public class WorkflowBillingResource {

    private final WorkflowBillingService service;

    public WorkflowBillingResource(WorkflowBillingService service) {
        this.service = service;
    }

    @GetMapping("/invoices/{id}/workflow")
    public Map<String, Object> details(@PathVariable long id) {
        return service.details(id);
    }

    @PostMapping("/invoices/{id}/payment-qr")
    public Map<String, Object> paymentQr(@PathVariable long id) {
        return service.paymentQr(id);
    }

    @PostMapping("/invoices/{id}/refunds")
    public Object refund(
        @PathVariable long id,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String requestKey
    ) {
        return service.refund(id, in, requestKey);
    }
}
