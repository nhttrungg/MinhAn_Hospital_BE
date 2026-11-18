package vn.antam.clinic.service.catalog;

import org.springframework.data.domain.Pageable;
import vn.antam.clinic.domain.catalog.ServiceCategory;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.CreateServiceRequest;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.ServiceResponse;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.SetPriceRequest;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.UpdateServiceRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed ServiceCatalogServiceImpl. */
public interface ServiceCatalogService {
    public PageResponse<ServiceResponse> search(
        String query,
        ServiceCategory category,
        Boolean active,
        Pageable pageable
    );

    public ServiceResponse get(long id);

    public long countActive();

    public ServiceResponse create(CreateServiceRequest request);

    public ServiceResponse update(long id, UpdateServiceRequest request);

    public ServiceResponse setPrice(long id, SetPriceRequest request);
}
