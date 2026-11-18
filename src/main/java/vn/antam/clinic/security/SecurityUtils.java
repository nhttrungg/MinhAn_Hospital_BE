package vn.antam.clinic.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.antam.clinic.web.rest.errors.ApiException;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static ClinicUserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
            authentication == null ||
            !(authentication.getPrincipal() instanceof ClinicUserPrincipal principal)
        ) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Bạn cần đăng nhập",
                "Phiên đăng nhập không còn hiệu lực."
            );
        }
        return principal;
    }
}
