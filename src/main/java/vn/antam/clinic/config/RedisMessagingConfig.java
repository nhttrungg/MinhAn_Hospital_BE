package vn.antam.clinic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisMessagingConfig {

    @Bean
    RedisMessageListenerContainer redisListeners(
        RedisConnectionFactory connection,
        CacheInvalidation invalidation,
        vn.antam.clinic.service.event.RealtimeBroadcaster events
    ) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connection);
        container.addMessageListener(invalidation, new ChannelTopic(invalidation.channel()));
        container.addMessageListener(events, new ChannelTopic(events.channel()));
        return container;
    }
}
