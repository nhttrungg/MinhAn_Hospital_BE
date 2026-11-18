package vn.antam.clinic.web.rest.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.dto.identity.PatientRegistrationDtos.*;
import vn.antam.clinic.service.identity.PatientRegistrationService;

@RestController
@RequestMapping("/api/v1/auth")
public class PatientRegistrationResource {

    private final PatientRegistrationService registrations;

    public PatientRegistrationResource(PatientRegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        registrations.register(request);
        return new MessageResponse("Đã tạo tài khoản. Vui lòng kiểm tra email để kích hoạt.");
    }

    @PostMapping("/activate")
    public MessageResponse activate(@Valid @RequestBody TokenRequest request) {
        registrations.activate(request.token());
        return new MessageResponse("Tài khoản đã được kích hoạt. Bạn có thể đăng nhập.");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgot(@Valid @RequestBody ForgotRequest request) {
        registrations.forgotPassword(request.email());
        return new MessageResponse("Nếu email đã đăng ký, hướng dẫn đặt lại mật khẩu sẽ được gửi.");
    }

    @PostMapping("/reset-password")
    public MessageResponse reset(@Valid @RequestBody ResetRequest request) {
        registrations.resetPassword(request.token(), request.password());
        return new MessageResponse("Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
    }
}
