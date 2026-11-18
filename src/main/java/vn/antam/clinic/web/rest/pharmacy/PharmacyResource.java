package vn.antam.clinic.web.rest.pharmacy;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy")
public class PharmacyResource {

    private final vn.antam.clinic.service.pharmacy.PharmacyWorkflowService workflow;

    public PharmacyResource(vn.antam.clinic.service.pharmacy.PharmacyWorkflowService workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/alerts")
    public vn.antam.clinic.service.pharmacy.PharmacyAlertService.Alerts alerts(
        @org.springframework.web.bind.annotation.RequestParam(required = false) Long counterId
    ) {
        return workflow.alerts(counterId);
    }

    @GetMapping("/medications")
    public Object medications() {
        return workflow.medications();
    }

    @GetMapping("/counter-destinations")
    public Object destinations() {
        return workflow.destinations();
    }

    @GetMapping("/counters")
    public Object counters() {
        return workflow.counters();
    }

    @GetMapping("/counters/{id}")
    public Object configuration(@PathVariable long id) {
        return workflow.configuration(id);
    }

    @PostMapping("/counters")
    public Object create(
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.create(in, key);
    }

    @PutMapping("/counters/{id}")
    public Object edit(
        @PathVariable long id,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.edit(id, in, key);
    }

    @GetMapping("/counters/{id}/stock")
    public Object stock(@PathVariable long id) {
        return workflow.stock(id);
    }

    @PostMapping("/counters/{id}/prices")
    public Object price(
        @PathVariable long id,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.price(id, in, key);
    }

    @PostMapping("/counters/{id}/stock/{action}")
    public Object change(
        @PathVariable long id,
        @PathVariable String action,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.change(id, action, in, key);
    }

    @GetMapping("/prescriptions/{id}")
    public Object prescription(@PathVariable long id) {
        return workflow.prescription(id);
    }

    @GetMapping("/encounters/{id}/fulfillment")
    public Object fulfillment(@PathVariable long id) {
        return workflow.fulfillment(id);
    }

    @PostMapping("/counters/{id}/claim/{prescription}")
    public Object claim(
        @PathVariable long id,
        @PathVariable long prescription,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.claim(id, prescription, in, key);
    }

    @GetMapping("/counters/{id}/orders")
    public Object orders(@PathVariable long id) {
        return workflow.orders(id);
    }

    @GetMapping("/counters/{id}/dispensings")
    public Object dispensings(@PathVariable long id) {
        return workflow.dispensings(id);
    }

    @GetMapping("/dispensings/{id}")
    public Object dispensing(@PathVariable long id) {
        return workflow.dispensing(id);
    }

    @PostMapping("/counters/{id}/dispensings")
    public Object draft(
        @PathVariable long id,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.draft(id, in, key);
    }

    @PutMapping("/dispensings/{id}")
    public Object editDraft(
        @PathVariable long id,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.editDraft(id, in, key);
    }

    @PostMapping("/dispensings/{id}/{action}")
    public Object action(
        @PathVariable long id,
        @PathVariable String action,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.action(id, action, in, key);
    }

    @PostMapping("/counters/{id}/orders/{encounter}/{action}")
    public Object orderAction(
        @PathVariable long id,
        @PathVariable long encounter,
        @PathVariable String action,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.orderAction(id, encounter, action, in, key);
    }

    @GetMapping("/counters/{id}/orders/{encounter}/trace")
    public Object trace(@PathVariable long id, @PathVariable long encounter) {
        return workflow.trace(id, encounter);
    }

    @GetMapping("/counters/{id}/movements")
    public Object movements(@PathVariable long id) {
        return workflow.movements(id);
    }

    @GetMapping("/counters/{id}/report")
    public Object report(@PathVariable long id) {
        return workflow.report(id);
    }
}
