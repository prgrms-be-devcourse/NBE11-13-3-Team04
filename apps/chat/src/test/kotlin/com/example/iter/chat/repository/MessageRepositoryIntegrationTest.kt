package com.example.iter.chat.repository

import com.example.iter.chat.domain.Message
import com.example.iter.chat.domain.MessageType
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

// 안읽음 수 계산은 SQL의 NULL 의미론에 걸려 있어서(sender_id IS NULL인 SYSTEM 메시지)
// 목으로는 검증이 안 된다 — 실제 DB에 넣고 세어봐야 한다.
// 컨테이너/태그 구성은 ChatRoomRepositoryIntegrationTest 주석 참고.
@Testcontainers
@SpringBootTest
@Tag("integration")
class MessageRepositoryIntegrationTest {

    @Autowired
    lateinit var messageRepository: MessageRepository

    @Test
    fun `내가 보낸 메시지는 안읽음에서 빠지고 상대가 보낸 것만 센다`() = runTest {
        val roomId = 900L
        save(roomId, senderId = ME, content = "내가 보낸 것")
        save(roomId, senderId = COUNTERPART, content = "상대가 보낸 것 1")
        save(roomId, senderId = COUNTERPART, content = "상대가 보낸 것 2")

        assertThat(messageRepository.countUnreadAll(roomId, ME)).isEqualTo(2)
        assertThat(messageRepository.countUnreadAll(roomId, COUNTERPART)).isEqualTo(1)
    }

    // 파생 쿼리(...AndSenderIdNot)로 짜면 sender_id <> ? 가 NULL 행을 TRUE로 치지 않아서
    // SYSTEM 메시지가 통째로 빠진다. 이 테스트가 그 회귀를 막는다.
    @Test
    fun `sender_id가 없는 SYSTEM 메시지도 안읽음에 포함된다`() = runTest {
        val roomId = 901L
        save(roomId, senderId = null, content = "거래가 시작되었습니다", type = MessageType.SYSTEM)
        save(roomId, senderId = ME, content = "내가 보낸 것")

        assertThat(messageRepository.countUnreadAll(roomId, ME)).isEqualTo(1)
    }

    @Test
    fun `마지막으로 읽은 메시지 이후만 세고 그 안에서도 내 메시지는 뺀다`() = runTest {
        val roomId = 902L
        val read = save(roomId, senderId = COUNTERPART, content = "읽은 메시지")
        save(roomId, senderId = ME, content = "그 뒤에 내가 보낸 것")
        save(roomId, senderId = COUNTERPART, content = "그 뒤에 상대가 보낸 것")

        assertThat(messageRepository.countUnreadAfter(roomId, read.id!!, ME)).isEqualTo(1)
    }

    private suspend fun save(
        roomId: Long,
        senderId: Long?,
        content: String,
        type: MessageType = MessageType.USER,
    ): Message = messageRepository.save(
        Message(roomId = roomId, senderId = senderId, type = type, content = content),
    )

    companion object {
        private const val ME = 1L
        private const val COUNTERPART = 2L

        @Suppress("DEPRECATION")
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.0"))
    }
}
