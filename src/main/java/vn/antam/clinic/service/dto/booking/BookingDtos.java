package vn.antam.clinic.service.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class BookingDtos {

    private BookingDtos() {}

    public record SpecialtyView(long id, String code, String name, String description) {}

    public record DoctorView(
        long id,
        String fullName,
        String title,
        long specialtyId,
        String specialtyName,
        String bio,
        long serviceId,
        String serviceName,
        String price,
        String serviceDescription,
        String photoUrl,
        String gender,
        java.math.BigDecimal ratingAverage,
        long ratingCount
    ) {
        public DoctorView(
            long id,
            String fullName,
            String title,
            long specialtyId,
            String specialtyName,
            String bio,
            long serviceId,
            String serviceName,
            String price,
            String serviceDescription,
            String photoUrl,
            String gender
        ) {
            this(
                id,
                fullName,
                title,
                specialtyId,
                specialtyName,
                bio,
                serviceId,
                serviceName,
                price,
                serviceDescription,
                photoUrl,
                gender,
                null,
                0
            );
        }
    }

    public record SlotView(
        long id,
        long doctorId,
        String doctorName,
        long roomId,
        String roomName,
        long serviceId,
        String serviceName,
        Instant startsAt,
        Instant endsAt,
        String status,
        boolean available,
        String price,
        String rejectionReason,
        String note
    ) {}

    public record AppointmentView(
        long id,
        String code,
        long patientId,
        String patientName,
        SlotView slot,
        String status,
        String symptoms,
        Integer queueNumber,
        String bookedPrice,
        Instant checkedInAt,
        int revision
    ) {}

    public record CreateSlotRequest(
        @NotNull Long roomId,
        @NotNull Long serviceId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 500) String note
    ) {}

    public record RejectSlotRequest(@NotBlank @Size(max = 500) String reason) {}

    public record BookRequest(
        @NotNull Long slotId,
        Long patientId,
        @Size(max = 1000) String symptoms
    ) {}

    public record RescheduleRequest(@NotNull Long slotId) {}

    public record CancelRequest(@Size(max = 500) String reason) {}
}
