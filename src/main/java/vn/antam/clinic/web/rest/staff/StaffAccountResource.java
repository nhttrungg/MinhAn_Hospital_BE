package vn.antam.clinic.web.rest.staff;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.dto.identity.AuthDtos.ResetPasswordRequest;
import vn.antam.clinic.service.dto.identity.AuthDtos.RoleResponse;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.dto.staff.StaffDtos.CreateStaffRequest;
import vn.antam.clinic.service.dto.staff.StaffDtos.StaffResponse;
import vn.antam.clinic.service.dto.staff.StaffDtos.UpdateStaffRequest;
import vn.antam.clinic.service.identity.AccountService;
import vn.antam.clinic.service.staff.StaffAccountService;

@RestController
@RequestMapping("/api/v1/staff-accounts")
public class StaffAccountResource {

    private final StaffAccountService staff;
    private final AccountService accounts;

    public StaffAccountResource(StaffAccountService staff, AccountService accounts) {
        this.staff = staff;
        this.accounts = accounts;
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        return accounts.staffRoles();
    }

    @GetMapping("/me")
    public StaffResponse me() {
        return staff.me();
    }

    @GetMapping
    public PageResponse<StaffResponse> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) Boolean active,
        @PageableDefault(size = 20, sort = "employeeCode") Pageable pageable
    ) {
        return staff.search(q, departmentId, role, active, pageable);
    }

    @GetMapping("/{id}")
    public StaffResponse get(@PathVariable long id) {
        return staff.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@Valid @RequestBody CreateStaffRequest request) {
        return staff.create(request);
    }

    @PutMapping("/{id}")
    public StaffResponse update(
        @PathVariable long id,
        @Valid @RequestBody UpdateStaffRequest request
    ) {
        return staff.update(id, request);
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(
        @PathVariable long id,
        @Valid @RequestBody ResetPasswordRequest request
    ) {
        staff.resetPassword(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
