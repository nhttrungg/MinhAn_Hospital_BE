package vn.antam.clinic.service.impl.audit;

import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.domain.audit.AuditLog;
import vn.antam.clinic.repository.audit.AuditLogRepository;
import vn.antam.clinic.security.ClinicUserPrincipal;
import vn.antam.clinic.service.audit.AuditService;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogs;

    public AuditServiceImpl(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    @Transactional
    public void record(String action, String entityType, String entityId, String detail) {
        AuditLog entry = new AuditLog();
        entry.setActorUserId(currentUserId());
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetail(truncate(detail));
        entry.setRequestId(MDC.get("requestId"));
        entry.setCreatedAt(Instant.now());
        auditLogs.save(entry);
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
            authentication != null &&
            authentication.getPrincipal() instanceof ClinicUserPrincipal principal
        ) {
            return principal.getId();
        }
        return null;
    }

    private static String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 1000 ? detail : detail.substring(0, 1000);
    }
}
