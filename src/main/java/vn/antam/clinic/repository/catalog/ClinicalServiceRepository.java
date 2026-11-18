package vn.antam.clinic.repository.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.antam.clinic.domain.catalog.ClinicalService;
import vn.antam.clinic.domain.catalog.ServiceCategory;

public interface ClinicalServiceRepository extends JpaRepository<ClinicalService, Long> {
    boolean existsByCode(String code);

    long countByActiveTrue();

    @Query(
        """
        select s from ClinicalService s
        where (:category is null or s.category = :category)
          and (:active is null or s.active = :active)
          and (:q is null or lower(s.code) like concat('%', :q, '%') escape '\\'
            or lower(s.name) like concat('%', :q, '%') escape '\\')
        """
    )
    Page<ClinicalService> search(
        @Param("q") String query,
        @Param("category") ServiceCategory category,
        @Param("active") Boolean active,
        Pageable pageable
    );
}
