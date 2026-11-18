package vn.antam.clinic.web.rest.pharmacy;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.service.pharmacy.OnlinePharmacyService;

@RestController
@RequestMapping("/api/v1/pharmacy")
public class OnlinePharmacyResource {

    private final OnlinePharmacyService service;

    public OnlinePharmacyResource(OnlinePharmacyService service) {
        this.service = service;
    }

    @GetMapping("/encounters/{id}/online-order")
    public Map<String, Object> get(@PathVariable long id) {
        return service.encounterOrder(id);
    }

    @PostMapping("/prescriptions/{id}/online-orders")
    public Map<String, Object> place(
        @PathVariable long id,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return service.place(id, key);
    }

    @PostMapping("/online-orders/{id}/cancel")
    public Map<String, Object> cancel(
        @PathVariable long id,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return service.cancel(id, key);
    }

    @PostMapping("/online-dispensings/{id}/verify")
    public Map<String, Object> verify(
        @PathVariable long id,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return service.verify(id, key);
    }

    @GetMapping("/counters/{id}/online-settlements")
    public Map<String, Object> finances(@PathVariable long id) {
        return service.finances(id);
    }

    @PostMapping("/counters/{id}/online-settlements")
    public Map<String, Object> settle(
        @PathVariable long id,
        @RequestBody Map<String, Object> body,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return service.recordSettlement(id, body, key);
    }
}
