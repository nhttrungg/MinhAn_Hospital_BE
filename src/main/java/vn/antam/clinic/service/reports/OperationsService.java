package vn.antam.clinic.service.reports;

import java.time.LocalDate;
import vn.antam.clinic.service.dto.reports.OperationsDtos.*;

public interface OperationsService {
    Thresholds thresholds();
    Thresholds updateThresholds(Thresholds input);
    Bottlenecks bottlenecks(
        LocalDate from,
        LocalDate to,
        Long departmentId,
        Long doctorId,
        Long roomId
    );
    Capacity capacity(
        LocalDate from,
        LocalDate to,
        Long departmentId,
        Long doctorId,
        Long roomId,
        Long serviceId
    );
    String capacityCsv(
        LocalDate from,
        LocalDate to,
        Long departmentId,
        Long doctorId,
        Long roomId,
        Long serviceId
    );
}
