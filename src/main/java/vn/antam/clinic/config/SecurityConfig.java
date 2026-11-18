package vn.antam.clinic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import vn.antam.clinic.security.ActiveAccountFilter;
import vn.antam.clinic.security.SpaCsrfTokenRequestHandler;
import vn.antam.clinic.web.rest.errors.ApiProblemWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] STAFF = {
        "ADMIN",
        "RECEPTIONIST",
        "DOCTOR",
        "NURSE",
        "LAB_TECH",
        "PHARMACIST",
        "CASHIER",
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new vn.antam.clinic.security.BoundedPasswordEncoder(
            new BCryptPasswordEncoder(12),
            Runtime.getRuntime().availableProcessors() * 2
        );
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
        throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CookieSerializer cookieSerializer(
        @Value("${app.security.cookie-secure:false}") boolean secure
    ) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("ANTAM_SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        serializer.setCookiePath("/");
        return serializer;
    }

    @Bean
    public FilterRegistrationBean<ActiveAccountFilter> activeAccountFilterRegistration(
        ActiveAccountFilter filter
    ) {
        FilterRegistrationBean<ActiveAccountFilter> registration = new FilterRegistrationBean<>(
            filter
        );
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SecurityContextRepository securityContextRepository,
        ActiveAccountFilter activeAccountFilter,
        vn.antam.clinic.security.RateLimitFilter rateLimitFilter,
        ApiProblemWriter problems,
        @Value("${management.server.port:9090}") int managementPort,
        @Value("${app.security.cookie-secure:false}") boolean secure
    ) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        csrfRepository.setCookieCustomizer(cookie ->
            cookie.sameSite("Lax").secure(secure).httpOnly(false).path("/")
        );
        http.csrf(csrf ->
            csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                .ignoringRequestMatchers("/api/v1/public/payments/webhook")
        )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .securityContext(context ->
                context.securityContextRepository(securityContextRepository)
            )
            .exceptionHandling(handler ->
                handler
                    .authenticationEntryPoint((request, response, exception) ->
                        problems.write(
                            response,
                            HttpStatus.UNAUTHORIZED,
                            "Bạn cần đăng nhập",
                            "Phiên đăng nhập không còn hiệu lực."
                        )
                    )
                    .accessDeniedHandler((request, response, exception) -> {
                        if (exception instanceof CsrfException) {
                            problems.write(
                                response,
                                HttpStatus.FORBIDDEN,
                                "Thiếu mã CSRF",
                                "Hãy tải lại trang rồi thử lại."
                            );
                            return;
                        }
                        problems.write(
                            response,
                            HttpStatus.FORBIDDEN,
                            "Không có quyền",
                            "Bạn không có quyền thực hiện thao tác này."
                        );
                    })
            )
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers(
                        request ->
                            request.getLocalPort() == managementPort &&
                            (request.getRequestURI().equals("/actuator/prometheus") ||
                                request.getRequestURI().equals("/actuator/metrics") ||
                                request.getRequestURI().startsWith("/actuator/metrics/"))
                    )
                    .permitAll()
                    .requestMatchers(
                        "/actuator/prometheus",
                        "/actuator/metrics",
                        "/actuator/metrics/**"
                    )
                    .denyAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**"
                    )
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/mfa/verify",
                        "/api/v1/public/payments/webhook"
                    )
                    .permitAll()
                    .requestMatchers("/api/v1/auth/mfa/**", "/api/v1/realtime/stream")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/public/queue-display",
                        "/api/v1/public/queue-display/**",
                        "/api/v1/doctors/*/reviews"
                    )
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/queue/*/call")
                    .hasRole("DOCTOR")
                    .requestMatchers(HttpMethod.GET, "/api/v1/queue/*/call-state")
                    .hasRole("DOCTOR")
                    .requestMatchers(
                        "/api/v1/waitlist",
                        "/api/v1/waitlist/**",
                        "/api/v1/patients/me/family",
                        "/api/v1/patients/me/family/**",
                        "/api/v1/encounters/*/review"
                    )
                    .hasRole("PATIENT")
                    .requestMatchers("/api/v1/reviews", "/api/v1/reviews/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        "/api/v1/patients/*/allergies",
                        "/api/v1/patients/*/allergies/**"
                    )
                    .hasAnyRole("PATIENT", "RECEPTIONIST", "DOCTOR", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/payments/mismatches")
                    .hasAnyRole("CASHIER", "ADMIN")
                    .requestMatchers(
                        "/api/v1/billing/invoices/*/payment-intents",
                        "/api/v1/payment-intents/**"
                    )
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/activate",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password"
                    )
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/public/clinic",
                        "/api/v1/specialties",
                        "/api/v1/doctors",
                        "/api/v1/slots"
                    )
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/public/knowledge",
                        "/api/v1/public/knowledge/*"
                    )
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/public/chat/messages")
                    .permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/public/chat/session")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/admin/chatbot/**",
                        "/api/v1/admin/knowledge",
                        "/api/v1/admin/knowledge/**"
                    )
                    .hasRole("ADMIN")
                    .requestMatchers(
                        "/api/v1/auth/me",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/change-password"
                    )
                    .authenticated()
                    .requestMatchers("/api/v1/schedules/absences", "/api/v1/schedules/absences/**")
                    .hasAnyRole("ADMIN", "RECEPTIONIST")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/reports/capacity",
                        "/api/v1/reports/capacity.csv"
                    )
                    .hasAnyRole("ADMIN", "DOCTOR")
                    .requestMatchers(HttpMethod.GET, "/api/v1/schedules/my-slots")
                    .hasAnyRole("DOCTOR", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/schedules/slots")
                    .hasRole("DOCTOR")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/schedules/slots/*/submit",
                        "/api/v1/schedules/slots/*/request-close"
                    )
                    .hasRole("DOCTOR")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/schedules/slots/*/approve",
                        "/api/v1/schedules/slots/*/reject",
                        "/api/v1/schedules/slots/*/approve-close"
                    )
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/schedules/templates")
                    .hasRole("DOCTOR")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/schedules/templates",
                        "/api/v1/schedules/templates/*/generate",
                        "/api/v1/schedules/slots/*/copy"
                    )
                    .hasRole("DOCTOR")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/schedules/slots/*/affected-appointments"
                    )
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/appointments/me")
                    .hasRole("PATIENT")
                    .requestMatchers(HttpMethod.GET, "/api/v1/appointments")
                    .hasRole("RECEPTIONIST")
                    .requestMatchers(HttpMethod.GET, "/api/v1/appointments/*")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/appointments",
                        "/api/v1/appointments/*/reschedule",
                        "/api/v1/appointments/*/cancel"
                    )
                    .hasAnyRole("PATIENT", "RECEPTIONIST")
                    .requestMatchers(HttpMethod.POST, "/api/v1/appointments/*/check-in")
                    .hasRole("RECEPTIONIST")
                    .requestMatchers(HttpMethod.POST, "/api/v1/appointments/*/no-show")
                    .hasRole("RECEPTIONIST")
                    .requestMatchers(HttpMethod.GET, "/api/v1/queue/doctor/me")
                    .hasRole("DOCTOR")
                    .requestMatchers(HttpMethod.GET, "/api/v1/patients/me")
                    .hasRole("PATIENT")
                    .requestMatchers(HttpMethod.GET, "/api/v1/patients", "/api/v1/patients/*")
                    .hasAnyRole("PATIENT", "RECEPTIONIST", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/patients")
                    .hasRole("RECEPTIONIST")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/patients/*")
                    .hasAnyRole("PATIENT", "RECEPTIONIST")
                    .requestMatchers("/api/v1/email-tasks", "/api/v1/email-tasks/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/billing/invoices/me")
                    .hasRole("PATIENT")
                    .requestMatchers(HttpMethod.GET, "/api/v1/billing/invoices")
                    .hasRole("CASHIER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/billing/invoices/*",
                        "/api/v1/billing/appointments/*/invoice"
                    )
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/billing/invoices/*/payments")
                    .hasRole("CASHIER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/encounters/appointments/*",
                        "/api/v1/encounters/*"
                    )
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/encounters/appointments/*/start",
                        "/api/v1/encounters/*/wait-results",
                        "/api/v1/encounters/*/complete",
                        "/api/v1/encounters/*/assign-nurse",
                        "/api/v1/encounters/*/amendments"
                    )
                    .hasRole("DOCTOR")
                    .requestMatchers("/api/v1/clinical-templates", "/api/v1/clinical-templates/**")
                    .hasAnyRole("ADMIN", "DOCTOR")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/encounters/*/patient-summary",
                        "/api/v1/encounters/*/diagnostic-history"
                    )
                    .hasRole("DOCTOR")
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/api/v1/encounters/*/draft",
                        "/api/v1/encounters/*/draft-sync"
                    )
                    .hasRole("DOCTOR")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/encounters/*/vitals")
                    .hasRole("NURSE")
                    .requestMatchers(HttpMethod.GET, "/api/v1/staff-accounts/me")
                    .hasAnyRole(STAFF)
                    .requestMatchers("/api/v1/staff-accounts", "/api/v1/staff-accounts/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**")
                    .hasAnyRole(STAFF)
                    .requestMatchers("/api/v1/catalog/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/dashboard/**")
                    .hasAnyRole(STAFF)
                    .requestMatchers(HttpMethod.GET, "/api/v1/schedules/search")
                    .hasAnyRole("ADMIN", "DOCTOR")
                    .requestMatchers(HttpMethod.GET, "/api/v1/schedules/doctors")
                    .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR")
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/api/v1/schedules/slots/*",
                        "/api/v1/schedules/templates/*"
                    )
                    .hasRole("DOCTOR")
                    .requestMatchers(
                        HttpMethod.DELETE,
                        "/api/v1/schedules/slots/*",
                        "/api/v1/schedules/templates/*"
                    )
                    .hasRole("DOCTOR")
                    .requestMatchers(HttpMethod.POST, "/api/v1/schedules/slots/*/reject-close")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/appointments/*/history",
                        "/api/v1/billing/search",
                        "/api/v1/billing/receipts/*",
                        "/api/v1/prescriptions/**"
                    )
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/prescriptions/**")
                    .hasRole("DOCTOR")
                    .requestMatchers(HttpMethod.POST, "/api/v1/prescriptions/**")
                    .hasRole("DOCTOR")
                    .requestMatchers(
                        "/api/v1/clinic-settings",
                        "/api/v1/clinic-settings/**",
                        "/api/v1/reports/**",
                        "/api/v1/audit-logs"
                    )
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/notifications", "/api/v1/notifications/*/read")
                    .authenticated()
                    .requestMatchers(
                        "/api/v1/pharmacy/**",
                        "/api/v1/diagnostics/**",
                        "/api/v1/documents",
                        "/api/v1/documents/**",
                        "/api/v1/qr/**",
                        "/api/v1/appointments/*/qr",
                        "/api/v1/billing/invoices/*/workflow",
                        "/api/v1/billing/invoices/*/payment-qr",
                        "/api/v1/billing/invoices/*/refunds"
                    )
                    .authenticated()
                    .anyRequest()
                    .denyAll()
            )
            .logout(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .addFilterAfter(activeAccountFilter, SecurityContextHolderFilter.class)
            .addFilterAfter(rateLimitFilter, ActiveAccountFilter.class);
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<vn.antam.clinic.security.RateLimitFilter> rateLimitRegistration(
        vn.antam.clinic.security.RateLimitFilter filter
    ) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
