package vn.antam.clinic.service.diagnostic;

import java.util.*;
import org.springframework.web.multipart.MultipartFile;

/** Business contract; implemented by the Spring-managed DiagnosticServiceImpl. */
public interface DiagnosticService {
    public List<Map<String, Object>> rooms();

    public Object config(long room);

    public Map<String, Object> configure(long room, Map<String, Object> in);

    public Object definitions(long service);

    public Map<String, Object> definitions(long service, Map<String, Object> in);

    public Map<String, Object> order(long id);

    public Object list(Long encounter, Long room);

    public Map<String, Object> create(Map<String, Object> in);

    public Map<String, Object> action(long id, String action, Map<String, Object> in);

    public Map<String, Object> saveResult(long id, Map<String, Object> in);

    public Map<String, Object> resultAction(long result, String action, Map<String, Object> in);

    public Map<String, Object> attach(long result, MultipartFile file, int revision);

    public Map<String, Object> attachment(long id);
}
