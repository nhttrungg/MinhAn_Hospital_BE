package vn.antam.clinic.service.organization;

import java.util.Collection;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.CreateDepartmentRequest;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.DepartmentRef;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.DepartmentResponse;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.UpdateDepartmentRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed DepartmentServiceImpl. */
public interface DepartmentService {
    public PageResponse<DepartmentResponse> search(String query, Boolean active, Pageable pageable);

    public DepartmentResponse get(long id);

    public DepartmentRef getRef(long id);

    public Map<Long, String> names(Collection<Long> ids);

    public long countActive();

    public DepartmentResponse create(CreateDepartmentRequest request);

    public DepartmentResponse update(long id, UpdateDepartmentRequest request);
}
