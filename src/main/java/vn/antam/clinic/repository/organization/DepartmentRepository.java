package vn.antam.clinic.repository.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.antam.clinic.domain.organization.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByCode(String code);

    long countByActiveTrue();

    @Query(
        """
        select d from Department d
        where (:q is null or lower(d.code) like concat('%', :q, '%') escape '\\'
            or lower(d.name) like concat('%', :q, '%') escape '\\')
          and (:active is null or d.active = :active)
        """
    )
    Page<Department> search(
        @Param("q") String query,
        @Param("active") Boolean active,
        Pageable pageable
    );
}
