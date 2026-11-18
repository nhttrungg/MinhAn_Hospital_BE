package vn.antam.clinic.web.rest.identity;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.dto.identity.EmailTaskDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.identity.EmailTaskService;

@RestController
@RequestMapping("/api/v1/email-tasks")
public class EmailTaskResource {

    private final EmailTaskService service;

    public EmailTaskResource(EmailTaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<EmailTaskView> list() {
        return service.list();
    }

    @GetMapping("/search")
    public PageResponse<EmailTaskView> search(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.search(q, status, page, size);
    }

    @PostMapping("/{id}/retry")
    public EmailTaskView retry(@PathVariable long id) {
        return service.retry(id);
    }
}
