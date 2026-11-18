package vn.antam.clinic.service.dto.dashboard;

public record DashboardSummary(
    long departments,
    long rooms,
    long beds,
    long bedsAvailable,
    long services,
    long staff
) {}
