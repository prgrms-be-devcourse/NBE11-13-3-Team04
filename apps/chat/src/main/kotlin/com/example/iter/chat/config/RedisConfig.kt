package com.example.iter.chat.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer

// Pub/Sub 구독에 쓴다(RoomBroadcaster). spring-boot-starter-data-redis-reactive는
// ReactiveStringRedisTemplate까지는 자동 설정해 주지만 이 리스너 컨테이너는 안 만들어 준다.
@Configuration
class RedisConfig {

    @Bean
    fun reactiveRedisMessageListenerContainer(
        connectionFactory: ReactiveRedisConnectionFactory,
    ): ReactiveRedisMessageListenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)
}
