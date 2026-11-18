package vn.antam.clinic.service.impl.identity;

import static vn.antam.clinic.security.AuthenticationErrors.*;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.identity.LoginGuard;

@Service
public class LoginGuardImpl implements LoginGuard {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LoginGuardImpl(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void check(String username) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update(
            "update users set failed_login_count=0,locked_until=null where username=? and locked_until<=?",
            username,
            now
        );
        Boolean locked = jdbc.query(
            "select locked_until from users where username=?",
            rs -> rs.next() && rs.getTimestamp(1) != null && rs.getTimestamp(1).after(now),
            username
        );
        if (Boolean.TRUE.equals(locked)) throw locked();
    }

    @Override
    @Transactional
    public boolean failure(String username) {
        // MySQL assignments are evaluated left-to-right: inspect old count before incrementing.
        jdbc.update(
            "update users set locked_until=case when failed_login_count>=4 then ? else locked_until end,failed_login_count=least(failed_login_count+1,5) where username=? and (locked_until is null or locked_until<=?)",
            Timestamp.from(clock.instant().plusSeconds(900)),
            username,
            Timestamp.from(clock.instant())
        );
        return Boolean.TRUE.equals(
            jdbc.query(
                "select locked_until from users where username=?",
                rs ->
                    rs.next() &&
                    rs.getTimestamp(1) != null &&
                    rs.getTimestamp(1).toInstant().isAfter(clock.instant()),
                username
            )
        );
    }

    @Override
    public void success(long userId) {
        jdbc.update("update users set failed_login_count=0,locked_until=null where id=?", userId);
    }
}
