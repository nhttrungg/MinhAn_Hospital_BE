package vn.antam.clinic.service.catalog;

import vn.antam.clinic.service.dto.catalog.OutpatientCatalogDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed OutpatientCatalogServiceImpl. */
public interface OutpatientCatalogService {
    public PageResponse<Specialty> specialties(String q, Boolean active, int page, int size);

    public Specialty createSpecialty(SpecialtyInput in);

    public Specialty updateSpecialty(long id, SpecialtyInput in);

    public PageResponse<Medication> medications(String q, Boolean active, int page, int size);

    public Medication createMedication(MedicationInput in);

    public Medication updateMedication(long id, MedicationInput in);

    public Settings settings();

    public Settings updateSettings(Settings in);
}
