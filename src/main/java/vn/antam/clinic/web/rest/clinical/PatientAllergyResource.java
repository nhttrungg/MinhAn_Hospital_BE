package vn.antam.clinic.web.rest.clinical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.service.clinical.PatientAllergyService;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/allergies")
public class PatientAllergyResource {

    private final PatientAllergyService service;

    public PatientAllergyResource(PatientAllergyService service) {
        this.service = service;
    }

    @GetMapping
    public List<PatientAllergyService.Allergy> list(@PathVariable long patientId) {
        return service.list(patientId);
    }

    @PostMapping
    public PatientAllergyService.Allergy create(
        @PathVariable long patientId,
        @Valid @RequestBody PatientAllergyService.Input in
    ) {
        return service.save(patientId, null, in);
    }

    @PutMapping("/{id}")
    public PatientAllergyService.Allergy update(
        @PathVariable long patientId,
        @PathVariable long id,
        @Valid @RequestBody PatientAllergyService.Input in
    ) {
        return service.save(patientId, id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable long patientId,
        @PathVariable long id,
        @RequestParam @Min(0) int revision
    ) {
        service.delete(patientId, id, revision);
    }
}
