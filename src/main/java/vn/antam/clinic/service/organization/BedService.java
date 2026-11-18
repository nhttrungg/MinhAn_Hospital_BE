package vn.antam.clinic.service.organization;

import org.springframework.data.domain.Pageable;
import vn.antam.clinic.domain.organization.enumeration.BedStatus;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.BedResponse;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.CreateBedRequest;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.UpdateBedRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed BedServiceImpl. */
public interface BedService {
    public PageResponse<BedResponse> search(
        String query,
        Long roomId,
        Long departmentId,
        BedStatus status,
        Boolean active,
        Pageable pageable
    );

    public BedResponse get(long id);

    public long countActive();

    public long countAvailable();

    public BedResponse create(CreateBedRequest request);

    public BedResponse update(long id, UpdateBedRequest request);
}
