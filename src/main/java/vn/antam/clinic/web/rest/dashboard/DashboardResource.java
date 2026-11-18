package vn.antam.clinic.web.rest.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.dashboard.DashboardOverviewService;
import vn.antam.clinic.service.dashboard.DashboardService;
import vn.antam.clinic.service.dto.dashboard.DashboardDtos.Overview;
import vn.antam.clinic.service.dto.dashboard.DashboardSummary;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardResource {

    private final DashboardService dashboard;
    private final DashboardOverviewService overview;

    public DashboardResource(DashboardService dashboard, DashboardOverviewService overview) {
        this.dashboard = dashboard;
        this.overview = overview;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return dashboard.summary();
    }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(defaultValue = "7") int days) {
        return overview.overview(days);
    }
}
