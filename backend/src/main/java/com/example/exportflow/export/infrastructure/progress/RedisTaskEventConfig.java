package com.example.exportflow.export.infrastructure.progress;

import com.example.exportflow.export.web.TaskEventStreamService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
@Profile("!test")
public class RedisTaskEventConfig {

    @Bean
    RedisMessageListenerContainer taskEventListenerContainer(
            RedisConnectionFactory connectionFactory,
            TaskEventStreamService streamService
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) ->
                streamService.broadcast(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(ProgressService.CHANNEL));
        return container;
    }
}
