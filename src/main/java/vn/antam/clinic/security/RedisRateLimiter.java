package vn.antam.clinic.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimiter {

    private final StringRedisTemplate redis;
    private final String prefix;
    private static final DefaultRedisScript<Long> BUCKET = new DefaultRedisScript<>(
        """
        local t=redis.call('TIME')
        local now=t[1]*1000+math.floor(t[2]/1000)
        local capacity=tonumber(ARGV[1])
        local period=tonumber(ARGV[2])
        local state=redis.call('HMGET',KEYS[1],'tokens','time')
        local tokens=tonumber(state[1]) or capacity
        local previous=tonumber(state[2]) or now
        tokens=math.min(capacity,tokens+math.max(0,now-previous)*capacity/period)
        local accepted=0
        if tokens>=1 then tokens=tokens-1 accepted=1 end
        redis.call('HSET',KEYS[1],'tokens',tokens,'time',now)
        redis.call('PEXPIRE',KEYS[1],period*2)
        return accepted
        """,
        Long.class
    );

    public RedisRateLimiter(
        StringRedisTemplate redis,
        @Value("${app.redis.channel-prefix:antam}") String prefix
    ) {
        this.redis = redis;
        this.prefix = prefix;
    }

    public boolean allow(String scope, String identity, int capacity) {
        try {
            String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    identity.getBytes(StandardCharsets.UTF_8)
                )
            );
            return Long.valueOf(1).equals(
                redis.execute(
                    BUCKET,
                    List.of(prefix + ":rate:" + scope + ":" + hash),
                    Integer.toString(capacity),
                    "60000"
                )
            );
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
