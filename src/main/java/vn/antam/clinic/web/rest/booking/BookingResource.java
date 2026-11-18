package vn.antam.clinic.web.rest.booking;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.booking.BookingService;
import vn.antam.clinic.service.dto.booking.BookingDtos.AppointmentView;
import vn.antam.clinic.service.dto.booking.BookingDtos.BookRequest;
import vn.antam.clinic.service.dto.booking.BookingDtos.CancelRequest;
import vn.antam.clinic.service.dto.booking.BookingDtos.CreateSlotRequest;
import vn.antam.clinic.service.dto.booking.BookingDtos.DoctorView;
import vn.antam.clinic.service.dto.booking.BookingDtos.RejectSlotRequest;
import vn.antam.clinic.service.dto.booking.BookingDtos.RescheduleRequest;
import vn.antam.clinic.service.dto.booking.BookingDtos.SlotView;
import vn.antam.clinic.service.dto.booking.BookingDtos.SpecialtyView;

@RestController
@RequestMapping("/api/v1")
public class BookingResource {

    private final BookingService bookings;

    public BookingResource(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping("/specialties")
    public List<SpecialtyView> specialties() {
        return bookings.specialties();
    }

    @GetMapping("/doctors")
    public List<DoctorView> doctors(@RequestParam(required = false) Long specialtyId) {
        return bookings.doctors(specialtyId);
    }

    @GetMapping("/slots")
    public List<SlotView> slots(
        @RequestParam Instant from,
        @RequestParam Instant to,
        @RequestParam(required = false) Long doctorId,
        @RequestParam(required = false) Long serviceId,
        @RequestParam(required = false) Long specialtyId
    ) {
        return bookings.availableSlots(from, to, doctorId, serviceId, specialtyId);
    }

    @GetMapping("/schedules/my-slots")
    public List<SlotView> managedSlots() {
        return bookings.managedSlots();
    }

    @PostMapping("/schedules/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public SlotView createSlot(@Valid @RequestBody CreateSlotRequest request) {
        return bookings.createSlot(request);
    }

    @PostMapping("/schedules/slots/{id}/submit")
    public SlotView submitSlot(@PathVariable long id) {
        return bookings.submitSlot(id);
    }

    @PutMapping("/schedules/slots/{id}")
    public SlotView editSlot(@PathVariable long id, @Valid @RequestBody CreateSlotRequest request) {
        return bookings.editSlot(id, request);
    }

    @DeleteMapping("/schedules/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(@PathVariable long id) {
        bookings.deleteSlot(id);
    }

    @PostMapping("/schedules/slots/{id}/reject-close")
    public SlotView rejectClose(
        @PathVariable long id,
        @Valid @RequestBody RejectSlotRequest request
    ) {
        return bookings.rejectClose(id, request.reason());
    }

    @PostMapping("/schedules/slots/{id}/approve")
    public SlotView approveSlot(@PathVariable long id) {
        return bookings.approveSlot(id);
    }

    @PostMapping("/schedules/slots/{id}/reject")
    public SlotView rejectSlot(
        @PathVariable long id,
        @Valid @RequestBody RejectSlotRequest request
    ) {
        return bookings.rejectSlot(id, request.reason());
    }

    @PostMapping("/schedules/slots/{id}/request-close")
    public SlotView requestClose(@PathVariable long id) {
        return bookings.requestClose(id);
    }

    @PostMapping("/schedules/slots/{id}/approve-close")
    public SlotView approveClose(@PathVariable long id) {
        return bookings.approveClose(id);
    }

    @GetMapping("/appointments/me")
    public List<AppointmentView> myAppointments() {
        return bookings.myAppointments();
    }

    @GetMapping("/appointments")
    public List<AppointmentView> receptionAppointments() {
        return bookings.receptionAppointments();
    }

    @GetMapping("/appointments/{id}")
    public AppointmentView appointment(@PathVariable long id) {
        return bookings.viewAppointment(id);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentView book(
        @Valid @RequestBody BookRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String key
    ) {
        return bookings.book(request, key);
    }

    @PostMapping("/appointments/{id}/reschedule")
    public AppointmentView reschedule(
        @PathVariable long id,
        @Valid @RequestBody RescheduleRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String key
    ) {
        return bookings.reschedule(id, request.slotId(), key);
    }

    @PostMapping("/appointments/{id}/cancel")
    public AppointmentView cancel(
        @PathVariable long id,
        @RequestBody(required = false) CancelRequest request
    ) {
        return bookings.cancel(id, request == null ? null : request.reason());
    }

    @PostMapping("/appointments/{id}/check-in")
    public AppointmentView checkIn(@PathVariable long id) {
        return bookings.checkIn(id);
    }

    @PostMapping("/appointments/{id}/no-show")
    public AppointmentView noShow(@PathVariable long id) {
        return bookings.markNoShow(id);
    }

    @GetMapping("/queue/doctor/me")
    public List<AppointmentView> myQueue() {
        return bookings.queueForDoctor();
    }
}
