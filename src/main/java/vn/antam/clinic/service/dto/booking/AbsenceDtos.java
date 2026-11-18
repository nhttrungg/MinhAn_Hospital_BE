package vn.antam.clinic.service.dto.booking;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class AbsenceDtos {

    private AbsenceDtos() {}

    public record AbsenceInput(
        @Min(1) long doctorId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 100) String requestId
    ) {}

    public record Impact(
        long slotId,
        String startsAt,
        String endsAt,
        String slotStatus,
        Long appointmentId,
        String appointmentCode,
        String patientName,
        String appointmentStatus,
        boolean separateHandling
    ) {}

    public record Absence(
        long id,
        long doctorId,
        String doctorName,
        String startsAt,
        String endsAt,
        String reason,
        String actorName,
        String createdAt,
        long pending,
        long rescheduled,
        long cancelled
    ) {}

    public record Coordination(
        long appointmentId,
        long absenceId,
        String appointmentCode,
        String patientName,
        String oldStartsAt,
        String doctorName,
        String contactStatus,
        String contactNote,
        String resolution,
        int revision,
        String agreedAt,
        String agreedBy,
        String resolvedAt,
        String resolvedBy,
        boolean paymentReviewRequired
    ) {}

    public record ContactInput(
        @Min(0) int revision,
        @NotBlank String status,
        @Size(max = 1000) String note
    ) {}

    public record FinalizeInput(
        @Min(0) int revision,
        @Min(1) long slotId,
        boolean patientAgreed,
        @NotBlank @Size(max = 100) String requestId
    ) {}

    public record Candidate(
        long slotId,
        long doctorId,
        String doctorName,
        long roomId,
        String roomName,
        String startsAt,
        String endsAt,
        long serviceId,
        String serviceName,
        double occupancy
    ) {}

    public record AbsenceConfirmed(Absence absence, List<Impact> separateCases) {}
}
