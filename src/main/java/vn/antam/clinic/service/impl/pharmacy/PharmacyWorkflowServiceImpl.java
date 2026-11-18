package vn.antam.clinic.service.impl.pharmacy;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.util.*;
import vn.antam.clinic.service.pharmacy.PharmacyAlertService;
import vn.antam.clinic.service.pharmacy.PharmacyService;
import vn.antam.clinic.service.pharmacy.PharmacyWorkflowService;
import vn.antam.clinic.service.support.WorkflowSupport;

@org.springframework.stereotype.Service
public class PharmacyWorkflowServiceImpl implements PharmacyWorkflowService {

    private final PharmacyService s;
    private final WorkflowSupport w;
    private final vn.antam.clinic.service.pharmacy.PharmacyAlertService alerts;

    public PharmacyWorkflowServiceImpl(
        PharmacyService s,
        WorkflowSupport w,
        vn.antam.clinic.service.pharmacy.PharmacyAlertService alerts
    ) {
        this.s = s;
        this.w = w;
        this.alerts = alerts;
    }

    @Override
    public vn.antam.clinic.service.pharmacy.PharmacyAlertService.Alerts alerts(Long counterId) {
        return alerts.alerts(counterId);
    }

    @Override
    public Object medications() {
        require("ADMIN", "PHARMACIST");
        return w
            .db()
            .queryForList(
                "select id,code,name,ingredient,strength,form,unit from medications where active=1 order by name,id"
            );
    }

    @Override
    public Object destinations() {
        require("ADMIN", "PHARMACIST");
        return w
            .db()
            .queryForList("select id,name from pharmacy_counters where active=1 order by name");
    }

    @Override
    public Object counters() {
        return s.counters();
    }

    @Override
    public Object configuration(long id) {
        return s.configuration(id);
    }

    @Override
    public Object create(Map<String, Object> in, String key) {
        require("ADMIN");
        return w.once("COUNTER_CREATE", key, in, () -> s.configure(null, in));
    }

    @Override
    public Object edit(long id, Map<String, Object> in, String key) {
        require("ADMIN");
        return w.once("COUNTER_EDIT:" + id, key, in, () -> s.configure(id, in));
    }

    @Override
    public Object stock(long id) {
        return s.stock(id);
    }

    @Override
    public Object price(long id, Map<String, Object> in, String key) {
        require("ADMIN");
        return w.once("PRICE:" + id, key, in, () -> s.price(id, in));
    }

    @Override
    public Object change(long id, String action, Map<String, Object> in, String key) {
        w.counterRead(id);
        require("ADMIN", "PHARMACIST");
        return w.once("STOCK:" + id + ":" + action, key, in, () -> s.stockChange(id, action, in));
    }

    @Override
    public Object prescription(long id) {
        return s.prescription(id);
    }

    @Override
    public Object fulfillment(long id) {
        w.encounter(id, false);
        return Map.of(
            "lines",
            w
                .db()
                .queryForList(
                    "select p.version,pl.name_snapshot name,pl.strength_snapshot strength,pl.unit_snapshot unit,sum(dl.quantity) delivered from dispensing_lines dl join dispensings d on d.id=dl.dispensing_id join prescription_lines pl on pl.id=dl.prescription_line_id join prescriptions p on p.id=pl.prescription_id where d.encounter_id=? and d.status='DELIVERED' group by p.version,pl.id order by p.version,pl.id",
                    id
                ),
            "pending",
            w
                .db()
                .queryForList(
                    "select id,status,counter_id from dispensings where encounter_id=? and status in ('DRAFT','READY')",
                    id
                )
        );
    }

    @Override
    public Object claim(long id, long prescription, Map<String, Object> in, String key) {
        w.counter(id, "PHARMACIST");
        return w.once("CLAIM:" + id + ":" + prescription, key, in, () ->
            s.claim(id, prescription, in)
        );
    }

    @Override
    public Object orders(long id) {
        return s.orders(id);
    }

    @Override
    public Object dispensings(long id) {
        return s.dispensings(id);
    }

    @Override
    public Object dispensing(long id) {
        return s.dispensing(id);
    }

    @Override
    public Object draft(long id, Map<String, Object> in, String key) {
        w.counter(id, "PHARMACIST");
        return w.once("DRAFT:" + id, key, in, () -> s.draft(id, null, in));
    }

    @Override
    public Object editDraft(long id, Map<String, Object> in, String key) {
        var d = s.dispensing(id);
        long c = WorkflowSupport.number(d, "counter_id");
        w.counter(c, "PHARMACIST");
        return w.once("DRAFT_EDIT:" + id, key, in, () -> s.draft(c, id, in));
    }

    @Override
    @vn.antam.clinic.service.support.RetryOnLockConflict
    public Object action(long id, String action, Map<String, Object> in, String key) {
        var d = s.dispensing(id);
        w.counter(WorkflowSupport.number(d, "counter_id"), "PHARMACIST");
        return w.once("DISPENSE:" + id + ":" + action, key, in, () -> s.act(id, action, in));
    }

    @Override
    public Object orderAction(
        long id,
        long encounter,
        String action,
        Map<String, Object> in,
        String key
    ) {
        w.counter(id, "PHARMACIST");
        return w.once("ORDER:" + encounter + ":" + action, key, in, () ->
            s.orderAction(id, encounter, action, in)
        );
    }

    @Override
    public Object trace(long id, long encounter) {
        return s.trace(id, encounter);
    }

    @Override
    public Object movements(long id) {
        w.counterRead(id);
        return w
            .db()
            .queryForList(
                "select sm.*,b.batch_code,m.name from stock_movements sm join medication_batches b on b.id=sm.batch_id join medications m on m.id=b.medication_id where b.counter_id=? order by sm.id desc limit 200",
                id
            );
    }

    @Override
    public Object report(long id) {
        w.counterRead(id);
        return Map.of(
            "stock",
            s.stock(id),
            "orders",
            s.orders(id),
            "invoices",
            w
                .db()
                .queryForList(
                    "select i.id,i.invoice_code,i.status,coalesce((select sum(amount) from receipts where invoice_id=i.id),0) collected,coalesce((select sum(amount) from refunds where invoice_id=i.id),0) refunded from invoices i where counter_id=? order by id desc limit 200",
                    id
                )
        );
    }
}
