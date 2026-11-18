package vn.antam.clinic.service.clinical;

import java.util.*;

/** Business contract; implemented by the Spring-managed PrescriptionSafetyServiceImpl. */
public interface PrescriptionSafetyService {
    public record Warning(String type, String severity, String message, List<Long> medicationIds) {}

    public List<Warning> check(long patientId, List<Long> medicationIds);
}
