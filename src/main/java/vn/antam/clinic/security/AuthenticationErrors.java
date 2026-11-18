package vn.antam.clinic.security;

import org.springframework.http.HttpStatus;
import vn.antam.clinic.web.rest.errors.ApiException;

/** Stateless formatting and validation shared by HTTP and business layers. */
public final class AuthenticationErrors {

    private AuthenticationErrors() {}

    public static ApiException locked() {
        return new ApiException(
            HttpStatus.LOCKED,
            "Tạm khóa đăng nhập",
            "Đã nhập sai 5 lần. Hãy thử lại sau 15 phút hoặc liên hệ quản trị viên."
        );
    }
}
