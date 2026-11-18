package vn.antam.clinic.web.rest.booking;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.booking.PatientService;
import vn.antam.clinic.service.dto.booking.PatientDtos.*;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientResource {

    private final PatientService patients;

    public PatientResource(PatientService patients) {
        this.patients = patients;
    }

    @GetMapping("/me")
    public PatientView me() {
        return patients.me();
    }

    @GetMapping
    public List<PatientView> search(@RequestParam(defaultValue = "") String q) {
        return patients.search(q);
    }

    @GetMapping("/{id}")
    public PatientView get(@PathVariable long id) {
        return patients.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientView create(@Valid @RequestBody PatientInput input) {
        return patients.create(input);
    }

    @PutMapping("/{id}")
    public PatientView update(@PathVariable long id, @Valid @RequestBody PatientInput input) {
        return patients.update(id, input);
    }
}
