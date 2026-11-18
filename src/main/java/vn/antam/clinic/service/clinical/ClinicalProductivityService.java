package vn.antam.clinic.service.clinical;

import java.time.LocalDate;
import java.util.List;
import vn.antam.clinic.service.dto.clinical.ClinicalProductivityDtos.*;

public interface ClinicalProductivityService {
    List<Template> templates(Long specialtyId);
    Template createTemplate(TemplateInput input);
    Template updateTemplate(long id, TemplateInput input);
    Template templateState(long id, int version, boolean publish);
    ChartSaved saveChart(long id, ChartSync input);
    RxSaved saveRx(long id, RxSync input);
    PatientSummary summary(long id);
    DiagnosticHistory history(long id, LocalDate from, LocalDate to);
    Availability availability(StockRequest input);
}
