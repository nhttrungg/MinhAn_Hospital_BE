package vn.antam.clinic.service.identity;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/** Business contract; implemented by the Spring-managed ClinicUserDetailsServiceImpl. */
public interface ClinicUserDetailsService extends UserDetailsService {
    public UserDetails loadUserByUsername(String username);
}
