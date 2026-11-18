package vn.antam.clinic.repository.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.antam.clinic.domain.organization.Room;
import vn.antam.clinic.domain.organization.enumeration.RoomType;

public interface RoomRepository extends JpaRepository<Room, Long> {
    boolean existsByDepartment_IdAndActiveTrue(Long departmentId);

    boolean existsByDepartment_IdAndCodeAndIdNot(Long departmentId, String code, Long id);

    boolean existsByDepartment_IdAndCode(Long departmentId, String code);

    long countByActiveTrue();

    @Query(
        """
        select r from Room r
        where (:departmentId is null or r.department.id = :departmentId)
          and (:roomType is null or r.roomType = :roomType)
          and (:active is null or r.active = :active)
          and (:q is null or lower(r.code) like concat('%', :q, '%') escape '\\'
            or lower(r.name) like concat('%', :q, '%') escape '\\'
            or lower(r.department.name) like concat('%', :q, '%') escape '\\')
        """
    )
    Page<Room> search(
        @Param("q") String query,
        @Param("departmentId") Long departmentId,
        @Param("roomType") RoomType roomType,
        @Param("active") Boolean active,
        Pageable pageable
    );
}
