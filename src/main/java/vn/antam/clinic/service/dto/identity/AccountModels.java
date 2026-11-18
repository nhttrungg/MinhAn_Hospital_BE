package vn.antam.clinic.service.dto.identity;

import java.util.List;
import java.util.Set;
import vn.antam.clinic.domain.identity.enumeration.AccountStatus;
import vn.antam.clinic.domain.identity.enumeration.UserType;

public final class AccountModels {

    private AccountModels() {}

    public record CreateAccountCommand(
        String username,
        String password,
        String fullName,
        String email,
        String phone,
        UserType userType,
        Set<String> roleCodes
    ) {}

    public record UpdateStaffIdentity(
        String fullName,
        String email,
        String phone,
        String role,
        boolean active
    ) {}

    public record AccountSummary(
        long id,
        String username,
        String fullName,
        String email,
        String phone,
        AccountStatus status,
        List<String> roles
    ) {}
}
