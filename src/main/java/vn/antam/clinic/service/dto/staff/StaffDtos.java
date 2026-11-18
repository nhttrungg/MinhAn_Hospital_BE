package vn.antam.clinic.service.dto.staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class StaffDtos {

    private StaffDtos() {}

    public record StaffResponse(
        Long id,
        Long userId,
        String username,
        String fullName,
        String email,
        String phone,
        List<String> roles,
        String employeeCode,
        Long departmentId,
        String departmentName,
        String title,
        String accountStatus,
        boolean active,
        Instant createdAt,
        Long specialtyId,
        String bio,
        String photoUrl,
        List<Long> serviceIds,
        String gender
    ) {}

    public record CreateStaffRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9._]{3,50}$", message = "Tên đăng nhập không hợp lệ")
        String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email(message = "Email không hợp lệ") @Size(max = 255) String email,
        @Pattern(regexp = "^$|^0\\d{8,10}$", message = "Số điện thoại không hợp lệ") String phone,
        @NotBlank String role,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{2,30}$", message = "Mã nhân viên không hợp lệ")
        String employeeCode,
        @NotNull Long departmentId,
        @NotBlank @Size(max = 100) String title,
        Long specialtyId,
        @Size(max = 1000) String bio,
        @Size(max = 500)
        @Pattern(
            regexp = "^$|^/images/doctors/[A-Za-z0-9_-]+\\.(jpg|jpeg|png|webp)$",
            message = "Chọn đường dẫn ảnh trong /images/doctors/."
        )
        String photoUrl,
        List<Long> serviceIds,
        @Pattern(
            regexp = "^$|MALE|FEMALE|OTHER",
            message = "Giới tính cần là Nam, Nữ, Khác hoặc chưa cung cấp."
        )
        String gender
    ) {}

    public record UpdateStaffRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email(message = "Email không hợp lệ") @Size(max = 255) String email,
        @Pattern(regexp = "^$|^0\\d{8,10}$", message = "Số điện thoại không hợp lệ") String phone,
        @NotBlank String role,
        @NotNull Long departmentId,
        @NotBlank @Size(max = 100) String title,
        @NotNull Boolean active,
        Long specialtyId,
        @Size(max = 1000) String bio,
        @Size(max = 500)
        @Pattern(
            regexp = "^$|^/images/doctors/[A-Za-z0-9_-]+\\.(jpg|jpeg|png|webp)$",
            message = "Chọn đường dẫn ảnh trong /images/doctors/."
        )
        String photoUrl,
        List<Long> serviceIds,
        @Pattern(
            regexp = "^$|MALE|FEMALE|OTHER",
            message = "Giới tính cần là Nam, Nữ, Khác hoặc chưa cung cấp."
        )
        String gender
    ) {}
}
