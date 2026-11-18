package vn.antam.clinic.service.audit;

/** Business contract; implemented by the Spring-managed AuditServiceImpl. */
public interface AuditService {
    public void record(String action, String entityType, String entityId, String detail);
}
