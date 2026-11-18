package vn.antam.clinic.web.rest.reports;

import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.dto.reports.ReportsDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.reports.ReportsService;

@RestController
@RequestMapping("/api/v1")
public class ReportsResource {

    private final ReportsService service;

    public ReportsResource(ReportsService service) {
        this.service = service;
    }

    @GetMapping("/reports/outpatient")
    public Report report(
        @RequestParam LocalDate from,
        @RequestParam LocalDate to,
        @RequestParam(required = false) Long doctorId,
        @RequestParam(required = false) Long serviceId
    ) {
        return service.report(from, to, doctorId, serviceId);
    }

    @GetMapping(value = "/reports/outpatient.csv", produces = "text/csv;charset=UTF-8")
    public String csv(
        @RequestParam LocalDate from,
        @RequestParam LocalDate to,
        @RequestParam(required = false) Long doctorId,
        @RequestParam(required = false) Long serviceId
    ) {
        return service.csv(from, to, doctorId, serviceId);
    }

    @GetMapping("/audit-logs")
    public PageResponse<Audit> audit(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.audit(q, page, size);
    }
}
