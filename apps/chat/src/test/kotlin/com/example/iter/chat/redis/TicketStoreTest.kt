package com.example.iter.chat.redis

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

@ExtendWith(MockitoExtension::class)
class TicketStoreTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Mock
    private lateinit var valueOperations: ReactiveValueOperations<String, String>

    private val jsonMapper: JsonMapper = JsonMapper.builder().findAndAddModules().build()

    private lateinit var ticketStore: TicketStore

    @Test
    fun `Redis에 값이 있으면 ChatPrincipal로 역직렬화한다`() = runTest {
        ticketStore = TicketStore(redisTemplate, jsonMapper)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.get("chat:ticket:abc"))
            .thenReturn(Mono.just("""{"userId":1,"nickname":"재준"}"""))

        val principal = ticketStore.resolve("abc")

        assertThat(principal?.userId).isEqualTo(1L)
        assertThat(principal?.nickname).isEqualTo("재준")
    }

    @Test
    fun `Redis에 값이 없으면(만료·무효) null을 반환한다`() = runTest {
        ticketStore = TicketStore(redisTemplate, jsonMapper)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.get("chat:ticket:expired")).thenReturn(Mono.empty())

        val principal = ticketStore.resolve("expired")

        assertThat(principal).isNull()
    }
}
