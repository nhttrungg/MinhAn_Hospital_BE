package vn.antam.clinic.web.rest.document;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.document.DocumentService;
import vn.antam.clinic.service.support.WorkflowSupport;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentResource {

    private final DocumentService s;

    public DocumentResource(DocumentService s) {
        this.s = s;
    }

    @PostMapping
    public Object issue(@RequestBody Map<String, Object> in) {
        return s.issue(
            WorkflowSupport.required(in, "type", 40),
            WorkflowSupport.number(in, "sourceId"),
            WorkflowSupport.yes(in, "draft")
        );
    }

    @GetMapping
    public Object list(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Long sourceId
    ) {
        return s.list(type, sourceId);
    }

    @GetMapping("/{id}/history")
    public Object history(@PathVariable long id) {
        return s.history(id);
    }

    @PostMapping("/{id}/requests")
    public Object request(@PathVariable long id, @RequestBody Map<String, Object> in) {
        return s.request(id, WorkflowSupport.required(in, "action", 30));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable long id) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "inline; filename=antam-document-" + id + ".pdf")
            .header("Cache-Control", "no-store")
            .body(s.pdf(id));
    }
}
