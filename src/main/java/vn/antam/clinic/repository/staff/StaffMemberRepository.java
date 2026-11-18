package vn.antam.clinic.repository.staff;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.antam.clinic.domain.staff.StaffMember;

public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {
    Optional<StaffMember> findByUserId(Long userId);

    boolean existsByEmployeeCode(String employeeCode);

    long countByActiveTrue();

    @Query(
        """
        select s from StaffMember s
        where (:departmentId is null or s.departmentId = :departmentId)
          and (:active is null or s.active = :active)
          and (:filterRole = 0 or s.userId in :roleIds)
          and (:q is null or lower(s.employeeCode) like concat('%', :q, '%') escape '\\'
            or lower(s.title) like concat('%', :q, '%') escape '\\'
            or s.userId in :textIds)
        """
    )
    Page<StaffMember> search(
        @Param("q") String query,
        @Param("textIds") Collection<Long> textIds,
        @Param("departmentId") Long departmentId,
        @Param("active") Boolean active,
        @Param("filterRole") int filterRole,
        @Param("roleIds") Collection<Long> roleIds,
        Pageable pageable
    );
}
