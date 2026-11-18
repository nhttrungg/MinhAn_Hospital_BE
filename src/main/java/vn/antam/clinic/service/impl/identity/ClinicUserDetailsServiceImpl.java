package vn.antam.clinic.service.impl.identity;

import java.util.Locale;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.domain.identity.UserAccount;
import vn.antam.clinic.domain.identity.enumeration.AccountStatus;
import vn.antam.clinic.repository.identity.UserAccountRepository;
import vn.antam.clinic.security.ClinicUserPrincipal;
import vn.antam.clinic.service.identity.ClinicUserDetailsService;

@Service
public class ClinicUserDetailsServiceImpl implements ClinicUserDetailsService {

    private final UserAccountRepository users;

    public ClinicUserDetailsServiceImpl(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        UserAccount user = users
            .findByUsername(normalized)
            .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));
        return new ClinicUserPrincipal(
            user.getId(),
            user.getUsername(),
            user.getPasswordHash(),
            user.getFullName(),
            user.getEmail(),
            user.getUserType(),
            user.getSessionVersion(),
            user.getStatus() == AccountStatus.ACTIVE,
            user
                .getRoles()
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                .toList()
        );
    }
}
