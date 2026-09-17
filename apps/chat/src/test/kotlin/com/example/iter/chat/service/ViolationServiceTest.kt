package com.example.iter.chat.service

import com.example.iter.chat.domain.Message
import com.example.iter.chat.domain.MessageType
import com.example.iter.chat.domain.RoomParticipant
import com.example.iter.chat.policy.ViolationCategory
import com.example.iter.chat.redis.RoomBroadcaster
import com.example.iter.chat.repository.MessageRepository
import com.example.iter.chat.repository.RoomParticipantRepository
import com.example.iter.chat.repository.ViolationRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

private const val ROOM_ID = 1L
private const val USER_ID = 20L
private const val MESSAGE_ID = 100L

@ExtendWith(MockitoExtension::class)
class ViolationServiceTest {

    @Mock
    private lateinit var violationRepository: ViolationRepository

    @Mock
    private lateinit var roomParticipantRepository: RoomParticipantRepository

    @Mock
    private lateinit var messageRepository: MessageRepository

    @Mock
    private lateinit var roomBroadcaster: RoomBroadcaster

    @Spy
    private val jsonMapper: JsonMapper = JsonMapper.builder().findAndAddModules().build()

    @InjectMocks
    private lateinit var violationService: ViolationService

    @Test
    fun `위반이 있으면 카테고리별로 기록하고 경고 시스템 메시지를 남긴다`() = runTest {
        whenever(violationRepository.countByUserIdAndOccurredAtAfter(any(), any())).thenReturn(1L)
        whenever(messageRepository.save(any())).thenAnswer { it.arguments[0] }

        violationService.recordAndEscalate(ROOM_ID, USER_ID, MESSAGE_ID, listOf(ViolationCategory.PHONE))

        verify(violationRepository).save(
            org.mockito.kotlin.argThat { roomId == ROOM_ID && userId == USER_ID && category == "PHONE" },
        )
        val captor = argumentCaptor<Message>()
        verify(messageRepository).save(captor.capture())
        assertThat(captor.firstValue.type).isEqualTo(MessageType.SYSTEM)
        verify(roomParticipantRepository, never()).save(any())
    }

    @Test
    fun `최근 30일 3회째면 방을 24시간 음소거하고 추가 경고를 보낸다`() = runTest {
        whenever(violationRepository.countByUserIdAndOccurredAtAfter(any(), any())).thenReturn(3L)
        whenever(messageRepository.save(any())).thenAnswer { it.arguments[0] }
        val participant = RoomParticipant(id = 1L, roomId = ROOM_ID, userId = USER_ID, nickname = "손님")
        whenever(roomParticipantRepository.findByRoomIdAndUserId(ROOM_ID, USER_ID)).thenReturn(participant)

        violationService.recordAndEscalate(ROOM_ID, USER_ID, MESSAGE_ID, listOf(ViolationCategory.DIRECT_DEAL))

        val captor = argumentCaptor<RoomParticipant>()
        verify(roomParticipantRepository).save(captor.capture())
        assertThat(captor.firstValue.mutedUntil).isAfter(Instant.now().plusSeconds(23 * 3600))
        // 경고 시스템 메시지(공통) + 음소거 안내 시스템 메시지, 최소 2번은 저장돼야 한다.
        verify(messageRepository, org.mockito.kotlin.atLeast(2)).save(any())
    }

    @Test
    fun `5회째부터는 음소거를 다시 걸지 않고 기록만 남긴다`() = runTest {
        whenever(violationRepository.countByUserIdAndOccurredAtAfter(any(), any())).thenReturn(5L)
        whenever(messageRepository.save(any())).thenAnswer { it.arguments[0] }

        violationService.recordAndEscalate(ROOM_ID, USER_ID, MESSAGE_ID, listOf(ViolationCategory.EXTERNAL_LINK))

        // 3회 임계값을 지나 5회가 됐을 때 새로 음소거 호출이 발생하지 않는다
        // (3회에서 이미 걸렸어야 하고, 5회는 로그로만 표시한다 — CH7에서 이벤트 발행 연결).
        verify(roomParticipantRepository, never()).save(any())
    }

    @Test
    fun `1회일 때는 음소거하지 않는다`() = runTest {
        whenever(violationRepository.countByUserIdAndOccurredAtAfter(any(), any())).thenReturn(1L)
        whenever(messageRepository.save(any())).thenAnswer { it.arguments[0] }

        violationService.recordAndEscalate(ROOM_ID, USER_ID, MESSAGE_ID, listOf(ViolationCategory.MESSENGER_ID))

        verify(roomParticipantRepository, never()).save(any())
    }
}
