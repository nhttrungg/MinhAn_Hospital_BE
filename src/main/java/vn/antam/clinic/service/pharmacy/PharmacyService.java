package vn.antam.clinic.service.pharmacy;

import java.math.BigDecimal;
import java.util.*;

/** Business contract; implemented by the Spring-managed PharmacyServiceImpl. */
public interface PharmacyService {
    public List<Map<String, Object>> counters();

    public Map<String, Object> configuration(long id);

    public Map<String, Object> configure(Long id, Map<String, Object> in);

    public List<Map<String, Object>> stock(long counter);

    public Map<String, Object> price(long c, Map<String, Object> in);

    public Map<String, Object> stockChange(long c, String action, Map<String, Object> in);

    public void movement(
        long batch,
        BigDecimal delta,
        BigDecimal reserve,
        String action,
        Long dispensing,
        String reason
    );

    public Map<String, Object> prescription(long id);

    public Map<String, Object> claim(long c, long prescription, Map<String, Object> in);

    public List<Map<String, Object>> orders(long c);

    public List<Map<String, Object>> dispensings(long c);

    public Map<String, Object> dispensing(long id);

    public Map<String, Object> draft(long c, Long id, Map<String, Object> in);

    public Map<String, Object> act(long id, String action, Map<String, Object> in);

    public Map<String, Object> orderAction(
        long c,
        long encounter,
        String action,
        Map<String, Object> in
    );

    public Map<String, Object> trace(long c, long encounter);
}
