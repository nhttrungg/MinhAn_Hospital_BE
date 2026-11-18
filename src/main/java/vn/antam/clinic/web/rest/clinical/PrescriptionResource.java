package vn.antam.clinic.web.rest.clinical;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.clinical.PrescriptionService;
import vn.antam.clinic.service.dto.clinical.PrescriptionDtos;

@RestController
@RequestMapping("/api/v1/prescriptions/encounters/{id}")
public class PrescriptionResource {

    private final PrescriptionService service;

    public PrescriptionResource(PrescriptionService service) {
        this.service = service;
    }

    @GetMapping
    public List<PrescriptionDtos.Prescription> list(@PathVariable long id) {
        return service.list(id);
    }

    @PutMapping("/draft")
    public PrescriptionDtos.Prescription save(
        @PathVariable long id,
        @Valid @RequestBody PrescriptionDtos.SaveRequest in
    ) {
        return service.save(id, in, false);
    }

    @PostMapping("/check")
    public List<vn.antam.clinic.service.clinical.PrescriptionSafetyService.Warning> check(
        @PathVariable long id,
        @Valid @RequestBody PrescriptionDtos.CheckRequest in
    ) {
        return service.check(id, in);
    }

    @PostMapping("/versions")
    public PrescriptionDtos.Prescription amend(
        @PathVariable long id,
        @Valid @RequestBody PrescriptionDtos.SaveRequest in
    ) {
        return service.save(id, in, true);
    }
}
