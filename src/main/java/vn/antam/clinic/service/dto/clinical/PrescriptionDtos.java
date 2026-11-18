package vn.antam.clinic.service.dto.clinical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class PrescriptionDtos {

    private PrescriptionDtos() {}

    public record LineInput(
        @NotNull Long medicationId,
        @NotBlank @Size(max = 100) String dose,
        @NotBlank @Size(max = 100) String route,
        @NotBlank @Size(max = 100) String frequency,
        @Min(1) @Max(365) int days,
        @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") BigDecimal quantity,
        @Size(max = 1000) String instructions
    ) {}

    public record SaveRequest(
        @Min(0) int revision,
        @Min(0) int encounterRevision,
        @Size(max = 500) String reason,
        @NotNull @Size(max = 100) List<@Valid LineInput> lines,
        Boolean remainingQuantityConfirmed,
        @Size(max = 500) String overrideReason
    ) {}

    public record CheckRequest(
        @NotNull @Size(max = 100) List<@NotNull @Min(1) Long> medicationIds
    ) {}

    public record Line(
        long id,
        long medicationId,
        String name,
        String strength,
        String unit,
        String dose,
        String route,
        String frequency,
        int days,
        String quantity,
        String instructions
    ) {}

    public record Prescription(
        long id,
        long encounterId,
        int version,
        int revision,
        String status,
        String reason,
        String authorName,
        String createdAt,
        String finalizedAt,
        List<Line> lines,
        String overrideReason,
        List<vn.antam.clinic.service.clinical.PrescriptionSafetyService.Warning> safetyWarnings
    ) {}
}
