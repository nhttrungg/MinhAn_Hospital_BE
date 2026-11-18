package vn.antam.clinic.service.dashboard;

import static vn.antam.clinic.service.dto.dashboard.DashboardDtos.*;

import java.time.*;
import java.util.*;

/** Business contract; implemented by the Spring-managed DashboardOverviewServiceImpl. */
public interface DashboardOverviewService {
    public Overview overview(int days);
}
