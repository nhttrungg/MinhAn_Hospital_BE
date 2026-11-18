package vn.antam.clinic.config;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.antam.clinic.security.SessionStateCache;

@Component
public class CacheInvalidation implements MessageListener {

    private static final Set<String> CACHES = Set.of(
        "clinicSettings",
        "catalog.specialties",
        "catalog.departments",
        "catalog.services",
        "doctors.public",
        "availableSlots",
        "icd10.search"
    );
    private final CacheManager manager;
    private final SessionStateCache states;
    private final StringRedisTemplate redis;
    private final String channel;

    public CacheInvalidation(
        CacheManager manager,
        SessionStateCache states,
        StringRedisTemplate redis,
        @Value("${app.redis.channel-prefix:antam}") String prefix
    ) {
        this.manager = manager;
        this.states = states;
        this.redis = redis;
        channel = prefix + ":evict";
    }

    public String channel() {
        return channel;
    }

    public void afterCommit(String name) {
        Runnable action = () -> {
            clear(name);
            try {
                redis.convertAndSend(channel, name);
            } catch (org.springframework.dao.DataAccessException e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "Cache invalidation publication failed; bounded TTL remains in effect"
                );
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
            );
        } else action.run();
    }

    private void clear(String name) {
        if ("users".equals(name)) states.clear();
        else if (CACHES.contains(name)) java.util.Objects.requireNonNull(
            manager.getCache(name)
        ).clear();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        clear(new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
