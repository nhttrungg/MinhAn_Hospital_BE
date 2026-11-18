package vn.antam.clinic.service.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record Kpi(
        String key,
        String label,
        BigDecimal value,
        BigDecimal previous,
        String unit,
        BigDecimal changePercent
    ) {}

    public record Day(String date, long completed, long cancelled, long noShow) {}

    public record Revenue(String date, BigDecimal amount) {}

    public record Slice(String name, BigDecimal value) {}

    public record Overview(
        String role,
        int days,
        String generatedAt,
        List<Kpi> kpis,
        List<Day> visits,
        List<Revenue> revenue,
        List<Slice> services,
        List<Slice> doctors,
        List<Slice> statuses
    ) {}
}
