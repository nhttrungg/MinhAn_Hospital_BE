package vn.antam.clinic.service.dto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
        @NotBlank(message = "Nhập tên đăng nhập") @Size(max = 50) String username,
        @NotBlank(message = "Nhập mật khẩu") @Size(max = 72) String password
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "Nhập mật khẩu hiện tại") @Size(max = 72) String currentPassword,
        @NotBlank(message = "Nhập mật khẩu mới") @Size(max = 72) String newPassword
    ) {}

    public record ResetPasswordRequest(
        @NotBlank(message = "Nhập mật khẩu mới") @Size(max = 72) String newPassword
    ) {}

    public record CurrentUserResponse(
        long id,
        String username,
        String fullName,
        String email,
        String userType,
        List<String> roles
    ) {}

    public record RoleResponse(String code, String name) {}
}
