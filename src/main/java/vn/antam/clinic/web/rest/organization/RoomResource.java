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
import vn.antam.clinic.domain.organization.enumeration.RoomType;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.CreateRoomRequest;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.RoomResponse;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.UpdateRoomRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.organization.RoomService;

@RestController
@RequestMapping("/api/v1/catalog/rooms")
public class RoomResource {

    private final RoomService rooms;

    public RoomResource(RoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping
    public PageResponse<RoomResponse> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) RoomType roomType,
        @RequestParam(required = false) Boolean active,
        @PageableDefault(size = 20, sort = "code") Pageable pageable
    ) {
        return rooms.search(q, departmentId, roomType, active, pageable);
    }

    @GetMapping("/{id}")
    public RoomResponse get(@PathVariable long id) {
        return rooms.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@Valid @RequestBody CreateRoomRequest request) {
        return rooms.create(request);
    }

    @PutMapping("/{id}")
    public RoomResponse update(
        @PathVariable long id,
        @Valid @RequestBody UpdateRoomRequest request
    ) {
        return rooms.update(id, request);
    }
}
