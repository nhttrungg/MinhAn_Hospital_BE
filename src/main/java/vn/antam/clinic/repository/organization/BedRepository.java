package vn.antam.clinic.repository.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.antam.clinic.domain.organization.Bed;
import vn.antam.clinic.domain.organization.enumeration.BedStatus;

public interface BedRepository extends JpaRepository<Bed, Long> {
    boolean existsByRoom_IdAndActiveTrue(Long roomId);

    boolean existsByRoom_IdAndCode(Long roomId, String code);

    long countByActiveTrue();

    long countByActiveTrueAndStatus(BedStatus status);

    @Query(
        """
        select b from Bed b
        where (:roomId is null or b.room.id = :roomId)
          and (:departmentId is null or b.room.department.id = :departmentId)
          and (:status is null or b.status = :status)
          and (:active is null or b.active = :active)
          and (:q is null or lower(b.code) like concat('%', :q, '%') escape '\\'
            or lower(b.room.code) like concat('%', :q, '%') escape '\\'
            or lower(b.room.name) like concat('%', :q, '%') escape '\\')
        """
    )
    Page<Bed> search(
        @Param("q") String query,
        @Param("roomId") Long roomId,
        @Param("departmentId") Long departmentId,
        @Param("status") BedStatus status,
        @Param("active") Boolean active,
        Pageable pageable
    );
}
