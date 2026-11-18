package vn.antam.clinic.web.rest.outpatient;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.dto.billing.BillingDtos.InvoiceView;
import vn.antam.clinic.service.dto.booking.BookingDtos;
import vn.antam.clinic.service.dto.booking.PatientDtos;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.EncounterView;
import vn.antam.clinic.service.dto.outpatient.OperationsDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.outpatient.OperationsService;

@RestController
@RequestMapping("/api/v1")
public class OperationsResource {

    private final OperationsService service;

    public OperationsResource(OperationsService service) {
        this.service = service;
    }

    @GetMapping("/schedules/doctors")
    public List<DoctorReference> doctorReferences() {
        return service.doctorReferences();
    }

    @GetMapping("/appointments/search")
    public PageResponse<BookingDtos.AppointmentView> appointments(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) Long doctorId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Long patientId
    ) {
        return service.appointments(q, from, to, doctorId, status, page, size, patientId);
    }

    @GetMapping("/schedules/search")
    public PageResponse<BookingDtos.SlotView> slots(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) Long doctorId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.slots(from, to, doctorId, status, page, size);
    }

    @GetMapping("/appointments/{id}/history")
    public List<Change> history(@PathVariable long id) {
        return service.history(id);
    }

    @GetMapping("/patients/search")
    public PageResponse<PatientDtos.PatientView> patients(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.patients(q, page, size);
    }

    @GetMapping("/encounters/search")
    public PageResponse<EncounterView> encounters(
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.encounters(patientId, from, to, status, page, size);
    }

    @GetMapping("/billing/search")
    public PageResponse<InvoiceView> invoices(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Long patientId
    ) {
        return service.invoices(q, status, from, to, page, size, patientId);
    }
}
