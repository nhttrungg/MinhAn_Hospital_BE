package vn.antam.clinic.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.antam.clinic.web.rest.errors.ApiProblemWriter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter limiter;
    private final ApiProblemWriter problems;
    private final boolean enabled;

    public RateLimitFilter(
        RedisRateLimiter limiter,
        ApiProblemWriter problems,
        @Value("${app.security.rate-limit-enabled:true}") boolean enabled
    ) {
        this.limiter = limiter;
        this.problems = problems;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return (
            !enabled ||
            !request.getRequestURI().startsWith("/api/v1/") ||
            request.getRequestURI().equals("/api/v1/public/payments/webhook")
        );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        String path = request.getRequestURI(),
            ip = request.getRemoteAddr();
        try {
            boolean accepted = true;
            if (path.equals("/api/v1/auth/login")) accepted = limiter.allow("login-ip", ip, 10);
            else if (
                path.startsWith("/api/v1/public/") ||
                java.util.Set.of(
                    "/api/v1/slots",
                    "/api/v1/doctors",
                    "/api/v1/specialties"
                ).contains(path)
            ) accepted = limiter.allow("public-ip", ip, 120);
            else {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof ClinicUserPrincipal p) accepted =
                    limiter.allow("user", Long.toString(p.getId()), 300);
            }
            if (!accepted) {
                response.setHeader("Retry-After", "60");
                problems.write(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Quá nhiều yêu cầu",
                    "Vui lòng chờ một phút rồi thử lại."
                );
                return;
            }
        } catch (org.springframework.dao.DataAccessException unavailable) {
            response.setHeader("Retry-After", "2");
            problems.write(
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Hệ thống đang bận",
                "Vui lòng thử lại sau vài giây."
            );
            return;
        }
        chain.doFilter(request, response);
    }
}
