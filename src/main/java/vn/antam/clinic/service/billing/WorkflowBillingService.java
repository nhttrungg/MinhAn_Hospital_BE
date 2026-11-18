package vn.antam.clinic.service.billing;

import java.util.*;

/** Business contract; implemented by the Spring-managed WorkflowBillingServiceImpl. */
public interface WorkflowBillingService {
    public Map<String, Object> details(long id);

    public Map<String, Object> paymentQr(long id);

    public Object refund(long id, Map<String, Object> in, String requestKey);
}
