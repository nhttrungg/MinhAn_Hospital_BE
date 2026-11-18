package vn.antam.clinic.repository.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.antam.clinic.domain.identity.UserAccount;
import vn.antam.clinic.domain.identity.enumeration.AccountStatus;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findByUsername(String username);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findWithRolesById(@Param("id") long id);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from UserAccount u where u.id in :ids")
    List<UserAccount> findWithRolesByIdIn(@Param("ids") Collection<Long> ids);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query(
        """
        select count(u) from UserAccount u join u.roles r
        where r.code = 'ADMIN'
          and u.status = AccountStatus.ACTIVE
          and u.id <> :userId
        """
    )
    long countOtherActiveAdmins(@Param("userId") long userId);

    @Query(
        """
        select u.id from UserAccount u
        where lower(u.username) like concat('%', :q, '%') escape '\\'
           or lower(u.fullName) like concat('%', :q, '%') escape '\\'
           or lower(coalesce(u.email, '')) like concat('%', :q, '%') escape '\\'
        """
    )
    List<Long> findIdsMatching(@Param("q") String query);

    @Query(
        """
        select u.id from UserAccount u join u.roles r
        where r.code = :code
          and u.userType = vn.antam.clinic.domain.identity.enumeration.UserType.STAFF
        """
    )
    List<Long> findStaffIdsByRole(@Param("code") String code);

    @Query(
        """
        select u.status as status, u.sessionVersion as sessionVersion
        from UserAccount u
        where u.id = :id
        """
    )
    Optional<SessionState> findSessionState(@Param("id") long id);

    interface SessionState {
        AccountStatus getStatus();

        long getSessionVersion();
    }
}
