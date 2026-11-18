package vn.antam.clinic.service.dto.catalog;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OutpatientCatalogDtos {

    private OutpatientCatalogDtos() {}

    public record Specialty(
        long id,
        long departmentId,
        String code,
        String name,
        String description,
        boolean active
    ) {}

    public record SpecialtyInput(
        @NotNull Long departmentId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{2,30}") String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        boolean active
    ) {}

    public record Medication(
        long id,
        String code,
        String name,
        String ingredient,
        String strength,
        String form,
        String unit,
        boolean active,
        int reorderLevel
    ) {}

    public record MedicationInput(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{2,30}") String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String ingredient,
        @NotBlank @Size(max = 100) String strength,
        @NotBlank @Size(max = 100) String form,
        @NotBlank @Size(max = 50) String unit,
        boolean active,
        @jakarta.validation.constraints.Min(0)
        @jakarta.validation.constraints.Max(100000000)
        Integer reorderLevel
    ) {}

    public record Settings(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        @Size(max = 30) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 500) String workingHours,
        Boolean queueDisplayEnabled,
        Boolean queueDisplayMaskNames,
        Boolean remindersEnabled,
        @Min(1) @Max(168) Integer reminderHoursBefore,
        @Min(1) @Max(365) Integer nearExpiryDays
    ) {}
}
