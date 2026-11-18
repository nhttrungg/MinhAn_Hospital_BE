package vn.antam.clinic.service.clinical;

import java.util.*;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.*;

/** Business contract; implemented by the Spring-managed Icd10ServiceImpl. */
public interface Icd10Service {
    public record Code(
        String code,
        String nameVi,
        String nameEn,
        String sourceSystem,
        boolean demo
    ) {}

    public List<Code> search(String query);

    public List<DiagnosisView> diagnoses(long encounter);

    public void save(long encounter, List<DiagnosisInput> diagnoses);
}
