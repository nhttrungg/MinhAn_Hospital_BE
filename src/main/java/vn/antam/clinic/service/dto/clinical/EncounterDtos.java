package vn.antam.clinic.service.dto.clinical;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class EncounterDtos {

    private EncounterDtos() {}

    public record DiagnosisInput(
        @NotBlank @jakarta.validation.constraints.Size(max = 10) String code,
        boolean primary
    ) {}

    public record DiagnosisView(
        String code,
        String nameVi,
        String nameEn,
        boolean primary,
        String sourceSystem,
        boolean demo
    ) {}

    public record AmendmentView(
        long id,
        String reason,
        String content,
        String createdAt,
        String authorName
    ) {}

    public record EncounterView(
        long id,
        long appointmentId,
        long patientId,
        String patientName,
        long doctorId,
        String status,
        int revision,
        String chiefComplaint,
        @jakarta.validation.constraints.Size(max = 10000) String medicalHistory,
        @jakarta.validation.constraints.Size(max = 10000) String allergies,
        @jakarta.validation.constraints.Size(max = 1000) String vitals,
        String diagnosis,
        String conclusion,
        String patientMedicalHistory,
        String patientAllergies,
        String startedAt,
        String completedAt,
        List<AmendmentView> amendments,
        String instructions,
        java.time.LocalDate followUpDate,
        String doctorName,
        String appointmentCode,
        Long nurseStaffId,
        String nurseName,
        List<DiagnosisView> diagnoses
    ) {}

    public record NurseReference(long id, String fullName, String departmentName) {}

    public record DraftRequest(
        @NotNull @jakarta.validation.constraints.Min(0) Integer revision,
        @jakarta.validation.constraints.Size(max = 1000) String chiefComplaint,
        @jakarta.validation.constraints.Size(max = 10000) String medicalHistory,
        @jakarta.validation.constraints.Size(max = 10000) String allergies,
        @jakarta.validation.constraints.Size(max = 1000) String vitals,
        @jakarta.validation.constraints.Size(max = 10000) String diagnosis,
        @jakarta.validation.constraints.Size(max = 10000) String conclusion,
        @jakarta.validation.constraints.Size(max = 10000) String instructions,
        java.time.LocalDate followUpDate,
        @jakarta.validation.constraints.Size(max = 20)
        List<@jakarta.validation.Valid DiagnosisInput> diagnoses
    ) {}

    public record RevisionRequest(@NotNull Integer revision) {}

    public record AssignNurseRequest(@NotNull Long nurseStaffId) {}

    public record VitalsRequest(@NotNull Integer revision, @NotBlank String vitals) {}

    public record AmendmentRequest(
        @NotBlank @jakarta.validation.constraints.Size(max = 500) String reason,
        @NotBlank @jakarta.validation.constraints.Size(max = 10000) String content
    ) {}
}
