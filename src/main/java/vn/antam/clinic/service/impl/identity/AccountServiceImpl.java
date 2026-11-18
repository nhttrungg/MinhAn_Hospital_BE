package vn.antam.clinic.service.impl.identity;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.domain.identity.Role;
import vn.antam.clinic.domain.identity.UserAccount;
import vn.antam.clinic.domain.identity.enumeration.AccountStatus;
import vn.antam.clinic.domain.identity.enumeration.UserType;
import vn.antam.clinic.repository.identity.RoleRepository;
import vn.antam.clinic.repository.identity.UserAccountRepository;
import vn.antam.clinic.security.Passwords;
import vn.antam.clinic.security.Roles;
import vn.antam.clinic.security.SecurityUtils;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.dto.identity.AccountModels.AccountSummary;
import vn.antam.clinic.service.dto.identity.AccountModels.CreateAccountCommand;
import vn.antam.clinic.service.dto.identity.AccountModels.UpdateStaffIdentity;
import vn.antam.clinic.service.dto.identity.AuthDtos.CurrentUserResponse;
import vn.antam.clinic.service.dto.identity.AuthDtos.RoleResponse;
import vn.antam.clinic.service.identity.AccountService;
import vn.antam.clinic.service.support.Text;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AccountServiceImpl(
        UserAccountRepository users,
        RoleRepository roles,
        PasswordEncoder passwordEncoder,
        AuditService audit
    ) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @PreAuthorize(Roles.HAS_ADMIN)
    public AccountSummary create(CreateAccountCommand command) {
        String username = Text.lower(command.username());
        if (!username.matches("^[a-z0-9._]{3,50}$")) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Tên đăng nhập không hợp lệ",
                "Dùng 3 đến 50 ký tự thường, số, dấu chấm hoặc gạch dưới."
            );
        }
        Passwords.check(command.password());
        String email = normalizeEmail(command.email());
        validateRoleSet(command.userType(), command.roleCodes());
        if (users.existsByUsername(username)) {
            throw conflict("Tên đăng nhập đã được dùng.");
        }
        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw conflict("Email đã được dùng.");
        }
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setFullName(command.fullName().trim());
        user.setEmail(email);
        user.setPhone(Text.blankToNull(command.phone()));
        user.setUserType(command.userType());
        user.setStatus(AccountStatus.ACTIVE);
        user.setRoles(loadRoles(command.roleCodes()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Tên đăng nhập hoặc email đã được dùng.");
        }
        audit.record("CREATE", "USER", String.valueOf(user.getId()), username);
        return summary(user);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(Roles.IS_AUTHENTICATED)
    public CurrentUserResponse current() {
        long id = SecurityUtils.currentPrincipal().getId();
        UserAccount user = users
            .findWithRolesById(id)
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Bạn cần đăng nhập",
                    "Tài khoản không còn tồn tại."
                )
            );
        return new CurrentUserResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.getUserType().name(),
            roleCodes(user)
        );
    }

    @Override
    @PreAuthorize(Roles.IS_AUTHENTICATED)
    public void changePassword(String currentPassword, String newPassword) {
        UserAccount user = users
            .findWithRolesById(SecurityUtils.currentPrincipal().getId())
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Bạn cần đăng nhập",
                    "Tài khoản không còn tồn tại."
                )
            );
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Mật khẩu hiện tại không đúng",
                "Hãy nhập lại mật khẩu đang dùng."
            );
        }
        applyNewPassword(user, newPassword);
        audit.record("CHANGE_PASSWORD", "USER", String.valueOf(user.getId()), user.getUsername());
    }

    @Override
    @PreAuthorize(Roles.HAS_ADMIN)
    public void resetPassword(long userId, String newPassword) {
        UserAccount user = requireUser(userId);
        applyNewPassword(user, newPassword);
        audit.record("RESET_PASSWORD", "USER", String.valueOf(user.getId()), user.getUsername());
    }

    @Override
    @PreAuthorize(Roles.HAS_ADMIN)
    public AccountSummary updateStaffIdentity(long userId, UpdateStaffIdentity update) {
        UserAccount user = requireUser(userId);
        if (user.getUserType() != UserType.STAFF) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Không phải tài khoản nhân viên",
                "Chỉ cập nhật được tài khoản nhân viên."
            );
        }
        String role = update.role().trim().toUpperCase(Locale.ROOT);
        if (!Roles.isStaffRole(role)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Vai trò không hợp lệ",
                "Chọn một vai trò nhân viên."
            );
        }
        boolean remainsActiveAdmin = update.active() && Roles.ADMIN.equals(role);
        ensureAdminRemains(user, remainsActiveAdmin);
        String email = normalizeEmail(update.email());
        if (email == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Thiếu email",
                "Nhân viên cần có email."
            );
        }
        if (users.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw conflict("Email đã được dùng.");
        }
        boolean roleChanged = !user.hasRole(role) || user.getRoles().size() != 1;
        boolean activeChanged = (user.getStatus() == AccountStatus.ACTIVE) != update.active();
        user.setFullName(update.fullName().trim());
        user.setEmail(email);
        user.setPhone(Text.blankToNull(update.phone()));
        user.setRoles(loadRoles(java.util.Set.of(role)));
        user.setStatus(update.active() ? AccountStatus.ACTIVE : AccountStatus.DISABLED);
        if (roleChanged || activeChanged) {
            user.bumpSession();
        }
        audit.record("UPDATE", "USER", String.valueOf(user.getId()), user.getUsername());
        return summary(user);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_STAFF)
    public Map<Long, AccountSummary> findSummaries(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return users
            .findWithRolesByIdIn(ids)
            .stream()
            .map(this::summary)
            .collect(Collectors.toMap(AccountSummary::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_ADMIN)
    public List<Long> findIdsMatching(String likeQuery) {
        if (likeQuery == null) {
            return List.of();
        }
        return users.findIdsMatching(likeQuery);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_ADMIN)
    public List<Long> findStaffIdsByRole(String role) {
        return users.findStaffIdsByRole(role);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_ADMIN)
    public List<RoleResponse> staffRoles() {
        return roles
            .findAll()
            .stream()
            .filter(role -> Roles.isStaffRole(role.getCode()))
            .sorted(Comparator.comparingInt(role -> Roles.staffCodes().indexOf(role.getCode())))
            .map(role -> new RoleResponse(role.getCode(), role.getName()))
            .toList();
    }

    private void applyNewPassword(UserAccount user, String newPassword) {
        Passwords.check(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Mật khẩu mới chưa thay đổi",
                "Hãy chọn mật khẩu khác mật khẩu hiện tại."
            );
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.bumpSession();
    }

    private UserAccount requireUser(long userId) {
        return users
            .findWithRolesById(userId)
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy tài khoản",
                    "Tài khoản không tồn tại."
                )
            );
    }

    private void ensureAdminRemains(UserAccount user, boolean remainsActiveAdmin) {
        boolean currentlyActiveAdmin =
            user.getStatus() == AccountStatus.ACTIVE && user.hasRole(Roles.ADMIN);
        if (
            currentlyActiveAdmin &&
            !remainsActiveAdmin &&
            users.countOtherActiveAdmins(user.getId()) == 0
        ) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "Cần ít nhất một quản trị viên",
                "Không thể khóa hoặc đổi vai trò của quản trị viên cuối cùng."
            );
        }
    }

    private void validateRoleSet(UserType userType, java.util.Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Thiếu vai trò",
                "Tài khoản cần ít nhất một vai trò."
            );
        }
        if (userType == UserType.PATIENT) {
            if (!(roleCodes.size() == 1 && roleCodes.contains(Roles.PATIENT))) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Vai trò không hợp lệ",
                    "Tài khoản bệnh nhân chỉ có vai trò bệnh nhân."
                );
            }
            return;
        }
        if (roleCodes.stream().anyMatch(code -> !Roles.isStaffRole(code))) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Vai trò không hợp lệ",
                "Tài khoản nhân viên không dùng vai trò bệnh nhân."
            );
        }
    }

    private HashSet<Role> loadRoles(java.util.Set<String> codes) {
        List<Role> found = roles.findByCodeIn(codes);
        if (found.size() != codes.size()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Vai trò không hợp lệ",
                "Có vai trò không tồn tại."
            );
        }
        return new HashSet<>(found);
    }

    private AccountSummary summary(UserAccount user) {
        return new AccountSummary(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.getPhone(),
            user.getStatus(),
            roleCodes(user)
        );
    }

    private static List<String> roleCodes(UserAccount user) {
        return user.getRoles().stream().map(Role::getCode).sorted().toList();
    }

    private static String normalizeEmail(String email) {
        String value = Text.blankToNull(email);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Thông tin bị trùng", detail);
    }
}
