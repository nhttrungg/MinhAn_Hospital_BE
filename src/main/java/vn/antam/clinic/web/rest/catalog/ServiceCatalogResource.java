package vn.antam.clinic.web.rest.catalog;

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
import vn.antam.clinic.domain.catalog.ServiceCategory;
import vn.antam.clinic.service.catalog.ServiceCatalogService;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.CreateServiceRequest;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.ServiceResponse;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.SetPriceRequest;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.UpdateServiceRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/catalog/services")
public class ServiceCatalogResource {

    private final ServiceCatalogService services;

    public ServiceCatalogResource(ServiceCatalogService services) {
        this.services = services;
    }

    @GetMapping
    public PageResponse<ServiceResponse> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) ServiceCategory category,
        @RequestParam(required = false) Boolean active,
        @PageableDefault(size = 20, sort = "code") Pageable pageable
    ) {
        return services.search(q, category, active, pageable);
    }

    @GetMapping("/{id}")
    public ServiceResponse get(@PathVariable long id) {
        return services.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse create(@Valid @RequestBody CreateServiceRequest request) {
        return services.create(request);
    }

    @PutMapping("/{id}")
    public ServiceResponse update(
        @PathVariable long id,
        @Valid @RequestBody UpdateServiceRequest request
    ) {
        return services.update(id, request);
    }

    @PutMapping("/{id}/price")
    public ServiceResponse setPrice(
        @PathVariable long id,
        @Valid @RequestBody SetPriceRequest request
    ) {
        return services.setPrice(id, request);
    }
}
