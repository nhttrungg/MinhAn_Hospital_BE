package vn.antam.clinic.service.booking;

import java.util.List;
import vn.antam.clinic.service.dto.booking.AbsenceDtos.*;
import vn.antam.clinic.service.dto.booking.BookingDtos.AppointmentView;

public interface AbsenceService {
    List<Impact> preview(AbsenceInput input);
    AbsenceConfirmed confirm(AbsenceInput input);
    List<Absence> absences();
    List<Coordination> coordination(Long absenceId);
    Coordination contact(long appointmentId, ContactInput input);
    List<Candidate> candidates(long appointmentId, int days);
    AppointmentView finalizeReplacement(long appointmentId, FinalizeInput input);
}
