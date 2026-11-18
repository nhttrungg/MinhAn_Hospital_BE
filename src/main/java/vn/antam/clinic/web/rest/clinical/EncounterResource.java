package vn.antam.clinic.web.rest.clinical;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.clinical.EncounterService;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/encounters")
public class EncounterResource {

    private final EncounterService encounters;

    public EncounterResource(EncounterService encounters) {
        this.encounters = encounters;
    }

    @GetMapping("/nurses")
    public List<NurseReference> nurses() {
        return encounters.nurses();
    }

    @GetMapping("/me")
    public PageResponse<EncounterView> myRecords(
        @RequestParam(required = false) java.time.LocalDate from,
        @RequestParam(required = false) java.time.LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return encounters.myRecords(from, to, page, size);
    }

    @GetMapping("/assigned")
    public PageResponse<EncounterView> assigned(
        @RequestParam(required = false) java.time.LocalDate from,
        @RequestParam(required = false) java.time.LocalDate to,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return encounters.assigned(from, to, status, page, size);
    }

    @GetMapping("/appointments/{appointmentId}")
    public EncounterView forAppointment(@PathVariable long appointmentId) {
        return encounters.forAppointment(appointmentId);
    }

    @GetMapping("/{id}")
    public EncounterView get(@PathVariable long id) {
        return encounters.get(id);
    }

    @PostMapping("/appointments/{appointmentId}/start")
    public EncounterView start(@PathVariable long appointmentId) {
        return encounters.start(appointmentId);
    }

    @PutMapping("/{id}/draft")
    public EncounterView save(@PathVariable long id, @Valid @RequestBody DraftRequest request) {
        return encounters.save(id, request);
    }

    @PostMapping("/{id}/wait-results")
    public EncounterView waitResults(
        @PathVariable long id,
        @Valid @RequestBody RevisionRequest request
    ) {
        return encounters.waitResults(id, request.revision());
    }

    @PostMapping("/{id}/complete")
    public EncounterView complete(
        @PathVariable long id,
        @Valid @RequestBody RevisionRequest request
    ) {
        return encounters.complete(id, request.revision());
    }

    @PostMapping("/{id}/assign-nurse")
    public EncounterView assignNurse(
        @PathVariable long id,
        @Valid @RequestBody AssignNurseRequest request
    ) {
        return encounters.assignNurse(id, request.nurseStaffId());
    }

    @PutMapping("/{id}/vitals")
    public EncounterView vitals(@PathVariable long id, @Valid @RequestBody VitalsRequest request) {
        return encounters.vitals(id, request);
    }

    @PostMapping("/{id}/amendments")
    public EncounterView amend(
        @PathVariable long id,
        @Valid @RequestBody AmendmentRequest request
    ) {
        return encounters.amend(id, request);
    }
}
