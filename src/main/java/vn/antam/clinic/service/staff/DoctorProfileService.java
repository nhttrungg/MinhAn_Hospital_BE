package vn.antam.clinic.service.staff;

import java.util.List;

/** Business contract; implemented by the Spring-managed DoctorProfileServiceImpl. */
public interface DoctorProfileService {
    public record Profile(
        Long specialtyId,
        String bio,
        String photoUrl,
        List<Long> serviceIds,
        String gender
    ) {}

    public Profile get(long id);

    public void save(
        long id,
        String role,
        Long specialty,
        String bio,
        String photoUrl,
        List<Long> serviceIds,
        boolean active,
        String gender
    );
}
