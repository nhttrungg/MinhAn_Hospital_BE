package vn.antam.clinic.service.impl.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.domain.organization.Department;
import vn.antam.clinic.domain.organization.Room;
import vn.antam.clinic.domain.organization.enumeration.RoomType;
import vn.antam.clinic.repository.organization.BedRepository;
import vn.antam.clinic.repository.organization.DepartmentRepository;
import vn.antam.clinic.repository.organization.RoomRepository;
import vn.antam.clinic.security.Roles;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.CreateRoomRequest;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.RoomResponse;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.UpdateRoomRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.organization.RoomService;
import vn.antam.clinic.service.shared.WorkloadGuard;
import vn.antam.clinic.service.support.SearchText;
import vn.antam.clinic.service.support.Sorts;
import vn.antam.clinic.service.support.Text;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
@Transactional(readOnly = true)
public class RoomServiceImpl implements RoomService {

    private final RoomRepository rooms;
    private final DepartmentRepository departments;
    private final BedRepository beds;
    private final AuditService audit;
    private final WorkloadGuard guard;

    public RoomServiceImpl(
        RoomRepository rooms,
        DepartmentRepository departments,
        BedRepository beds,
        AuditService audit,
        WorkloadGuard guard
    ) {
        this.rooms = rooms;
        this.departments = departments;
        this.beds = beds;
        this.audit = audit;
        this.guard = guard;
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public PageResponse<RoomResponse> search(
        String query,
        Long departmentId,
        RoomType roomType,
        Boolean active,
        Pageable pageable
    ) {
        Page<Room> page = rooms.search(
            SearchText.like(query),
            departmentId,
            roomType,
            active,
            Sorts.allow(pageable, "code", "name", "createdAt")
        );
        return map(page);
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public RoomResponse get(long id) {
        return toResponse(require(id));
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public long countActive() {
        return rooms.countByActiveTrue();
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public RoomResponse create(CreateRoomRequest request) {
        Department department = requireActiveDepartment(request.departmentId());
        String code = Text.upperCode(request.code());
        if (rooms.existsByDepartment_IdAndCode(department.getId(), code)) {
            throw conflict("Mã phòng đã tồn tại trong khoa này.");
        }
        Room room = new Room();
        room.setDepartment(department);
        room.setCode(code);
        room.setDescription(Text.blankToNull(request.description()));
        room.setName(request.name().trim());
        room.setRoomType(request.roomType());
        room.setActive(request.active());
        rooms.save(room);
        audit.record("CREATE", "ROOM", String.valueOf(room.getId()), code);
        return toResponse(room);
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public RoomResponse update(long id, UpdateRoomRequest request) {
        Room room = require(id);
        if (
            !request.active() ||
            request.roomType() != room.getRoomType() ||
            !request.departmentId().equals(room.getDepartment().getId())
        ) guard.check("room", id);
        Department department = requireDepartment(request.departmentId());
        if (request.active() && !department.isActive()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Khoa đang ngưng",
                "Phòng đang dùng cần thuộc khoa đang hoạt động."
            );
        }
        if (
            !room.getDepartment().getId().equals(department.getId()) &&
            rooms.existsByDepartment_IdAndCodeAndIdNot(
                department.getId(),
                room.getCode(),
                room.getId()
            )
        ) {
            throw conflict("Khoa mới đã có phòng cùng mã.");
        }
        boolean leavingWard =
            room.getRoomType() == RoomType.WARD && request.roomType() != RoomType.WARD;
        if (
            (leavingWard || (room.isActive() && !request.active())) &&
            beds.existsByRoom_IdAndActiveTrue(room.getId())
        ) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "Không thể đổi phòng",
                "Còn giường đang hoạt động trong phòng này."
            );
        }
        room.setDepartment(department);
        room.setName(request.name().trim());
        room.setRoomType(request.roomType());
        room.setActive(request.active());
        audit.record("UPDATE", "ROOM", String.valueOf(id), room.getCode());
        if (request.description() != null) room.setDescription(
            Text.blankToNull(request.description())
        );
        return toResponse(room);
    }

    private PageResponse<RoomResponse> map(Page<Room> page) {
        return new PageResponse<>(
            page.getContent().stream().map(this::toResponse).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    private Room require(long id) {
        return rooms
            .findById(id)
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy phòng",
                    "Phòng không tồn tại."
                )
            );
    }

    private Department requireDepartment(long id) {
        return departments
            .findById(id)
            .orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khoa", "Khoa không tồn tại.")
            );
    }

    private Department requireActiveDepartment(long id) {
        Department department = requireDepartment(id);
        if (!department.isActive()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Khoa đang ngưng",
                "Hãy chọn khoa đang hoạt động."
            );
        }
        return department;
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
            room.getId(),
            room.getDepartment().getId(),
            room.getDepartment().getName(),
            room.getCode(),
            room.getName(),
            room.getRoomType(),
            room.getDescription(),
            room.isActive(),
            room.getCreatedAt()
        );
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Mã bị trùng", detail);
    }
}
