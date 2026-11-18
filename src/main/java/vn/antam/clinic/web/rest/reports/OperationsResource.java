package vn.antam.clinic.web.rest.reports;

import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.service.dto.reports.OperationsDtos.*;
import vn.antam.clinic.service.reports.OperationsService;

@RestController("capacityOperationsResource")
@RequestMapping("/api/v1")
public class OperationsResource {

    private final OperationsService service;

    public OperationsResource(OperationsService service) {
        this.service = service;
    }

    @GetMapping("/clinic-settings/wait-thresholds")
    public Thresholds thresholds() {
        return service.thresholds();
    }

    @PutMapping("/clinic-settings/wait-thresholds")
    public Thresholds save(@Valid @RequestBody Thresholds in) {
        return service.updateThresholds(in);
    }

    @GetMapping("/dashboard/bottlenecks")
    public Bottlenecks bottlenecks(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) Long doctorId,
        @RequestParam(required = false) Long roomId
    ) {
        return service.bottlenecks(from, to, departmentId, doctorId, roomId);
    }

    @GetMapping("/reports/capacity")
    public Capacity capacity(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) Long doctorId,
        @RequestParam(required = false) Long roomId,
        @RequestParam(required = false) Long serviceId
    ) {
        return service.capacity(from, to, departmentId, doctorId, roomId, serviceId);
    }

    @GetMapping(value = "/reports/capacity.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> csv(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) Long doctorId,
        @RequestParam(required = false) Long roomId,
        @RequestParam(required = false) Long serviceId
    ) {
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=capacity.csv")
            .body(service.capacityCsv(from, to, departmentId, doctorId, roomId, serviceId));
    }
}
