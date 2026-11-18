package vn.antam.clinic.service.staff;

import org.springframework.data.domain.Pageable;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.dto.staff.StaffDtos.CreateStaffRequest;
import vn.antam.clinic.service.dto.staff.StaffDtos.StaffResponse;
import vn.antam.clinic.service.dto.staff.StaffDtos.UpdateStaffRequest;

/** Business contract; implemented by the Spring-managed StaffAccountServiceImpl. */
public interface StaffAccountService {
    public PageResponse<StaffResponse> search(
        String query,
        Long departmentId,
        String role,
        Boolean active,
        Pageable pageable
    );

    public StaffResponse get(long id);

    public StaffResponse me();

    public long countActive();

    public StaffResponse create(CreateStaffRequest request);

    public StaffResponse update(long id, UpdateStaffRequest request);

    public void resetPassword(long id, String newPassword);
}
