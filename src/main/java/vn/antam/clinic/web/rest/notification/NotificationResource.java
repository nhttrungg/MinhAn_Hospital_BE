package vn.antam.clinic.web.rest.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.notification.AppointmentNotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationResource {

    private final AppointmentNotificationService notices;

    public NotificationResource(AppointmentNotificationService notices) {
        this.notices = notices;
    }

    @GetMapping
    public PageResponse<AppointmentNotificationService.Notice> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return notices.list(page, size);
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable long id) {
        notices.read(id);
    }
}
