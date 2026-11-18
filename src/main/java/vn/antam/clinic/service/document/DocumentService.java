package vn.antam.clinic.service.document;

import java.time.*;
import java.util.*;

/** Business contract; implemented by the Spring-managed DocumentServiceImpl. */
public interface DocumentService {
    public Map<String, Object> source(String type, long id);

    public Map<String, Object> issue(String type, long id);

    public Map<String, Object> issue(String type, long id, boolean draft);

    public List<Map<String, Object>> history(long id);

    public Map<String, Object> request(long id, String action);

    public List<Map<String, Object>> list(String type, Long source);

    public byte[] pdf(long id);
}
