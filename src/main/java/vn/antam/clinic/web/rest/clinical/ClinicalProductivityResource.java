package vn.antam.clinic.web.rest.clinical;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.service.clinical.ClinicalProductivityService;
import vn.antam.clinic.service.dto.clinical.ClinicalProductivityDtos.*;

@RestController
@RequestMapping("/api/v1")
public class ClinicalProductivityResource {

    private final ClinicalProductivityService service;

    public ClinicalProductivityResource(ClinicalProductivityService service) {
        this.service = service;
    }

    @GetMapping("/clinical-templates")
    public List<Template> templates(@RequestParam(required = false) Long specialtyId) {
        return service.templates(specialtyId);
    }

    @PostMapping("/clinical-templates")
    public Template create(@Valid @RequestBody TemplateInput in) {
        return service.createTemplate(in);
    }

    @PutMapping("/clinical-templates/{id}")
    public Template update(@PathVariable long id, @Valid @RequestBody TemplateInput in) {
        return service.updateTemplate(id, in);
    }

    @PostMapping("/clinical-templates/{id}/publish")
    public Template publish(@PathVariable long id, @Valid @RequestBody VersionRequest in) {
        return service.templateState(id, in.version(), true);
    }

    @PostMapping("/clinical-templates/{id}/deactivate")
    public Template deactivate(@PathVariable long id, @Valid @RequestBody VersionRequest in) {
        return service.templateState(id, in.version(), false);
    }

    @PutMapping("/encounters/{id}/draft-sync")
    public ChartSaved chart(@PathVariable long id, @Valid @RequestBody ChartSync in) {
        return service.saveChart(id, in);
    }

    @PutMapping("/prescriptions/encounters/{id}/draft-sync")
    public RxSaved rx(@PathVariable long id, @Valid @RequestBody RxSync in) {
        return service.saveRx(id, in);
    }

    @GetMapping("/encounters/{id}/patient-summary")
    public PatientSummary summary(@PathVariable long id) {
        return service.summary(id);
    }

    @GetMapping("/encounters/{id}/diagnostic-history")
    public DiagnosticHistory history(
        @PathVariable long id,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to
    ) {
        return service.history(id, from, to);
    }

    @PostMapping("/prescriptions/medication-availability")
    public Availability stock(@Valid @RequestBody StockRequest in) {
        return service.availability(in);
    }
}
