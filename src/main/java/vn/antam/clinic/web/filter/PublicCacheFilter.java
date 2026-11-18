package vn.antam.clinic.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PublicCacheFilter extends ShallowEtagHeaderFilter {

    private static final Set<String> PATHS = Set.of(
        "/api/v1/public/clinic",
        "/api/v1/specialties",
        "/api/v1/doctors",
        "/api/v1/slots"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod()) || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        super.doFilterInternal(request, response, (req, res) -> {
            chain.doFilter(req, res);
            HttpServletResponse http = (HttpServletResponse) res;
            http.setHeader(
                "Cache-Control",
                http.getStatus() == 200
                    ? "public, max-age=" +
                          (request.getRequestURI().endsWith("/slots") ? "10" : "30")
                    : "no-store"
            );
        });
    }
}
