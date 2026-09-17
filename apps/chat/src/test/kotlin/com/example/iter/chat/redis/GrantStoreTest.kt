package com.example.iter.chat.redis

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

@ExtendWith(MockitoExtension::class)
class GrantStoreTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Mock
    private lateinit var valueOperations: ReactiveValueOperations<String, String>

    private val jsonMapper: JsonMapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `그랜트를 GETDEL로 소비하면 GrantPayload로 역직렬화되고 다시 조회하지 않는다`() = runTest {
        val grantStore = GrantStore(redisTemplate, jsonMapper)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.getAndDelete("chat:grant:tok")).thenReturn(
            Mono.just(
                """{"equipmentId":1,"equipmentName":"드릴","ownerId":10,"ownerNickname":"사장님","requesterId":20,"requesterNickname":"손님"}""",
            ),
        )

        val payload = grantStore.consume("tok")

        assertThat(payload?.equipmentId).isEqualTo(1L)
        assertThat(payload?.ownerNickname).isEqualTo("사장님")
        assertThat(payload?.requesterNickname).isEqualTo("손님")
        // get이 아니라 getAndDelete(GETDEL)로만 접근해야 한다 — 재사용 방지가 핵심이다.
        verify(valueOperations).getAndDelete("chat:grant:tok")
    }

    @Test
    fun `이미 소비됐거나 만료된 토큰이면 null을 반환한다`() = runTest {
        val grantStore = GrantStore(redisTemplate, jsonMapper)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.getAndDelete("chat:grant:used")).thenReturn(Mono.empty())

        val payload = grantStore.consume("used")

        assertThat(payload).isNull()
    }
}
