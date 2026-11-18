package vn.antam.clinic.service.pharmacy;

import java.util.*;

/** Business contract; implemented by the Spring-managed PharmacyWorkflowServiceImpl. */
public interface PharmacyWorkflowService {
    public vn.antam.clinic.service.pharmacy.PharmacyAlertService.Alerts alerts(Long counterId);

    public Object medications();

    public Object destinations();

    public Object counters();

    public Object configuration(long id);

    public Object create(Map<String, Object> in, String key);

    public Object edit(long id, Map<String, Object> in, String key);

    public Object stock(long id);

    public Object price(long id, Map<String, Object> in, String key);

    public Object change(long id, String action, Map<String, Object> in, String key);

    public Object prescription(long id);

    public Object fulfillment(long id);

    public Object claim(long id, long prescription, Map<String, Object> in, String key);

    public Object orders(long id);

    public Object dispensings(long id);

    public Object dispensing(long id);

    public Object draft(long id, Map<String, Object> in, String key);

    public Object editDraft(long id, Map<String, Object> in, String key);

    public Object action(long id, String action, Map<String, Object> in, String key);

    public Object orderAction(
        long id,
        long encounter,
        String action,
        Map<String, Object> in,
        String key
    );

    public Object trace(long id, long encounter);

    public Object movements(long id);

    public Object report(long id);
}
