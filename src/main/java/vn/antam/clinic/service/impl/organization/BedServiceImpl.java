package vn.antam.clinic.service.impl.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.domain.organization.Bed;
import vn.antam.clinic.domain.organization.Room;
import vn.antam.clinic.domain.organization.enumeration.BedStatus;
import vn.antam.clinic.domain.organization.enumeration.RoomType;
import vn.antam.clinic.repository.organization.BedRepository;
import vn.antam.clinic.repository.organization.RoomRepository;
import vn.antam.clinic.security.Roles;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.BedResponse;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.CreateBedRequest;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.UpdateBedRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.organization.BedService;
import vn.antam.clinic.service.support.SearchText;
import vn.antam.clinic.service.support.Sorts;
import vn.antam.clinic.service.support.Text;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
@Transactional(readOnly = true)
public class BedServiceImpl implements BedService {

    private final BedRepository beds;
    private final RoomRepository rooms;
    private final AuditService audit;

    public BedServiceImpl(BedRepository beds, RoomRepository rooms, AuditService audit) {
        this.beds = beds;
        this.rooms = rooms;
        this.audit = audit;
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public PageResponse<BedResponse> search(
        String query,
        Long roomId,
        Long departmentId,
        BedStatus status,
        Boolean active,
        Pageable pageable
    ) {
        Page<Bed> page = beds.search(
            SearchText.like(query),
            roomId,
            departmentId,
            status,
            active,
            Sorts.allow(pageable, "code", "status", "createdAt")
        );
        return new PageResponse<>(
            page.getContent().stream().map(this::toResponse).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public BedResponse get(long id) {
        return toResponse(require(id));
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public long countActive() {
        return beds.countByActiveTrue();
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public long countAvailable() {
        return beds.countByActiveTrueAndStatus(BedStatus.AVAILABLE);
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public BedResponse create(CreateBedRequest request) {
        Room room = requireWard(request.roomId());
        String code = Text.upperCode(request.code());
        if (beds.existsByRoom_IdAndCode(room.getId(), code)) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "Mã bị trùng",
                "Mã giường đã tồn tại trong phòng này."
            );
        }
        Bed bed = new Bed();
        bed.setRoom(room);
        bed.setCode(code);
        bed.setStatus(request.status());
        bed.setActive(true);
        beds.save(bed);
        audit.record("CREATE", "BED", String.valueOf(bed.getId()), code);
        return toResponse(bed);
    }

    @Override
    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public BedResponse update(long id, UpdateBedRequest request) {
        Bed bed = require(id);
        if (bed.getStatus() == BedStatus.OCCUPIED && !request.active()) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "Không thể ngưng giường",
                "Giường đang có bệnh nhân."
            );
        }
        bed.setStatus(request.status());
        bed.setActive(request.active());
        audit.record("UPDATE", "BED", String.valueOf(id), bed.getCode());
        return toResponse(bed);
    }

    private Bed require(long id) {
        return beds
            .findById(id)
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy giường",
                    "Giường không tồn tại."
                )
            );
    }

    private Room requireWard(long roomId) {
        Room room = rooms
            .findById(roomId)
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy phòng",
                    "Phòng không tồn tại."
                )
            );
        if (!room.isActive()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Phòng đang ngưng",
                "Hãy chọn phòng đang hoạt động."
            );
        }
        if (room.getRoomType() != RoomType.WARD) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Phòng không phải buồng nội trú",
                "Chỉ buồng nội trú mới có giường."
            );
        }
        return room;
    }

    private BedResponse toResponse(Bed bed) {
        Room room = bed.getRoom();
        return new BedResponse(
            bed.getId(),
            room.getId(),
            room.getCode(),
            room.getName(),
            room.getDepartment().getId(),
            room.getDepartment().getName(),
            bed.getCode(),
            bed.getStatus(),
            bed.isActive(),
            bed.getCreatedAt()
        );
    }
}
