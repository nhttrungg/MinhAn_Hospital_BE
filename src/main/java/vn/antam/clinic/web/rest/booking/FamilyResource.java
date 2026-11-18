package vn.antam.clinic.web.rest.booking;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.service.booking.PatientService;
import vn.antam.clinic.service.dto.booking.FamilyDtos.*;

@RestController
@RequestMapping("/api/v1/patients/me/family")
public class FamilyResource {

    private final PatientService patients;

    public FamilyResource(PatientService patients) {
        this.patients = patients;
    }

    @GetMapping
    public List<FamilyView> list() {
        return patients.family();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyView create(@Valid @RequestBody FamilyInput input) {
        return patients.addFamily(input);
    }

    @PutMapping("/{id}")
    public FamilyView update(@PathVariable long id, @Valid @RequestBody FamilyInput input) {
        return patients.updateFamily(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        patients.removeFamily(id);
    }
}
