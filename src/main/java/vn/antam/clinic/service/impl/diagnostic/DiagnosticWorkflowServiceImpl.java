package vn.antam.clinic.service.impl.diagnostic;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import vn.antam.clinic.service.diagnostic.DiagnosticService;
import vn.antam.clinic.service.diagnostic.DiagnosticWorkflowService;
import vn.antam.clinic.service.support.WorkflowSupport;

@org.springframework.stereotype.Service
public class DiagnosticWorkflowServiceImpl implements DiagnosticWorkflowService {

    private final DiagnosticService s;
    private final WorkflowSupport w;

    public DiagnosticWorkflowServiceImpl(DiagnosticService s, WorkflowSupport w) {
        this.s = s;
        this.w = w;
    }

    @Override
    public Object rooms() {
        return s.rooms();
    }

    @Override
    public Object staff(long id) {
        return s.config(id);
    }

    @Override
    public Object configure(long id, Map<String, Object> in, String key) {
        require("ADMIN");
        return w.once("LAB_STAFF:" + id, key, in, () -> s.configure(id, in));
    }

    @Override
    public Object definitions(long service) {
        return s.definitions(service);
    }

    @Override
    public Object configureDefinitions(long service, Map<String, Object> in, String key) {
        require("ADMIN");
        return w.once("LAB_DEFS:" + service, key, in, () -> s.definitions(service, in));
    }

    @Override
    public Object list(Long encounterId, Long roomId) {
        return s.list(encounterId, roomId);
    }

    @Override
    public Object order(long id) {
        return s.order(id);
    }

    @Override
    public Object create(Map<String, Object> in, String key) {
        w.encounter(WorkflowSupport.number(in, "encounterId"), true);
        return w.once("LAB_ISSUE", key, in, () -> s.create(in));
    }

    @Override
    public Object action(long id, String action, Map<String, Object> in, String key) {
        var o = s.order(id);
        if (action.equals("cancel")) w.encounter(WorkflowSupport.number(o, "encounter_id"), true);
        else w.diagnostic(WorkflowSupport.number(o, "room_id"), false);
        return w.once("LAB_ORDER:" + id + ":" + action, key, in, () -> s.action(id, action, in));
    }

    @Override
    public Object save(long id, Map<String, Object> in, String key) {
        var o = s.order(id);
        w.diagnostic(WorkflowSupport.number(o, "room_id"), false);
        return w.once("LAB_RESULT:" + id, key, in, () -> s.saveResult(id, in));
    }

    @Override
    public Object resultAction(long id, String action, Map<String, Object> in, String key) {
        var r = w.row(
            "select o.room_id from diagnostic_results r join diagnostic_orders o on o.id=r.order_id where r.id=?",
            id
        );
        w.diagnostic(WorkflowSupport.number(r, "room_id"), !action.equals("submit"));
        return w.once("LAB_RESULT:" + id + ":" + action, key, in, () ->
            s.resultAction(id, action, in)
        );
    }

    @Override
    public Object attach(long id, MultipartFile file, int revision) {
        return s.attach(id, file, revision);
    }

    @Override
    public Map<String, Object> attachment(long id) {
        return s.attachment(id);
    }
}
