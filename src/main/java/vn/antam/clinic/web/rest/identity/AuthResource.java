package vn.antam.clinic.web.rest.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.Serializable;
import java.time.Clock;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.security.AuthenticationErrors;
import vn.antam.clinic.security.ClinicUserPrincipal;
import vn.antam.clinic.security.SecurityUtils;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.chatbot.ChatSessionStore;
import vn.antam.clinic.service.dto.identity.AuthDtos.ChangePasswordRequest;
import vn.antam.clinic.service.dto.identity.AuthDtos.CurrentUserResponse;
import vn.antam.clinic.service.dto.identity.AuthDtos.LoginRequest;
import vn.antam.clinic.service.identity.AccountService;
import vn.antam.clinic.service.identity.ClinicUserDetailsService;
import vn.antam.clinic.service.identity.LoginGuard;
import vn.antam.clinic.service.identity.MfaService;
import vn.antam.clinic.service.support.Text;
import vn.antam.clinic.web.rest.errors.ApiException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthResource {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AccountService accounts;
    private final AuditService audit;
    private final ChatSessionStore chatSessions;
    private final LoginGuard guard;
    private final MfaService mfa;
    private final ClinicUserDetailsService users;
    private final Clock clock;
    private final vn.antam.clinic.security.RedisRateLimiter rateLimiter;
    private final boolean rateLimitEnabled;
    private static final String PENDING = "ANTAM_PENDING_MFA";

    private record Pending(
        long userId,
        String username,
        long version,
        long expiresAt
    ) implements Serializable {}

    public record MfaCode(String code) {}

    public AuthResource(
        AuthenticationManager authenticationManager,
        SecurityContextRepository securityContextRepository,
        AccountService accounts,
        AuditService audit,
        ChatSessionStore chatSessions,
        LoginGuard guard,
        MfaService mfa,
        ClinicUserDetailsService users,
        Clock clock,
        vn.antam.clinic.security.RedisRateLimiter rateLimiter,
        @org.springframework.beans.factory.annotation.Value(
            "${app.security.rate-limit-enabled:true}"
        ) boolean rateLimitEnabled
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.accounts = accounts;
        this.audit = audit;
        this.chatSessions = chatSessions;
        this.guard = guard;
        this.mfa = mfa;
        this.users = users;
        this.clock = clock;
        this.rateLimiter = rateLimiter;
        this.rateLimitEnabled = rateLimitEnabled;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public Object login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        String username = Text.lower(request.username());
        // A new login must discard any previous authenticated context and pending challenge.
        chatSessions.reset(httpRequest.getSession(false));
        if (httpRequest.getSession(false) != null) httpRequest.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        guard.check(username);
        try {
            if (
                rateLimitEnabled && !rateLimiter.allow("login-username", username, 5)
            ) throw new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Quá nhiều yêu cầu",
                "Vui lòng chờ một phút rồi thử lại."
            );
        } catch (org.springframework.dao.DataAccessException unavailable) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Hệ thống đang bận",
                "Vui lòng thử lại sau vài giây."
            );
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, request.password())
            );
            ClinicUserPrincipal principal = (ClinicUserPrincipal) authentication.getPrincipal();
            if (mfa.enabled(principal.getId())) {
                httpRequest
                    .getSession(true)
                    .setAttribute(
                        PENDING,
                        new Pending(
                            principal.getId(),
                            username,
                            principal.getSessionVersion(),
                            clock.instant().plusSeconds(300).getEpochSecond()
                        )
                    );
                return Map.of("mfaRequired", true);
            }
            guard.success(principal.getId());
            chatSessions.reset(httpRequest.getSession(false));
            if (httpRequest.getSession(false) != null) {
                httpRequest.changeSessionId();
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            audit.record(
                "LOGIN_SUCCESS",
                "USER",
                String.valueOf(principal.getId()),
                principal.getUsername()
            );
            return new CurrentUserResponse(
                principal.getId(),
                principal.getUsername(),
                principal.getFullName(),
                principal.getEmail(),
                principal.getUserType().name(),
                principal
                    .getAuthorities()
                    .stream()
                    .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                    .sorted()
                    .toList()
            );
        } catch (DisabledException exception) {
            audit.record("LOGIN_FAILURE", "USER", null, username);
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Tài khoản chưa thể đăng nhập",
                "Tài khoản đang bị khóa hoặc chưa kích hoạt."
            );
        } catch (AuthenticationException exception) {
            audit.record("LOGIN_FAILURE", "USER", null, username);
            if (guard.failure(username)) throw AuthenticationErrors.locked();
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Đăng nhập thất bại",
                "Tên đăng nhập hoặc mật khẩu không đúng."
            );
        }
    }

    @PostMapping("/mfa/verify")
    public CurrentUserResponse verify(
        @RequestBody MfaCode input,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        HttpSession session = request.getSession(false);
        Pending pending = session == null ? null : (Pending) session.getAttribute(PENDING);
        if (pending == null || pending.expiresAt() <= clock.instant().getEpochSecond()) {
            if (session != null) session.removeAttribute(PENDING);
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Cần đăng nhập lại",
                "Yêu cầu xác thực đã hết hạn."
            );
        }
        guard.check(pending.username());
        if (!mfa.verify(pending.userId(), pending.version(), input.code())) {
            audit.record("MFA_FAILURE", "USER", String.valueOf(pending.userId()), null);
            if (guard.failure(pending.username())) {
                session.removeAttribute(PENDING);
                throw AuthenticationErrors.locked();
            }
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Mã xác thực không đúng",
                "Nhập mã mới từ ứng dụng hoặc mã khôi phục chưa sử dụng."
            );
        }
        ClinicUserPrincipal principal = (ClinicUserPrincipal) users.loadUserByUsername(
            pending.username()
        );
        if (
            !principal.isEnabled() || principal.getSessionVersion() != pending.version()
        ) throw new ApiException(
            HttpStatus.UNAUTHORIZED,
            "Cần đăng nhập lại",
            "Tài khoản đã thay đổi."
        );
        // Never persist a password hash in the pending MFA session.
        principal = new ClinicUserPrincipal(
            principal.getId(),
            principal.getUsername(),
            "",
            principal.getFullName(),
            principal.getEmail(),
            principal.getUserType(),
            principal.getSessionVersion(),
            true,
            principal.getAuthorities()
        );
        session.removeAttribute(PENDING);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
            )
        );
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        guard.success(principal.getId());
        audit.record("LOGIN_SUCCESS", "USER", String.valueOf(principal.getId()), null);
        return accounts.current();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        ClinicUserPrincipal principal = SecurityUtils.currentPrincipal();
        audit.record("LOGOUT", "USER", String.valueOf(principal.getId()), principal.getUsername());
        chatSessions.reset(request.getSession(false));
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return accounts.current();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        HttpServletRequest httpRequest
    ) {
        accounts.changePassword(request.currentPassword(), request.newPassword());
        chatSessions.reset(httpRequest.getSession(false));
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
