package vn.antam.clinic.service.reports;

import java.time.LocalDate;
import vn.antam.clinic.service.dto.reports.ReportsDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed ReportsServiceImpl. */
public interface ReportsService {
    public Report report(LocalDate from, LocalDate to, Long doctorId, Long serviceId);

    public String csv(LocalDate from, LocalDate to, Long doctorId, Long serviceId);

    public PageResponse<Audit> audit(String q, int page, int size);
}
