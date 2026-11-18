package vn.antam.clinic.service.identity;

import vn.antam.clinic.service.dto.identity.PatientRegistrationDtos.RegisterRequest;

/** Business contract; implemented by the Spring-managed PatientRegistrationServiceImpl. */
public interface PatientRegistrationService {
    public void register(RegisterRequest request);

    public void activate(String token);

    public void forgotPassword(String address);

    public void resetPassword(String token, String password);
}
