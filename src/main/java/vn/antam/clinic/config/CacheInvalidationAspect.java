package vn.antam.clinic.config;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(500)
public class CacheInvalidationAspect {

    private final CacheInvalidation invalidation;

    public CacheInvalidationAspect(CacheInvalidation invalidation) {
        this.invalidation = invalidation;
    }

    @AfterReturning(
        "execution(* vn.antam.clinic.service.impl.identity.AccountServiceImpl.update*(..)) || execution(* vn.antam.clinic.service.impl.identity.AccountServiceImpl.*Password(..)) || execution(* vn.antam.clinic.service.impl.identity.MfaServiceImpl.enable(..)) || execution(* vn.antam.clinic.service.impl.identity.MfaServiceImpl.reset(..)) || execution(* vn.antam.clinic.service.impl.identity.PatientRegistrationServiceImpl.resetPassword(..))"
    )
    public void users() {
        invalidation.afterCommit("users");
        invalidation.afterCommit("doctors.public");
        invalidation.afterCommit("availableSlots");
    }

    @AfterReturning(
        "execution(* vn.antam.clinic.service.impl.catalog.OutpatientCatalogServiceImpl.updateSettings(..))"
    )
    public void settings() {
        invalidation.afterCommit("clinicSettings");
    }

    @AfterReturning(
        "execution(* vn.antam.clinic.service.impl.staff.DoctorProfileServiceImpl.save(..)) || execution(* vn.antam.clinic.service.impl.catalog.*.setPrice(..)) || execution(* vn.antam.clinic.service.impl.catalog.*.create*(..)) || execution(* vn.antam.clinic.service.impl.catalog.*.update*(..)) || execution(* vn.antam.clinic.service.impl.catalog.*.delete*(..)) || execution(* vn.antam.clinic.service.impl.organization.*.create*(..)) || execution(* vn.antam.clinic.service.impl.organization.*.update*(..)) || execution(* vn.antam.clinic.service.impl.organization.*.delete*(..))"
    )
    public void catalog() {
        invalidation.afterCommit("catalog.departments");
        invalidation.afterCommit("catalog.services");
        invalidation.afterCommit("catalog.specialties");
        invalidation.afterCommit("doctors.public");
        invalidation.afterCommit("availableSlots");
    }

    @AfterReturning(
        "execution(* vn.antam.clinic.service.impl.clinical.ReviewServiceImpl.create(..)) || execution(* vn.antam.clinic.service.impl.clinical.ReviewServiceImpl.visibility(..))"
    )
    public void reviews() {
        invalidation.afterCommit("doctors.public");
    }

    @AfterReturning(
        "execution(* vn.antam.clinic.service.impl.booking.BookingServiceImpl.book(..)) || execution(* vn.antam.clinic.service.impl.booking.BookingServiceImpl.cancel(..)) || execution(* vn.antam.clinic.service.impl.booking.BookingServiceImpl.reschedule(..)) || execution(* vn.antam.clinic.service.impl.booking.BookingServiceImpl.*Slot(..)) || execution(* vn.antam.clinic.service.impl.booking.BookingServiceImpl.*Close(..)) || execution(* vn.antam.clinic.service.impl.booking.ScheduleTemplateServiceImpl.generate(..))"
    )
    public void slots() {
        invalidation.afterCommit("availableSlots");
    }
}
