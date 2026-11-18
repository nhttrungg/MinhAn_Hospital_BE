package vn.antam.clinic.service.booking;

import vn.antam.clinic.service.dto.booking.PublicClinicDtos.*;

/** Business contract; implemented by the Spring-managed PublicClinicServiceImpl. */
public interface PublicClinicService {
    public ClinicView clinic();
}
