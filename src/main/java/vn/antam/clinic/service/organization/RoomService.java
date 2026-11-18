package vn.antam.clinic.service.organization;

import org.springframework.data.domain.Pageable;
import vn.antam.clinic.domain.organization.enumeration.RoomType;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.CreateRoomRequest;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.RoomResponse;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.UpdateRoomRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed RoomServiceImpl. */
public interface RoomService {
    public PageResponse<RoomResponse> search(
        String query,
        Long departmentId,
        RoomType roomType,
        Boolean active,
        Pageable pageable
    );

    public RoomResponse get(long id);

    public long countActive();

    public RoomResponse create(CreateRoomRequest request);

    public RoomResponse update(long id, UpdateRoomRequest request);
}
