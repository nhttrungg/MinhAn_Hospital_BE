package vn.antam.clinic.service.outpatient;

import java.time.LocalDate;
import java.util.List;
import vn.antam.clinic.service.dto.billing.BillingDtos.InvoiceView;
import vn.antam.clinic.service.dto.booking.BookingDtos;
import vn.antam.clinic.service.dto.booking.PatientDtos;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.EncounterView;
import vn.antam.clinic.service.dto.outpatient.OperationsDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed OperationsServiceImpl. */
public interface OperationsService {
    public List<DoctorReference> doctorReferences();

    public PageResponse<BookingDtos.AppointmentView> appointments(
        String q,
        LocalDate from,
        LocalDate to,
        Long doctorId,
        String status,
        int page,
        int size
    );

    public PageResponse<BookingDtos.AppointmentView> appointments(
        String q,
        LocalDate from,
        LocalDate to,
        Long doctorId,
        String status,
        int page,
        int size,
        Long patientId
    );

    public PageResponse<BookingDtos.SlotView> slots(
        LocalDate from,
        LocalDate to,
        Long doctorId,
        String status,
        int page,
        int size
    );

    public List<Change> history(long id);

    public PageResponse<PatientDtos.PatientView> patients(String q, int page, int size);

    public PageResponse<EncounterView> encounters(
        Long patientId,
        LocalDate from,
        LocalDate to,
        String status,
        int page,
        int size
    );

    public PageResponse<InvoiceView> invoices(
        String q,
        String status,
        LocalDate from,
        LocalDate to,
        int page,
        int size
    );

    public PageResponse<InvoiceView> invoices(
        String q,
        String status,
        LocalDate from,
        LocalDate to,
        int page,
        int size,
        Long patientId
    );
}
