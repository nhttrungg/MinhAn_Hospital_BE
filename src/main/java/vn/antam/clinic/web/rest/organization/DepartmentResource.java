package vn.antam.clinic.web.rest.organization;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.CreateDepartmentRequest;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.DepartmentResponse;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.UpdateDepartmentRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.organization.DepartmentService;

@RestController
@RequestMapping("/api/v1/catalog/departments")
public class DepartmentResource {

    private final DepartmentService departments;

    public DepartmentResource(DepartmentService departments) {
        this.departments = departments;
    }

    @GetMapping
    public PageResponse<DepartmentResponse> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Boolean active,
        @PageableDefault(size = 20, sort = "code") Pageable pageable
    ) {
        return departments.search(q, active, pageable);
    }

    @GetMapping("/{id}")
    public DepartmentResponse get(@PathVariable long id) {
        return departments.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse create(@Valid @RequestBody CreateDepartmentRequest request) {
        return departments.create(request);
    }

    @PutMapping("/{id}")
    public DepartmentResponse update(
        @PathVariable long id,
        @Valid @RequestBody UpdateDepartmentRequest request
    ) {
        return departments.update(id, request);
    }
}
