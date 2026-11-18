package vn.antam.clinic.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ChatLockConfig {

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("clinic-jobs-");
        return scheduler;
    }

    @Bean
    ThreadPoolTaskScheduler chatRenewalScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("clinic-chat-lock-");
        return scheduler;
    }

    @Bean
    RedisLockRegistry chatLocks(
        RedisConnectionFactory connection,
        @Value("${app.redis.channel-prefix:antam}") String prefix,
        @Qualifier("chatRenewalScheduler") ThreadPoolTaskScheduler scheduler
    ) {
        var locks = new RedisLockRegistry(connection, prefix + ":chat-lock", 120000);
        locks.setRenewalTaskScheduler(scheduler);
        return locks;
    }
}
