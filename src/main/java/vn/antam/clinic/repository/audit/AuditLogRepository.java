package vn.antam.clinic.repository.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.antam.clinic.domain.audit.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
