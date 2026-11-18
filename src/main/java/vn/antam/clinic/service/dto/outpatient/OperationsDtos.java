package vn.antam.clinic.service.dto.outpatient;

public final class OperationsDtos {

    private OperationsDtos() {}

    public record DoctorReference(long id, String fullName, boolean active) {}

    public record Change(
        long id,
        String action,
        Long oldSlotId,
        Long newSlotId,
        String actorName,
        String occurredAt
    ) {}
}
