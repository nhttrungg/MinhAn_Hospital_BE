package vn.antam.clinic.service.dashboard;

import vn.antam.clinic.service.dto.dashboard.DashboardSummary;

/** Business contract; implemented by the Spring-managed DashboardServiceImpl. */
public interface DashboardService {
    public DashboardSummary summary();
}
