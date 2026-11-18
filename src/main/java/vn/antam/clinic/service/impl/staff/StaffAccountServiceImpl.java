package vn.antam.clinic.service.impl.staff;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.domain.identity.enumeration.UserType;
import vn.antam.clinic.domain.staff.StaffMember;
import vn.antam.clinic.repository.staff.StaffMemberRepository;
import vn.antam.clinic.security.Roles;
import vn.antam.clinic.security.SecurityUtils;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.dto.identity.AccountModels.AccountSummary;
import vn.antam.clinic.service.dto.identity.AccountModels.CreateAccountCommand;
import vn.antam.clinic.service.dto.identity.AccountModels.UpdateStaffIdentity;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.DepartmentRef;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.dto.staff.StaffDtos.CreateStaffRequest;
import vn.antam.clinic.service.dto.staff.StaffDtos.StaffResponse;
import vn.antam.clinic.service.dto.staff.StaffDtos.UpdateStaffRequest;
import vn.antam.clinic.service.identity.AccountService;
import vn.antam.clinic.service.organization.DepartmentService;
import vn.antam.clinic.service.staff.DoctorProfileService;
import vn.antam.clinic.service.staff.StaffAccountService;
import vn.antam.clinic.service.support.SearchText;
import vn.antam.clinic.service.support.Sorts;
import vn.antam.clinic.service.support.Text;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
@Transactional(readOnly = true)
public class StaffAccountServiceImpl implements StaffAccountService {

    private final StaffMemberRepository staffMembers;
    private final AccountService accounts;
    private final DepartmentService departments;
    private final AuditService audit;
    private final vn.antam.clinic.service.staff.DoctorProfileService doctorProfiles;

    public StaffAccountServiceImpl(
        StaffMemberRepository staffMembers,
        AccountService accounts,
        DepartmentService departments,
        AuditService audit,
        vn.antam.clinic.service.staff.DoctorProfileService doctorProfiles
    ) {
        this.staffMembers = staffMembers;
        this.accounts = accounts;
        this.departments = departments;
        this.audit = audit;
        this.doctorProfiles = doctorProfiles;
    }

    @Override
    @PreAuthorize(Roles.HAS_ADMIN)
    public PageResponse<StaffResponse> search(
        String query,
        Long departmentId,
        String role,
        Boolean active,
        Pageable pageable
    ) {
        String like = SearchText.like(query);
        List<Long> textIds = like == null ? List.of(-1L) : nonempty(accounts.findIdsMatching(like));
        boolean filterRole = role != null && !role.isBlank();
        List<Long> roleIds = List.of(-1L);
        if (filterRole) {
            String code = role.trim().toUpperCase(Locale.ROOT);
            if (!Roles.isStaffRole(code)) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Vai trò không hợp lệ",
                    "Chọn một vai trò nhân viên."
                );
            }
            roleIds = nonempty(accounts.findStaffIdsByRole(code));
        }
        Page<StaffMember> page = staffMembers.search(
            like,
            textIds,
            departmentId,
            active,
            filterRole ? 1 : 0,
            roleIds,
            Sorts.allow(pageable, "employeeCode", "title", "createdAt")
        );
        Map<Long, AccountSummary> people = accounts.findSummaries(
            page.getContent().stream().map(StaffMember::getUserId).toList()
        );
        Map<Long, String> departmentNames = departments.names(
            page.getContent().stream().map(StaffMember::getDepartmentId).toList()
        );
        return new PageResponse<>(
            page
                .getContent()
                .stream()
                .map(member ->
                    toResponse(
                        member,
                        people.get(member.getUserId()),
                        departmentNames.get(member.getDepartmentId())
                    )
                )
                .toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Override
    @PreAuthorize(Roles.HAS_ADMIN)
    public StaffResponse get(long id) {
        return respond(require(id));
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public StaffResponse me() {
        long userId = SecurityUtils.currentPrincipal().getId();
        StaffMember member = staffMembers
            .findByUserId(userId)
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không có hồ sơ nhân viên",
                    "Tài khoản này chưa gắn hồ sơ nhân viên."
                )
            );
        return respond(member);
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public long countActive() {
        return staffMembers.countByActiveTrue();
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public StaffResponse create(CreateStaffRequest request) {
        String role = request.role().trim().toUpperCase(Locale.ROOT);
        if (!Roles.isStaffRole(role)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Vai trò không hợp lệ",
                "Chọn một vai trò nhân viên."
            );
        }
        DepartmentRef department = departments.getRef(request.departmentId());
        if (!department.active()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Khoa đang ngưng",
                "Hãy chọn khoa đang hoạt động."
            );
        }
        String employeeCode = Text.upperCode(request.employeeCode());
        if (staffMembers.existsByEmployeeCode(employeeCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "Mã bị trùng", "Mã nhân viên đã tồn tại.");
        }
        AccountSummary account = accounts.create(
            new CreateAccountCommand(
                request.username(),
                request.password(),
                request.fullName(),
                request.email(),
                request.phone(),
                UserType.STAFF,
                java.util.Set.of(role)
            )
        );
        StaffMember member = new StaffMember();
        member.setUserId(account.id());
        member.setEmployeeCode(employeeCode);
        member.setDepartmentId(department.id());
        member.setTitle(request.title().trim());
        member.setActive(true);
        staffMembers.saveAndFlush(member);
        doctorProfiles.save(
            member.getId(),
            role,
            request.specialtyId(),
            request.bio(),
            request.photoUrl(),
            request.serviceIds(),
            true,
            request.gender()
        );
        audit.record("CREATE", "STAFF", String.valueOf(member.getId()), employeeCode);
        return toResponse(member, account, department.name());
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public StaffResponse update(long id, UpdateStaffRequest request) {
        StaffMember member = require(id);
        DepartmentRef department = departments.getRef(request.departmentId());
        if (request.active() && !department.active()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Khoa đang ngưng",
                "Nhân viên đang làm việc cần thuộc khoa đang hoạt động."
            );
        }
        doctorProfiles.save(
            id,
            request.role().trim().toUpperCase(Locale.ROOT),
            request.specialtyId(),
            request.bio(),
            request.photoUrl(),
            request.serviceIds(),
            request.active(),
            request.gender()
        );
        AccountSummary account = accounts.updateStaffIdentity(
            member.getUserId(),
            new UpdateStaffIdentity(
                request.fullName(),
                request.email(),
                request.phone(),
                request.role(),
                request.active()
            )
        );
        member.setDepartmentId(department.id());
        member.setTitle(request.title().trim());
        member.setActive(request.active());
        audit.record("UPDATE", "STAFF", String.valueOf(id), member.getEmployeeCode());
        return toResponse(member, account, department.name());
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public void resetPassword(long id, String newPassword) {
        StaffMember member = require(id);
        accounts.resetPassword(member.getUserId(), newPassword);
    }

    private StaffResponse respond(StaffMember member) {
        Map<Long, AccountSummary> people = accounts.findSummaries(List.of(member.getUserId()));
        Map<Long, String> names = departments.names(List.of(member.getDepartmentId()));
        return toResponse(
            member,
            people.get(member.getUserId()),
            names.get(member.getDepartmentId())
        );
    }

    private StaffMember require(long id) {
        return staffMembers
            .findById(id)
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy nhân viên",
                    "Hồ sơ nhân viên không tồn tại."
                )
            );
    }

    private static List<Long> nonempty(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : List.copyOf(ids);
    }

    private StaffResponse toResponse(
        StaffMember member,
        AccountSummary account,
        String departmentName
    ) {
        var profile = doctorProfiles.get(member.getId());
        return new StaffResponse(
            member.getId(),
            member.getUserId(),
            account == null ? null : account.username(),
            account == null ? null : account.fullName(),
            account == null ? null : account.email(),
            account == null ? null : account.phone(),
            account == null ? List.of() : account.roles(),
            member.getEmployeeCode(),
            member.getDepartmentId(),
            departmentName,
            member.getTitle(),
            account == null ? null : account.status().name(),
            member.isActive(),
            member.getCreatedAt(),
            profile.specialtyId(),
            profile.bio(),
            profile.photoUrl(),
            profile.serviceIds(),
            profile.gender()
        );
    }
}
