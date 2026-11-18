package vn.antam.clinic.service.pharmacy;

import java.util.Map;

/** Business contract; implemented by the Spring-managed OnlinePharmacyServiceImpl. */
public interface OnlinePharmacyService {
    Map<String, Object> encounterOrder(long encounter);

    Map<String, Object> place(long prescription, String key);

    Map<String, Object> cancel(long order, String key);

    void expire();

    Map<String, Object> verify(long dispensing, String key);

    Map<String, Object> finances(long counter);

    Map<String, Object> recordSettlement(long counter, Map<String, Object> input, String key);
}
