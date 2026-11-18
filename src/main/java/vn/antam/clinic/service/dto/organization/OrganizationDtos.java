package vn.antam.clinic.service.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import vn.antam.clinic.domain.organization.enumeration.BedStatus;
import vn.antam.clinic.domain.organization.enumeration.RoomType;

public final class OrganizationDtos {

    private OrganizationDtos() {}

    public record DepartmentResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean active,
        Instant createdAt
    ) {}

    public record CreateDepartmentRequest(
        @NotBlank
        @Pattern(
            regexp = "^[A-Za-z0-9-]{2,30}$",
            message = "Mã gồm 2-30 ký tự chữ, số hoặc dấu gạch"
        )
        String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String description,
        @NotNull Boolean active
    ) {}

    public record UpdateDepartmentRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String description,
        @NotNull Boolean active
    ) {}

    public record RoomResponse(
        Long id,
        Long departmentId,
        String departmentName,
        String code,
        String name,
        RoomType roomType,
        String description,
        boolean active,
        Instant createdAt
    ) {}

    public record CreateRoomRequest(
        @NotNull Long departmentId,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{1,30}$", message = "Mã phòng không hợp lệ")
        String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull RoomType roomType,
        @Size(max = 1000) String description,
        @NotNull Boolean active
    ) {}

    public record UpdateRoomRequest(
        @NotNull Long departmentId,
        @NotBlank @Size(max = 200) String name,
        @NotNull RoomType roomType,
        @Size(max = 1000) String description,
        @NotNull Boolean active
    ) {}

    public record BedResponse(
        Long id,
        Long roomId,
        String roomCode,
        String roomName,
        Long departmentId,
        String departmentName,
        String code,
        BedStatus status,
        boolean active,
        Instant createdAt
    ) {}

    public record CreateBedRequest(
        @NotNull Long roomId,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{1,30}$", message = "Mã giường không hợp lệ")
        String code,
        @NotNull BedStatus status
    ) {}

    public record UpdateBedRequest(@NotNull BedStatus status, @NotNull Boolean active) {}

    public record DepartmentRef(Long id, String code, String name, boolean active) {}
}
