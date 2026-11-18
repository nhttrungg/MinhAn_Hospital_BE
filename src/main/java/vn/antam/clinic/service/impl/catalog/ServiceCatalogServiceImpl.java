package vn.antam.clinic.service.impl.catalog;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.domain.catalog.ClinicalService;
import vn.antam.clinic.domain.catalog.ServiceCategory;
import vn.antam.clinic.domain.catalog.ServicePrice;
import vn.antam.clinic.repository.catalog.ClinicalServiceRepository;
import vn.antam.clinic.repository.catalog.ServicePriceRepository;
import vn.antam.clinic.security.Roles;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.catalog.ServiceCatalogService;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.CreateServiceRequest;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.ServiceResponse;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.SetPriceRequest;
import vn.antam.clinic.service.dto.catalog.CatalogDtos.UpdateServiceRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.shared.WorkloadGuard;
import vn.antam.clinic.service.support.Money;
import vn.antam.clinic.service.support.SearchText;
import vn.antam.clinic.service.support.Sorts;
import vn.antam.clinic.service.support.Text;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
@Transactional(readOnly = true)
public class ServiceCatalogServiceImpl implements ServiceCatalogService {

    private final ClinicalServiceRepository services;
    private final ServicePriceRepository prices;
    private final AuditService audit;
    private final Clock clock;
    private final WorkloadGuard guard;

    public ServiceCatalogServiceImpl(
        ClinicalServiceRepository services,
        ServicePriceRepository prices,
        AuditService audit,
        Clock clock,
        WorkloadGuard guard
    ) {
        this.services = services;
        this.prices = prices;
        this.audit = audit;
        this.clock = clock;
        this.guard = guard;
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    @org.springframework.cache.annotation.Cacheable(cacheNames = "catalog.services", sync = true)
    public PageResponse<ServiceResponse> search(
        String query,
        ServiceCategory category,
        Boolean active,
        Pageable pageable
    ) {
        Page<ClinicalService> page = services.search(
            SearchText.like(query),
            category,
            active,
            Sorts.allow(pageable, "code", "name", "category", "createdAt")
        );
        Map<Long, ServicePrice> openPrices = openPrices(page.getContent());
        return new PageResponse<>(
            page
                .getContent()
                .stream()
                .map(service -> toResponse(service, openPrices.get(service.getId())))
                .toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public ServiceResponse get(long id) {
        ClinicalService service = require(id);
        return toResponse(service, currentPrice(service.getId()));
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public long countActive() {
        return services.countByActiveTrue();
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public ServiceResponse create(CreateServiceRequest request) {
        String code = Text.upperCode(request.code());
        if (services.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Mã bị trùng", "Mã dịch vụ đã tồn tại.");
        }
        ClinicalService service = new ClinicalService();
        service.setCode(code);
        service.setDescription(Text.blankToNull(request.description()));
        service.setName(request.name().trim());
        service.setCategory(request.category());
        service.setUnit(request.unit().trim());
        service.setActive(request.active());
        services.save(service);
        ServicePrice price = newPrice(
            service,
            Money.parseVnd(request.unitPrice()),
            LocalDate.now(clock)
        );
        prices.save(price);
        audit.record("CREATE", "SERVICE", String.valueOf(service.getId()), code);
        return toResponse(service, price);
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public ServiceResponse update(long id, UpdateServiceRequest request) {
        ClinicalService service = require(id);
        if (!request.active() || request.category() != service.getCategory()) guard.check(
            "service",
            id
        );
        service.setName(request.name().trim());
        service.setCategory(request.category());
        service.setUnit(request.unit().trim());
        service.setActive(request.active());
        audit.record("UPDATE", "SERVICE", String.valueOf(id), service.getCode());
        if (request.description() != null) service.setDescription(
            Text.blankToNull(request.description())
        );
        return toResponse(service, currentPrice(id));
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public ServiceResponse setPrice(long id, SetPriceRequest request) {
        ClinicalService service = require(id);
        if (!service.isActive()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Dịch vụ đang ngưng",
                "Chỉ đặt giá cho dịch vụ đang hoạt động."
            );
        }
        BigDecimal amount = Money.parseVnd(request.unitPrice());
        ServicePrice current = prices
            .findByService_IdAndEffectiveToIsNull(id)
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.CONFLICT,
                    "Chưa có giá",
                    "Dịch vụ chưa có đơn giá hiệu lực."
                )
            );
        if (current.getUnitPrice().compareTo(amount) == 0) {
            return toResponse(service, current);
        }
        LocalDate today = LocalDate.now(clock);
        ServicePrice visible = current;
        if (current.getEffectiveFrom().isBefore(today)) {
            current.setEffectiveTo(today.minusDays(1));
            visible = newPrice(service, amount, today);
            prices.save(visible);
        } else {
            current.setUnitPrice(amount);
        }
        audit.record("SET_PRICE", "SERVICE", String.valueOf(id), Money.format(amount));
        return toResponse(service, visible);
    }

    private Map<Long, ServicePrice> openPrices(List<ClinicalService> content) {
        if (content.isEmpty()) {
            return Map.of();
        }
        return prices
            .findOpenByServiceIds(content.stream().map(ClinicalService::getId).toList())
            .stream()
            .collect(Collectors.toMap(price -> price.getService().getId(), Function.identity()));
    }

    private ServicePrice currentPrice(long serviceId) {
        return prices.findByService_IdAndEffectiveToIsNull(serviceId).orElse(null);
    }

    private ClinicalService require(long id) {
        return services
            .findById(id)
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy dịch vụ",
                    "Dịch vụ không tồn tại."
                )
            );
    }

    private static ServicePrice newPrice(
        ClinicalService service,
        BigDecimal amount,
        LocalDate from
    ) {
        ServicePrice price = new ServicePrice();
        price.setService(service);
        price.setUnitPrice(amount);
        price.setEffectiveFrom(from);
        return price;
    }

    private static ServiceResponse toResponse(ClinicalService service, ServicePrice price) {
        return new ServiceResponse(
            service.getId(),
            service.getCode(),
            service.getName(),
            service.getCategory(),
            service.getUnit(),
            service.getDescription(),
            service.isActive(),
            price == null ? null : Money.format(price.getUnitPrice()),
            price == null ? null : price.getEffectiveFrom(),
            service.getCreatedAt()
        );
    }
}
