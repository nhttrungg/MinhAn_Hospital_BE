package vn.antam.clinic.service.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import vn.antam.clinic.domain.catalog.ServiceCategory;

public final class CatalogDtos {

    private CatalogDtos() {}

    public record ServiceResponse(
        Long id,
        String code,
        String name,
        ServiceCategory category,
        String unit,
        String description,
        boolean active,
        String unitPrice,
        LocalDate effectiveFrom,
        Instant createdAt
    ) {}

    public record CreateServiceRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{2,30}$", message = "Mã dịch vụ không hợp lệ")
        String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull ServiceCategory category,
        @NotBlank @Size(max = 50) String unit,
        @Size(max = 1500) String description,
        @NotNull Boolean active,
        @NotBlank String unitPrice
    ) {}

    public record UpdateServiceRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull ServiceCategory category,
        @NotBlank @Size(max = 50) String unit,
        @Size(max = 1500) String description,
        @NotNull Boolean active
    ) {}

    public record SetPriceRequest(@NotBlank String unitPrice) {}
}
