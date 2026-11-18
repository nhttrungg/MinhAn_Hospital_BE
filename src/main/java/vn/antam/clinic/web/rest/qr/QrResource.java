package vn.antam.clinic.web.rest.qr;

import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.qr.QrService;
import vn.antam.clinic.service.support.WorkflowSupport;

@RestController
public class QrResource {

    private final QrService s;

    public QrResource(QrService s) {
        this.s = s;
    }

    @PostMapping("/api/v1/qr/resolve")
    public Object resolve(@RequestBody Map<String, Object> in) {
        return s.resolve(WorkflowSupport.required(in, "payload", 200));
    }

    @PostMapping("/api/v1/appointments/{id}/qr")
    public Object appointment(@PathVariable long id) {
        return s.issue("APPOINTMENT", id);
    }

    @PostMapping("/api/v1/qr/{kind}/{id}")
    public Object issue(@PathVariable String kind, @PathVariable long id) {
        return s.issue(kind, id);
    }

    @PostMapping("/api/v1/qr/{kind}/{id}/revoke")
    public Object revoke(@PathVariable String kind, @PathVariable long id) {
        return s.revoke(kind, id);
    }
}
