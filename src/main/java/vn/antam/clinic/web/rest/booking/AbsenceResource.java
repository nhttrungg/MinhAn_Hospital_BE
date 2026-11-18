package vn.antam.clinic.web.rest.booking;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.service.booking.AbsenceService;
import vn.antam.clinic.service.dto.booking.AbsenceDtos.*;
import vn.antam.clinic.service.dto.booking.BookingDtos.AppointmentView;

@RestController
@RequestMapping("/api/v1/schedules/absences")
public class AbsenceResource {

    private final AbsenceService service;

    public AbsenceResource(AbsenceService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public List<Impact> preview(@Valid @RequestBody AbsenceInput in) {
        return service.preview(in);
    }

    @PostMapping
    public AbsenceConfirmed confirm(@Valid @RequestBody AbsenceInput in) {
        return service.confirm(in);
    }

    @GetMapping
    public List<Absence> list() {
        return service.absences();
    }

    @GetMapping("/coordination")
    public List<Coordination> coordination(@RequestParam(required = false) Long absenceId) {
        return service.coordination(absenceId);
    }

    @PutMapping("/coordination/{id}/contact")
    public Coordination contact(@PathVariable long id, @Valid @RequestBody ContactInput in) {
        return service.contact(id, in);
    }

    @GetMapping("/coordination/{id}/candidates")
    public List<Candidate> candidates(
        @PathVariable long id,
        @RequestParam(defaultValue = "7") int days
    ) {
        return service.candidates(id, days);
    }

    @PostMapping("/coordination/{id}/finalize")
    public AppointmentView finalizeReplacement(
        @PathVariable long id,
        @Valid @RequestBody FinalizeInput in
    ) {
        return service.finalizeReplacement(id, in);
    }
}
