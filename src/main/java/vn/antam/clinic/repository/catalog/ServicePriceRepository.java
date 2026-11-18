package vn.antam.clinic.repository.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.antam.clinic.domain.catalog.ServicePrice;

public interface ServicePriceRepository extends JpaRepository<ServicePrice, Long> {
    Optional<ServicePrice> findByService_IdAndEffectiveToIsNull(Long serviceId);

    @Query("select p from ServicePrice p where p.service.id in :ids and p.effectiveTo is null")
    List<ServicePrice> findOpenByServiceIds(@Param("ids") Collection<Long> serviceIds);
}
