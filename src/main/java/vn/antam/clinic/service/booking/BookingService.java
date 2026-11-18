package vn.antam.clinic.service.booking;

import java.time.Instant;
import java.util.List;
import vn.antam.clinic.service.dto.booking.BookingDtos.AppointmentView;
import vn.antam.clinic.service.dto.booking.BookingDtos.BookRequest;
import vn.antam.clinic.service.dto.booking.BookingDtos.CreateSlotRequest;
import vn.antam.clinic.service.dto.booking.BookingDtos.DoctorView;
import vn.antam.clinic.service.dto.booking.BookingDtos.SlotView;
import vn.antam.clinic.service.dto.booking.BookingDtos.SpecialtyView;

/** Business contract; implemented by the Spring-managed BookingServiceImpl. */
public interface BookingService {
    public List<SpecialtyView> specialties();

    public List<DoctorView> doctors(Long specialtyId);

    public List<SlotView> availableSlots(
        Instant from,
        Instant to,
        Long doctorId,
        Long serviceId,
        Long specialtyId
    );

    public List<SlotView> availableSlots(
        Instant from,
        Instant to,
        Long doctorId,
        Long serviceId,
        Long specialtyId,
        String gender
    );

    public List<SlotView> managedSlots();

    public SlotView createSlot(CreateSlotRequest request);

    public SlotView submitSlot(long id);

    public SlotView editSlot(long id, CreateSlotRequest request);

    public void deleteSlot(long id);

    public SlotView rejectClose(long id, String reason);

    public SlotView approveSlot(long id);

    public SlotView rejectSlot(long id, String reason);

    public SlotView requestClose(long id);

    public SlotView approveClose(long id);

    public AppointmentView book(BookRequest request, String key);

    public AppointmentView reschedule(long id, long newSlotId, String key);
    public AppointmentView rescheduleReplacement(
        long id,
        long newSlotId,
        String key,
        int coordinationRevision
    );

    public AppointmentView cancel(long id, String reason);

    public AppointmentView checkIn(long id);

    public AppointmentView markNoShow(long id);

    public List<AppointmentView> receptionAppointments();

    public List<AppointmentView> myAppointments();

    public List<AppointmentView> queueForDoctor();

    public AppointmentView viewAppointment(long id);

    public AppointmentView findAppointment(long id);

    public SlotView findSlot(long id);
}
