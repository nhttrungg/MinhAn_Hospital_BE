package vn.antam.clinic.service.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import vn.antam.clinic.service.dto.identity.AccountModels.AccountSummary;
import vn.antam.clinic.service.dto.identity.AccountModels.CreateAccountCommand;
import vn.antam.clinic.service.dto.identity.AccountModels.UpdateStaffIdentity;
import vn.antam.clinic.service.dto.identity.AuthDtos.CurrentUserResponse;
import vn.antam.clinic.service.dto.identity.AuthDtos.RoleResponse;

/** Business contract; implemented by the Spring-managed AccountServiceImpl. */
public interface AccountService {
    public AccountSummary create(CreateAccountCommand command);

    public CurrentUserResponse current();

    public void changePassword(String currentPassword, String newPassword);

    public void resetPassword(long userId, String newPassword);

    public AccountSummary updateStaffIdentity(long userId, UpdateStaffIdentity update);

    public Map<Long, AccountSummary> findSummaries(Collection<Long> ids);

    public List<Long> findIdsMatching(String likeQuery);

    public List<Long> findStaffIdsByRole(String role);

    public List<RoleResponse> staffRoles();
}
