package vn.antam.clinic.web.rest.catalog;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.catalog.OutpatientCatalogService;
import vn.antam.clinic.service.dto.catalog.OutpatientCatalogDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;

@RestController
@RequestMapping("/api/v1")
public class OutpatientCatalogResource {

    private final OutpatientCatalogService service;

    public OutpatientCatalogResource(OutpatientCatalogService service) {
        this.service = service;
    }

    @GetMapping("/catalog/specialties")
    public PageResponse<Specialty> specialties(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) Boolean active,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.specialties(q, active, page, size);
    }

    @PostMapping("/catalog/specialties")
    public Specialty createSpecialty(@Valid @RequestBody SpecialtyInput in) {
        return service.createSpecialty(in);
    }

    @PutMapping("/catalog/specialties/{id}")
    public Specialty updateSpecialty(@PathVariable long id, @Valid @RequestBody SpecialtyInput in) {
        return service.updateSpecialty(id, in);
    }

    @GetMapping("/catalog/medications")
    public PageResponse<Medication> medications(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) Boolean active,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.medications(q, active, page, size);
    }

    @PostMapping("/catalog/medications")
    public Medication createMedication(@Valid @RequestBody MedicationInput in) {
        return service.createMedication(in);
    }

    @PutMapping("/catalog/medications/{id}")
    public Medication updateMedication(
        @PathVariable long id,
        @Valid @RequestBody MedicationInput in
    ) {
        return service.updateMedication(id, in);
    }

    @GetMapping("/clinic-settings")
    public Settings settings() {
        return service.settings();
    }

    @PutMapping("/clinic-settings")
    public Settings updateSettings(@Valid @RequestBody Settings in) {
        return service.updateSettings(in);
    }
}
