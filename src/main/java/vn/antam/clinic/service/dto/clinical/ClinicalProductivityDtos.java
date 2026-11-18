package vn.antam.clinic.service.dto.clinical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class ClinicalProductivityDtos {

    private ClinicalProductivityDtos() {}

    public record TemplateInput(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Min(1) Long specialtyId,
        boolean shared,
        @Min(0) int version,
        @Size(max = 1000) String chiefComplaint,
        @Size(max = 10000) String diagnosis,
        @Size(max = 10000) String conclusion,
        @Size(max = 10000) String instructions
    ) {}

    public record Template(
        long id,
        Long ownerStaffId,
        long specialtyId,
        String name,
        String state,
        int version,
        String chiefComplaint,
        String diagnosis,
        String conclusion,
        String instructions,
        String authorName,
        String updatedAt
    ) {}

    public record VersionRequest(@Min(1) int version) {}

    public record Application(@Min(1) long templateId, @Min(1) int version) {}

    public record ChartSync(
        @NotBlank @Size(max = 100) String requestId,
        @NotNull @Valid EncounterDtos.DraftRequest draft,
        @Valid Application template
    ) {}

    public record RxSync(
        @NotBlank @Size(max = 100) String requestId,
        @NotNull @Valid PrescriptionDtos.SaveRequest draft
    ) {}

    public record ChartSaved(EncounterDtos.EncounterView data, int revision, String savedAt) {}

    public record RxSaved(PrescriptionDtos.Prescription data, int revision, String savedAt) {}

    public record Source(String content, String recordedAt, String href) {}

    public record PriorMedication(
        String name,
        String strength,
        String dose,
        String frequency,
        String quantity,
        String prescribedAt,
        String href
    ) {}

    public record PatientSummary(
        long encounterId,
        Integer age,
        Source medicalHistory,
        Source allergies,
        Source vitals,
        Source lastCompletedVisit,
        Source diagnosis,
        List<PriorMedication> prescribedMedications,
        List<Source> allergyDetails
    ) {}

    public record DiagnosticPoint(
        long resultId,
        long orderId,
        long encounterId,
        long serviceId,
        String serviceName,
        Long definitionId,
        String name,
        String value,
        BigDecimal numericValue,
        String unit,
        String referenceRange,
        String publishedAt,
        String href,
        boolean comparable
    ) {}

    public record DiagnosticHistory(List<DiagnosticPoint> points, int ambiguousMeasurements) {}

    public record StockRequest(
        @NotNull @Size(max = 100) List<@NotNull @Min(1) Long> medicationIds,
        @Min(1) Long counterId
    ) {}

    public record CounterStock(long counterId, String counterName, BigDecimal available) {}

    public record MedicationStock(
        long medicationId,
        String unit,
        BigDecimal available,
        List<CounterStock> counters
    ) {}

    public record Availability(String checkedAt, List<MedicationStock> medications) {}
}
