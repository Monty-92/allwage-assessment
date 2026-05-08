package com.allwage.clockin.config;

import com.allwage.clockin.service.RedisEventBus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Redis infrastructure configuration, active only when {@code app.sse.redis-enabled=true}.
 *
 * <p>Sets up a {@link RedisMessageListenerContainer} that subscribes {@link RedisEventBus}
 * to the configured channel. Each JVM instance has its own container and will receive every
 * message published on the channel, forwarding it to the local SSE emitters.
 */
@Configuration
@ConditionalOnProperty(name = "app.sse.redis-enabled", havingValue = "true")
public class RedisConfig {

    /**
     * Listener container that subscribes {@link RedisEventBus} to the clock-events channel.
     * One container per JVM instance — each instance independently consumes all messages.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisEventBus eventBus,
            AppProperties appProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("redis-sse-");
        executor.initialize();

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(executor);
        container.addMessageListener(eventBus,
                new ChannelTopic(appProperties.getSse().getRedisChannel()));
        return container;
    }
}
