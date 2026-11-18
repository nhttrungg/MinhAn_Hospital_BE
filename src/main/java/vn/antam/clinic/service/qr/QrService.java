package vn.antam.clinic.service.qr;

import com.google.zxing.*;
import java.util.*;

/** Business contract; implemented by the Spring-managed QrServiceImpl. */
public interface QrService {
    public Object access(String kind, long id);

    public Map<String, Object> issue(String kind, long id);

    public Map<String, Object> resolve(String payload);

    public Map<String, Object> revoke(String kind, long id);
}
