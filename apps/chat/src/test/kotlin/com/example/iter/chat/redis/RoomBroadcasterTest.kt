package com.example.iter.chat.redis

import com.example.iter.chat.ws.SessionRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Mono

@ExtendWith(MockitoExtension::class)
class RoomBroadcasterTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Mock
    private lateinit var listenerContainer: ReactiveRedisMessageListenerContainer

    @Mock
    private lateinit var sessionRegistry: SessionRegistry

    @InjectMocks
    private lateinit var roomBroadcaster: RoomBroadcaster

    @Test
    fun `publish는 chat_room_{roomId} 채널로 발행한다`() = runTest {
        whenever(redisTemplate.convertAndSend("chat:room:42", "payload")).thenReturn(Mono.just(1L))

        roomBroadcaster.publish(42L, "payload")

        verify(redisTemplate).convertAndSend("chat:room:42", "payload")
    }
}
