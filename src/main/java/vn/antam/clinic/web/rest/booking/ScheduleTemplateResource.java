package vn.antam.clinic.web.rest.booking;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.booking.ScheduleTemplateService;
import vn.antam.clinic.service.dto.booking.BookingDtos.AppointmentView;
import vn.antam.clinic.service.dto.booking.BookingDtos.SlotView;
import vn.antam.clinic.service.dto.booking.ScheduleTemplateDtos.*;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleTemplateResource {

    private final ScheduleTemplateService templates;

    public ScheduleTemplateResource(ScheduleTemplateService templates) {
        this.templates = templates;
    }

    @GetMapping("/templates")
    public List<TemplateView> templates() {
        return templates.list();
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateView create(@Valid @RequestBody CreateTemplateRequest request) {
        return templates.create(request);
    }

    @PostMapping("/templates/{id}/generate")
    public List<SlotView> generate(
        @PathVariable long id,
        @Valid @RequestBody GenerateRequest request
    ) {
        return templates.generate(id, request);
    }

    @PutMapping("/templates/{id}")
    public TemplateView update(
        @PathVariable long id,
        @Valid @RequestBody CreateTemplateRequest in
    ) {
        return templates.update(id, in);
    }

    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable long id) {
        templates.disable(id);
    }

    @PostMapping("/slots/{id}/copy")
    public List<SlotView> copy(@PathVariable long id, @Valid @RequestBody CopyRequest request) {
        return templates.copy(id, request);
    }

    @GetMapping("/slots/{id}/affected-appointments")
    public List<AppointmentView> affected(@PathVariable long id) {
        return templates.affected(id);
    }
}
