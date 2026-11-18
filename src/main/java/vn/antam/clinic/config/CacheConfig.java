package vn.antam.clinic.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    CacheManager cacheManager(
        @org.springframework.beans.factory.annotation.Value(
            "${app.cache.enabled:true}"
        ) boolean enabled
    ) {
        if (!enabled) return new org.springframework.cache.support.NoOpCacheManager();
        var manager = new SimpleCacheManager();
        manager.setCaches(
            java.util.List.of(
                cache("clinicSettings", Duration.ofMinutes(10)),
                cache("catalog.specialties", Duration.ofMinutes(5)),
                cache("catalog.departments", Duration.ofMinutes(5)),
                cache("catalog.services", Duration.ofMinutes(5)),
                cache("doctors.public", Duration.ofMinutes(5)),
                cache("availableSlots", Duration.ofSeconds(10)),
                cache("icd10.search", Duration.ofHours(1))
            )
        );
        return manager;
    }

    private CaffeineCache cache(String name, Duration ttl) {
        return new CaffeineCache(
            name,
            Caffeine.newBuilder().maximumSize(2000).expireAfterWrite(ttl).recordStats().build(),
            false
        );
    }
}
