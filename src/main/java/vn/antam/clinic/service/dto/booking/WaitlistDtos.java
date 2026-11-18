package vn.antam.clinic.service.dto.booking;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

public final class WaitlistDtos {

    private WaitlistDtos() {}

    public record JoinRequest(
        Long patientId,
        Long serviceId,
        Long specialtyId,
        Long doctorId,
        @NotNull LocalDate dateFrom,
        @NotNull LocalDate dateTo
    ) {}

    public record Entry(
        long id,
        long patientId,
        String patientName,
        Long serviceId,
        String serviceName,
        Long specialtyId,
        String specialtyName,
        Long doctorId,
        String doctorName,
        LocalDate dateFrom,
        LocalDate dateTo,
        String status,
        Instant notifiedAt,
        Long notifiedSlotId,
        Instant createdAt
    ) {}
}
