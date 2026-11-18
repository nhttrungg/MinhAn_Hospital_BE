package vn.antam.clinic.service.dto.reports;

import java.util.List;

public final class ReportsDtos {

    private ReportsDtos() {}

    public record DoctorStats(
        long doctorId,
        String doctorName,
        long booked,
        long checkedIn,
        long completed,
        long cancelled,
        long noShow,
        Double ratingAverage,
        long ratingCount
    ) {}

    public record Report(
        long booked,
        long checkedIn,
        long completed,
        long cancelled,
        long noShow,
        String revenue,
        String balance,
        List<DoctorStats> doctors
    ) {}

    public record Audit(
        long id,
        String actorName,
        String action,
        String entityType,
        String entityId,
        String detail,
        String requestId,
        String createdAt
    ) {}
}
