package vn.antam.clinic.service.dto.booking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class ScheduleTemplateDtos {

    private ScheduleTemplateDtos() {}

    public record TemplateView(
        long id,
        int weekday,
        LocalTime startsTime,
        LocalTime endsTime,
        long roomId,
        long serviceId,
        boolean active
    ) {}

    public record CreateTemplateRequest(
        @Min(1) @Max(7) int weekday,
        @NotNull LocalTime startsTime,
        @NotNull LocalTime endsTime,
        @NotNull Long roomId,
        @NotNull Long serviceId
    ) {}

    public record CopyRequest(@NotEmpty List<@NotNull LocalDate> dates) {}

    public record GenerateRequest(@NotNull LocalDate from, @NotNull LocalDate to) {}
}
