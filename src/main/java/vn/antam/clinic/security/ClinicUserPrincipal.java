package vn.antam.clinic.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.antam.clinic.domain.identity.enumeration.UserType;

public class ClinicUserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String username;
    private final String password;
    private final String fullName;
    private final String email;
    private final UserType userType;
    private final long sessionVersion;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public ClinicUserPrincipal(
        long id,
        String username,
        String password,
        String fullName,
        String email,
        UserType userType,
        long sessionVersion,
        boolean enabled,
        Collection<? extends GrantedAuthority> authorities
    ) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.userType = userType;
        this.sessionVersion = sessionVersion;
        this.enabled = enabled;
        this.authorities = List.copyOf(authorities);
    }

    public long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public UserType getUserType() {
        return userType;
    }

    public long getSessionVersion() {
        return sessionVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
