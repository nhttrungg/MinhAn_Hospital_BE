package vn.antam.clinic.web.rest.diagnostic;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.antam.clinic.service.support.WorkflowSupport;

@RestController
@RequestMapping("/api/v1/diagnostics")
public class DiagnosticResource {

    private final vn.antam.clinic.service.diagnostic.DiagnosticWorkflowService workflow;

    public DiagnosticResource(
        vn.antam.clinic.service.diagnostic.DiagnosticWorkflowService workflow
    ) {
        this.workflow = workflow;
    }

    @GetMapping("/rooms")
    public Object rooms() {
        return workflow.rooms();
    }

    @GetMapping("/rooms/{id}/staff")
    public Object staff(@PathVariable long id) {
        return workflow.staff(id);
    }

    @PutMapping("/rooms/{id}/staff")
    public Object configure(
        @PathVariable long id,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.configure(id, in, key);
    }

    @GetMapping("/definitions/{service}")
    public Object definitions(@PathVariable long service) {
        return workflow.definitions(service);
    }

    @PutMapping("/definitions/{service}")
    public Object configureDefinitions(
        @PathVariable long service,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.configureDefinitions(service, in, key);
    }

    @GetMapping("/orders")
    public Object list(
        @RequestParam(required = false) Long encounterId,
        @RequestParam(required = false) Long roomId
    ) {
        return workflow.list(encounterId, roomId);
    }

    @GetMapping("/orders/{id}")
    public Object order(@PathVariable long id) {
        return workflow.order(id);
    }

    @PostMapping("/orders")
    public Object create(
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.create(in, key);
    }

    @PostMapping("/orders/{id}/{action}")
    public Object action(
        @PathVariable long id,
        @PathVariable String action,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.action(id, action, in, key);
    }

    @PutMapping("/orders/{id}/result")
    public Object save(
        @PathVariable long id,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.save(id, in, key);
    }

    @PostMapping("/results/{id}/{action}")
    public Object resultAction(
        @PathVariable long id,
        @PathVariable String action,
        @RequestBody Map<String, Object> in,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.resultAction(id, action, in, key);
    }

    @PostMapping("/results/{id}/attachments")
    public Object attach(
        @PathVariable long id,
        @RequestParam MultipartFile file,
        @RequestParam int revision
    ) {
        return workflow.attach(id, file, revision);
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<byte[]> attachment(@PathVariable long id) {
        var a = workflow.attachment(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(WorkflowSupport.text(a, "content_type")))
            .header(
                "Content-Disposition",
                ContentDisposition.attachment()
                    .filename(
                        WorkflowSupport.text(a, "filename"),
                        java.nio.charset.StandardCharsets.UTF_8
                    )
                    .build()
                    .toString()
            )
            .header("Cache-Control", "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body((byte[]) a.get("content"));
    }
}
