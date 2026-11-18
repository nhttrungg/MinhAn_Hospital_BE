package vn.antam.clinic.service.booking;

import java.util.List;
import vn.antam.clinic.service.dto.booking.FamilyDtos.*;
import vn.antam.clinic.service.dto.booking.PatientDtos.PatientInput;
import vn.antam.clinic.service.dto.booking.PatientDtos.PatientView;

/** Business contract; implemented by the Spring-managed PatientServiceImpl. */
public interface PatientService {
    public PatientView me();

    public List<PatientView> search(String query);

    public PatientView get(long id);

    public PatientView create(PatientInput input);

    public PatientView update(long id, PatientInput input);

    public List<FamilyView> family();

    public FamilyView addFamily(FamilyInput input);

    public FamilyView updateFamily(long id, FamilyInput input);

    public void removeFamily(long id);
}
