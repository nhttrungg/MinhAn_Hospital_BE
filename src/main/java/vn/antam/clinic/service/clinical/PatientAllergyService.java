package vn.antam.clinic.service.clinical;

import jakarta.validation.constraints.*;
import java.util.List;

/** Business contract; implemented by the Spring-managed PatientAllergyServiceImpl. */
public interface PatientAllergyService {
    public record Allergy(
        long id,
        long patientId,
        String ingredient,
        String reaction,
        String severity,
        int revision
    ) {}

    public record Input(
        @NotBlank @Size(max = 200) String ingredient,
        @Size(max = 300) String reaction,
        @NotBlank @Pattern(regexp = "MILD|MODERATE|MAJOR") String severity,
        @Min(0) int revision
    ) {}

    public List<Allergy> list(long patientId);

    public Allergy save(long patientId, Long id, Input in);

    public void delete(long patientId, long id, int revision);
}
