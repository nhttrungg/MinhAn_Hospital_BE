package vn.antam.clinic.service.clinical;

import java.util.List;
import vn.antam.clinic.service.dto.clinical.PrescriptionDtos.*;

/** Business contract; implemented by the Spring-managed PrescriptionServiceImpl. */
public interface PrescriptionService {
    public List<Prescription> list(long encounterId);

    public Prescription save(long encounterId, SaveRequest in, boolean amendment);

    public void finalizeDraft(long encounterId);

    public List<PrescriptionSafetyService.Warning> check(long encounterId, CheckRequest in);
}
