package vn.antam.clinic.service.diagnostic;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;

/** Business contract; implemented by the Spring-managed DiagnosticWorkflowServiceImpl. */
public interface DiagnosticWorkflowService {
    public Object rooms();

    public Object staff(long id);

    public Object configure(long id, Map<String, Object> in, String key);

    public Object definitions(long service);

    public Object configureDefinitions(long service, Map<String, Object> in, String key);

    public Object list(Long encounterId, Long roomId);

    public Object order(long id);

    public Object create(Map<String, Object> in, String key);

    public Object action(long id, String action, Map<String, Object> in, String key);

    public Object save(long id, Map<String, Object> in, String key);

    public Object resultAction(long id, String action, Map<String, Object> in, String key);

    public Object attach(long id, MultipartFile file, int revision);

    public Map<String, Object> attachment(long id);
}
