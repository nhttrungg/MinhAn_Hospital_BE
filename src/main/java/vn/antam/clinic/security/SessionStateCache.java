package vn.antam.clinic.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SessionStateCache {

    public record State(boolean active, long version) {}

    private final JdbcTemplate db;
    private final Cache<Long, Optional<State>> cache;

    public SessionStateCache(
        JdbcTemplate db,
        @Value("${app.cache.session-state-ttl:5s}") Duration ttl
    ) {
        this.db = db;
        cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(ttl)
            .recordStats()
            .build();
    }

    public Optional<State> get(long id) {
        return cache.get(id, key ->
            db
                .query(
                    "select status,session_version from users where id=?",
                    (r, n) -> new State("ACTIVE".equals(r.getString(1)), r.getLong(2)),
                    key
                )
                .stream()
                .findFirst()
        );
    }

    public Map<Long, State> refresh(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, State> states = new HashMap<>();
        db.query(
            "select id,status,session_version from users where id in (" +
                String.join(",", Collections.nCopies(ids.size(), "?")) +
                ")",
            r -> {
                states.put(r.getLong(1), new State("ACTIVE".equals(r.getString(2)), r.getLong(3)));
            },
            ids.toArray()
        );
        ids.forEach(id -> cache.put(id, Optional.ofNullable(states.get(id))));
        return states;
    }

    public void evict(long id) {
        cache.invalidate(id);
    }

    public void clear() {
        cache.invalidateAll();
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }
}
