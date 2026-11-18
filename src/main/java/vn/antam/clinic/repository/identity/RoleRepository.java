package vn.antam.clinic.repository.identity;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.antam.clinic.domain.identity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByCodeIn(Collection<String> codes);
}
