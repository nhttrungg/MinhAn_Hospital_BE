package vn.antam.clinic.web.rest.identity;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.security.SecurityUtils;
import vn.antam.clinic.service.audit.*;
import vn.antam.clinic.service.billing.*;
import vn.antam.clinic.service.booking.*;
import vn.antam.clinic.service.catalog.*;
import vn.antam.clinic.service.chatbot.*;
import vn.antam.clinic.service.clinical.*;
import vn.antam.clinic.service.dashboard.*;
import vn.antam.clinic.service.demo.*;
import vn.antam.clinic.service.diagnostic.*;
import vn.antam.clinic.service.document.*;
import vn.antam.clinic.service.identity.*;
import vn.antam.clinic.service.notification.*;
import vn.antam.clinic.service.organization.*;
import vn.antam.clinic.service.outpatient.*;
import vn.antam.clinic.service.payment.*;
import vn.antam.clinic.service.pharmacy.*;
import vn.antam.clinic.service.qr.*;
import vn.antam.clinic.service.realtime.*;
import vn.antam.clinic.service.reports.*;
import vn.antam.clinic.service.scheduler.*;
import vn.antam.clinic.service.shared.*;
import vn.antam.clinic.service.staff.*;
import vn.antam.clinic.service.support.QrImages;

@RestController
public class MfaResource {

    private final MfaService mfa;
    private final TotpService totp;
    private final Clock clock;
    private static final String SETUP = "ANTAM_MFA_SETUP";

    private record Setup(
        long userId,
        byte[] encrypted,
        long expiresAt,
        int attempts
    ) implements Serializable {}

    public record Confirm(String password, String code) {}

    public MfaResource(MfaService mfa, TotpService totp, Clock clock) {
        this.mfa = mfa;
        this.totp = totp;
        this.clock = clock;
    }

    @GetMapping("/api/v1/auth/mfa/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", mfa.enabled(user()));
    }

    @PostMapping("/api/v1/auth/mfa/setup")
    public Object setup(@RequestBody Confirm input, HttpServletRequest request) {
        long id = user();
        mfa.password(id, input.password());
        if (mfa.enabled(id)) throw conflict("2FA đã bật. Tắt trước khi thiết lập lại.");
        String secret = totp.secret();
        byte[] encrypted = mfa.encrypt(secret);
        request
            .getSession()
            .setAttribute(
                SETUP,
                new Setup(id, encrypted, clock.instant().plusSeconds(300).getEpochSecond(), 0)
            );
        String uri =
            "otpauth://totp/" +
            URLEncoder.encode(
                "MinhAn:" + SecurityUtils.currentPrincipal().getUsername(),
                StandardCharsets.UTF_8
            ).replace("+", "%20") +
            "?secret=" +
            secret +
            "&issuer=MinhAn&algorithm=SHA1&digits=6&period=30";
        return Map.of("secret", secret, "image", QrImages.image(uri));
    }

    @PostMapping("/api/v1/auth/mfa/enable")
    public Object enable(@RequestBody Confirm input, HttpServletRequest request) {
        long id = user();
        Setup setup =
            request.getSession(false) == null
                ? null
                : (Setup) request.getSession(false).getAttribute(SETUP);
        if (
            setup == null ||
            setup.userId() != id ||
            setup.expiresAt() <= clock.instant().getEpochSecond() ||
            setup.attempts() >= 5
        ) throw conflict("Thiết lập đã hết hạn. Hãy quét QR mới.");
        request
            .getSession()
            .setAttribute(
                SETUP,
                new Setup(id, setup.encrypted(), setup.expiresAt(), setup.attempts() + 1)
            );
        var codes = mfa.enable(id, setup.encrypted(), input.code());
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("recoveryCodes", codes);
    }

    @PostMapping("/api/v1/auth/mfa/disable")
    public Object disable(@RequestBody Confirm input, HttpServletRequest request) {
        long id = user();
        mfa.password(id, input.password());
        mfa.reset(id);
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("enabled", false);
    }

    @PostMapping("/api/v1/staff-accounts/{id}/mfa/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public Object reset(@PathVariable long id) {
        mfa.reset(id);
        return Map.of("enabled", false);
    }

    @PostMapping("/api/v1/staff-accounts/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public Object unlock(@PathVariable long id) {
        mfa.unlock(id);
        return Map.of("unlocked", true);
    }
}
