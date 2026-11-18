package vn.antam.clinic.service.dto.clinical;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ReviewDtos {

    private ReviewDtos() {}

    public record ReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 1000) String comment
    ) {}

    public enum ModerationReason {
        SPAM,
        OFFENSIVE,
        PERSONAL_INFORMATION,
        UNRELATED,
        FRAUDULENT,
        RESTORE,
    }

    public record VisibilityRequest(
        @NotNull Boolean visible,
        @NotNull ModerationReason reasonCode,
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record ReviewView(
        long id,
        Long encounterId,
        Long patientId,
        long doctorId,
        int rating,
        String comment,
        String patientName,
        boolean visible,
        Instant createdAt
    ) {}
}
