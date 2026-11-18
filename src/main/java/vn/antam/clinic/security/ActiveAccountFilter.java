package vn.antam.clinic.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.antam.clinic.web.rest.errors.ApiProblemWriter;

@Component
public class ActiveAccountFilter extends OncePerRequestFilter {

    private final SessionStateCache users;
    private final ApiProblemWriter problems;

    public ActiveAccountFilter(SessionStateCache users, ApiProblemWriter problems) {
        this.users = users;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
            authentication != null &&
            authentication.getPrincipal() instanceof ClinicUserPrincipal principal
        ) {
            var state = users.get(principal.getId());
            boolean valid =
                state.isPresent() &&
                state.get().active() &&
                state.get().version() == principal.getSessionVersion();
            if (!valid) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                problems.write(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Phiên đăng nhập đã kết thúc",
                    "Hãy đăng nhập lại."
                );
                return;
            }
        }
        try {
            if (
                authentication != null &&
                authentication.getPrincipal() instanceof ClinicUserPrincipal principal
            ) {
                org.slf4j.MDC.put("userId", Long.toString(principal.getId()));
            }
            filterChain.doFilter(request, response);
        } finally {
            org.slf4j.MDC.remove("userId");
        }
    }
}
