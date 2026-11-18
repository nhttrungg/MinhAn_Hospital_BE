package vn.antam.clinic.service.dto.reports;

import jakarta.validation.constraints.*;
import java.util.List;

public final class OperationsDtos {

    private OperationsDtos() {}

    public record Thresholds(
        @Min(1) @Max(1440) int paymentMinutes,
        @Min(1) @Max(1440) int doctorMinutes,
        @Min(1) @Max(1440) int diagnosticMinutes
    ) {}

    public record DiagnosticProgress(
        long orderId,
        String serviceName,
        String status,
        String phase
    ) {}

    public record Bottleneck(
        long encounterId,
        long appointmentId,
        String appointmentCode,
        String patientName,
        long doctorId,
        String doctorName,
        long roomId,
        String roomName,
        String stage,
        String stageStartedAt,
        Long waitingSeconds,
        boolean insufficientHistory,
        boolean breached,
        String href,
        List<DiagnosticProgress> diagnostics
    ) {}

    public record StageMetric(
        String stage,
        long count,
        long breached,
        Double medianSeconds,
        Double p90Seconds
    ) {}

    public record Bottlenecks(
        String checkedAt,
        List<Bottleneck> encounters,
        List<StageMetric> stages
    ) {}

    public record CapacityRow(
        long doctorId,
        String doctorName,
        long roomId,
        String roomName,
        long departmentId,
        long serviceId,
        String serviceName,
        String day,
        int hour,
        long opened,
        long serving,
        long closed,
        long booked,
        long free,
        long checkedIn,
        long examined,
        long openedMinutes,
        long bookedMinutes,
        Double occupancy,
        Double examinationRate,
        Double roomScheduleUtilization,
        boolean insufficientHistory
    ) {}

    public record Capacity(String from, String to, List<CapacityRow> rows) {}
}
