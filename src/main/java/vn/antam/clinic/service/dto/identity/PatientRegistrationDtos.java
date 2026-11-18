package vn.antam.clinic.service.dto.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PatientRegistrationDtos {

    private PatientRegistrationDtos() {}

    public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank String password,
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 20) String phone
    ) {}

    public record TokenRequest(@NotBlank String token) {}

    public record ForgotRequest(@NotBlank @Email String email) {}

    public record ResetRequest(@NotBlank String token, @NotBlank String password) {}

    public record MessageResponse(String message) {}
}
