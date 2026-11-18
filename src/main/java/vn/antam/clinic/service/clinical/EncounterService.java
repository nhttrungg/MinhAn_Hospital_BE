package vn.antam.clinic.service.clinical;

import java.util.List;
import vn.antam.clinic.service.dto.clinical.EncounterDtos;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.AmendmentRequest;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.DraftRequest;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.EncounterView;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.VitalsRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed EncounterServiceImpl. */
public interface EncounterService {
    public EncounterView forAppointment(long appointmentId);

    public EncounterView get(long id);

    public PageResponse<EncounterView> myRecords(
        java.time.LocalDate from,
        java.time.LocalDate to,
        int page,
        int size
    );

    public List<EncounterDtos.NurseReference> nurses();

    public PageResponse<EncounterView> assigned(
        java.time.LocalDate from,
        java.time.LocalDate to,
        String status,
        int page,
        int size
    );

    public EncounterView start(long appointmentId);

    public EncounterView save(long id, DraftRequest request);

    public EncounterView waitResults(long id, int revision);

    public EncounterView complete(long id, int revision);

    public EncounterView assignNurse(long id, long nurseStaffId);

    public EncounterView vitals(long id, VitalsRequest request);

    public EncounterView amend(long id, AmendmentRequest request);
}
