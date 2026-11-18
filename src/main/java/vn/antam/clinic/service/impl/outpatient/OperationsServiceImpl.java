package vn.antam.clinic.service.impl.outpatient;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.antam.clinic.security.PatientAccess;
import vn.antam.clinic.service.billing.BillingService;
import vn.antam.clinic.service.booking.BookingService;
import vn.antam.clinic.service.booking.PatientService;
import vn.antam.clinic.service.clinical.EncounterService;
import vn.antam.clinic.service.dto.billing.BillingDtos.InvoiceView;
import vn.antam.clinic.service.dto.booking.BookingDtos;
import vn.antam.clinic.service.dto.booking.PatientDtos;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.EncounterView;
import vn.antam.clinic.service.dto.outpatient.OperationsDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.outpatient.OperationsService;
import vn.antam.clinic.service.support.ClinicJdbc;

@Service
public class OperationsServiceImpl implements OperationsService {

    private final JdbcTemplate jdbc;
    private final BookingService bookings;
    private final PatientService patients;
    private final EncounterService encounters;
    private final BillingService billing;
    private final PatientAccess access;

    public OperationsServiceImpl(
        JdbcTemplate jdbc,
        BookingService bookings,
        PatientService patients,
        EncounterService encounters,
        BillingService billing,
        PatientAccess access
    ) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.patients = patients;
        this.encounters = encounters;
        this.billing = billing;
        this.access = access;
    }

    @Override
    public List<DoctorReference> doctorReferences() {
        ClinicJdbc.require("ADMIN", "RECEPTIONIST", "DOCTOR");
        Long own = ClinicJdbc.role("DOCTOR") ? ClinicJdbc.staff(jdbc) : null;
        return jdbc.query(
            "select st.id,u.full_name,st.active from doctor_profiles dp join staff st on st.id=dp.staff_id join users u on u.id=st.user_id where (? is null or st.id=?) order by u.full_name,st.id",
            (r, n) -> new DoctorReference(r.getLong(1), r.getString(2), r.getBoolean(3)),
            own,
            own
        );
    }

    @Override
    public PageResponse<BookingDtos.AppointmentView> appointments(
        String q,
        LocalDate from,
        LocalDate to,
        Long doctorId,
        String status,
        int page,
        int size
    ) {
        return appointments(q, from, to, doctorId, status, page, size, null);
    }

    @Override
    public PageResponse<BookingDtos.AppointmentView> appointments(
        String q,
        LocalDate from,
        LocalDate to,
        Long doctorId,
        String status,
        int page,
        int size,
        Long patientId
    ) {
        ClinicJdbc.require("ADMIN", "RECEPTIONIST", "PATIENT", "DOCTOR");
        if (patientId != null && ClinicJdbc.role("PATIENT")) access.requireOwn(
            ClinicJdbc.user(),
            patientId
        );
        var dates = ClinicJdbc.dates(from, to);
        String like = ClinicJdbc.like(q);
        Long patientUser = ClinicJdbc.role("PATIENT") ? ClinicJdbc.user() : null;
        if (ClinicJdbc.role("DOCTOR")) doctorId = ClinicJdbc.staff(jdbc);
        return ClinicJdbc.queryPage(
            jdbc,
            "select a.id from appointments a join patient_profiles p on p.id=a.patient_id join appointment_slots sl on sl.id=a.slot_id " +
                "where sl.starts_at>=? and sl.starts_at<? and (? is null or sl.doctor_staff_id=?) and (? is null or a.status=?) and (? is null or (" +
                PatientAccess.ownedSql("p") +
                ")) " +
                "and (? is null or a.patient_id=?) and (p.full_name like ? or p.patient_code like ? or a.appointment_code like ?) order by sl.starts_at,a.id",
            (r, n) -> bookings.viewAppointment(r.getLong(1)),
            page,
            size,
            dates.from(),
            dates.to(),
            doctorId,
            doctorId,
            status,
            status,
            patientUser,
            patientUser,
            patientUser,
            patientId,
            patientId,
            like,
            like,
            like
        );
    }

    @Override
    public PageResponse<BookingDtos.SlotView> slots(
        LocalDate from,
        LocalDate to,
        Long doctorId,
        String status,
        int page,
        int size
    ) {
        ClinicJdbc.require("ADMIN", "DOCTOR");
        var dates = ClinicJdbc.dates(from, to);
        if (ClinicJdbc.role("DOCTOR")) doctorId = ClinicJdbc.staff(jdbc);
        return ClinicJdbc.queryPage(
            jdbc,
            "select id from appointment_slots where starts_at>=? and starts_at<? and (? is null or doctor_staff_id=?) and (? is null or status=?) order by starts_at,id",
            (r, n) -> bookings.findSlot(r.getLong(1)),
            page,
            size,
            dates.from(),
            dates.to(),
            doctorId,
            doctorId,
            status,
            status
        );
    }

    @Override
    public List<Change> history(long id) {
        bookings.viewAppointment(id);
        return jdbc.query(
            "select c.*,u.full_name actor_name from appointment_changes c left join users u on u.id=c.actor_user_id where c.appointment_id=? order by c.id",
            (r, n) ->
                new Change(
                    r.getLong("id"),
                    r.getString("action"),
                    (Long) r.getObject("old_slot_id"),
                    (Long) r.getObject("new_slot_id"),
                    r.getString("actor_name"),
                    r.getTimestamp("occurred_at").toInstant().toString()
                ),
            id
        );
    }

    @Override
    public PageResponse<PatientDtos.PatientView> patients(String q, int page, int size) {
        ClinicJdbc.require("ADMIN", "RECEPTIONIST");
        String like = ClinicJdbc.like(q);
        return ClinicJdbc.queryPage(
            jdbc,
            "select id from patient_profiles where active=1 and (full_name like ? or patient_code like ? or phone like ?) order by id desc",
            (r, n) -> patients.get(r.getLong(1)),
            page,
            size,
            like,
            like,
            like
        );
    }

    @Override
    public PageResponse<EncounterView> encounters(
        Long patientId,
        LocalDate from,
        LocalDate to,
        String status,
        int page,
        int size
    ) {
        ClinicJdbc.require("PATIENT", "DOCTOR");
        var dates = ClinicJdbc.dates(from, to);
        Long user = ClinicJdbc.role("PATIENT") ? ClinicJdbc.user() : null;
        Long doctor = ClinicJdbc.role("DOCTOR") ? ClinicJdbc.staff(jdbc) : null;
        if (patientId != null && user != null) access.requireOwn(user, patientId);
        if (
            patientId != null &&
            doctor != null &&
            jdbc.queryForObject(
                "select count(*) from encounters where patient_id=? and doctor_staff_id=? and status='IN_PROGRESS'",
                Integer.class,
                patientId,
                doctor
            ) == 0
        ) throw ClinicJdbc.forbidden();
        return ClinicJdbc.queryPage(
            jdbc,
            "select e.id from encounters e join patient_profiles p on p.id=e.patient_id join appointments a on a.id=e.appointment_id join appointment_slots sl on sl.id=a.slot_id " +
                "where sl.starts_at>=? and sl.starts_at<? and (? is null or (" +
                PatientAccess.ownedSql("p") +
                ")) and (? is null or e.patient_id=?) and (? is null or e.status=?) " +
                "and (? is null or e.status='COMPLETED') and (? is null or (? is null and e.doctor_staff_id=?) or (? is not null and e.status='COMPLETED')) order by sl.starts_at desc,e.id desc",
            (r, n) -> encounters.get(r.getLong(1)),
            page,
            size,
            dates.from(),
            dates.to(),
            user,
            user,
            user,
            patientId,
            patientId,
            status,
            status,
            user,
            doctor,
            patientId,
            doctor,
            patientId
        );
    }

    @Override
    public PageResponse<InvoiceView> invoices(
        String q,
        String status,
        LocalDate from,
        LocalDate to,
        int page,
        int size
    ) {
        return invoices(q, status, from, to, page, size, null);
    }

    @Override
    public PageResponse<InvoiceView> invoices(
        String q,
        String status,
        LocalDate from,
        LocalDate to,
        int page,
        int size,
        Long patientId
    ) {
        ClinicJdbc.require("ADMIN", "CASHIER", "PATIENT");
        if (patientId != null && ClinicJdbc.role("PATIENT")) access.requireOwn(
            ClinicJdbc.user(),
            patientId
        );
        var dates = ClinicJdbc.dates(from, to);
        String like = ClinicJdbc.like(q);
        Long user = ClinicJdbc.role("PATIENT") ? ClinicJdbc.user() : null;
        return ClinicJdbc.queryPage(
            jdbc,
            "select i.id from invoices i join patient_profiles p on p.id=i.patient_id left join appointments a on a.id=i.appointment_id " +
                "where i.created_at>=? and i.created_at<? and (? is null or (" +
                PatientAccess.ownedSql("p") +
                ")) and (? is null or i.patient_id=?) and (? is null or i.status=?) and (p.full_name like ? or i.invoice_code like ? or a.appointment_code like ?) and (?=0 or i.counter_id is null or exists(select 1 from counter_staff cs join staff st on st.id=cs.staff_id where cs.counter_id=i.counter_id and st.user_id=? and st.active=1)) order by i.id desc",
            (r, n) -> billing.invoice(r.getLong(1)),
            page,
            size,
            dates.from(),
            dates.to(),
            user,
            user,
            user,
            patientId,
            patientId,
            status,
            status,
            like,
            like,
            like,
            ClinicJdbc.role("CASHIER") ? 1 : 0,
            ClinicJdbc.user()
        );
    }
}
