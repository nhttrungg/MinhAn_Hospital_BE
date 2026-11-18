package vn.antam.clinic.service.impl.organization;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.domain.organization.Department;
import vn.antam.clinic.repository.organization.DepartmentRepository;
import vn.antam.clinic.repository.organization.RoomRepository;
import vn.antam.clinic.security.Roles;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.CreateDepartmentRequest;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.DepartmentRef;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.DepartmentResponse;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.UpdateDepartmentRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.organization.DepartmentService;
import vn.antam.clinic.service.support.SearchText;
import vn.antam.clinic.service.support.Sorts;
import vn.antam.clinic.service.support.Text;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
@Transactional(readOnly = true)
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departments;
    private final RoomRepository rooms;
    private final AuditService audit;

    public DepartmentServiceImpl(
        DepartmentRepository departments,
        RoomRepository rooms,
        AuditService audit
    ) {
        this.departments = departments;
        this.rooms = rooms;
        this.audit = audit;
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    @org.springframework.cache.annotation.Cacheable(cacheNames = "catalog.departments", sync = true)
    public PageResponse<DepartmentResponse> search(
        String query,
        Boolean active,
        Pageable pageable
    ) {
        Page<Department> page = departments.search(
            SearchText.like(query),
            active,
            Sorts.allow(pageable, "code", "name", "createdAt")
        );
        return new PageResponse<>(
            page.getContent().stream().map(this::toResponse).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public DepartmentResponse get(long id) {
        return toResponse(require(id));
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public DepartmentRef getRef(long id) {
        Department department = require(id);
        return new DepartmentRef(
            department.getId(),
            department.getCode(),
            department.getName(),
            department.isActive()
        );
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public Map<Long, String> names(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return departments
            .findAllById(ids)
            .stream()
            .collect(Collectors.toMap(Department::getId, Department::getName));
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public long countActive() {
        return departments.countByActiveTrue();
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public DepartmentResponse create(CreateDepartmentRequest request) {
        String code = Text.upperCode(request.code());
        if (departments.existsByCode(code)) {
            throw conflict("Mã khoa đã tồn tại.");
        }
        Department department = new Department();
        department.setCode(code);
        department.setName(request.name().trim());
        department.setDescription(Text.blankToNull(request.description()));
        department.setActive(request.active());
        departments.save(department);
        audit.record("CREATE", "DEPARTMENT", String.valueOf(department.getId()), code);
        return toResponse(department);
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public DepartmentResponse update(long id, UpdateDepartmentRequest request) {
        Department department = require(id);
        if (
            department.isActive() &&
            !request.active() &&
            rooms.existsByDepartment_IdAndActiveTrue(id)
        ) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "Không thể ngưng khoa",
                "Còn phòng đang hoạt động trong khoa này."
            );
        }
        department.setName(request.name().trim());
        department.setDescription(Text.blankToNull(request.description()));
        department.setActive(request.active());
        audit.record("UPDATE", "DEPARTMENT", String.valueOf(id), department.getCode());
        return toResponse(department);
    }

    private Department require(long id) {
        return departments
            .findById(id)
            .orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khoa", "Khoa không tồn tại.")
            );
    }

    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(
            department.getId(),
            department.getCode(),
            department.getName(),
            department.getDescription(),
            department.isActive(),
            department.getCreatedAt()
        );
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Mã bị trùng", detail);
    }
}
