package com.example.iter.chat.service

import com.example.iter.chat.domain.ChatRoom
import com.example.iter.chat.domain.Message
import com.example.iter.chat.domain.MessageType
import com.example.iter.chat.domain.RoomParticipant
import com.example.iter.chat.domain.RoomStage
import com.example.iter.chat.policy.ViolationCategory
import com.example.iter.chat.repository.ChatRoomRepository
import com.example.iter.chat.repository.MessageRepository
import com.example.iter.chat.repository.RoomParticipantRepository
import com.example.iter.chat.security.ChatPrincipal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val ROOM_ID = 1L
private const val SENDER_ID = 20L

@ExtendWith(MockitoExtension::class)
class ChatMessageWriteServiceTest {

    @Mock
    private lateinit var messageRepository: MessageRepository

    @Mock
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Mock
    private lateinit var roomParticipantRepository: RoomParticipantRepository

    @InjectMocks
    private lateinit var chatMessageWriteService: ChatMessageWriteService

    @Test
    fun `INQUIRY 단계에서 평범한 문의는 마스킹 없이 그대로 저장한다`() = runTest {
        whenever(chatRoomRepository.findById(ROOM_ID)).thenReturn(inquiryRoom())
        val captor = argumentCaptor<Message>()
        whenever(messageRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = chatMessageWriteService.send(ROOM_ID, ChatPrincipal(SENDER_ID, "손님"), "안녕하세요")

        assertThat(result).isInstanceOf(SendResult.Sent::class.java)
        val sent = result as SendResult.Sent
        assertThat(sent.violations).isEmpty()
        val saved = captor.firstValue
        assertThat(saved.roomId).isEqualTo(ROOM_ID)
        assertThat(saved.senderId).isEqualTo(SENDER_ID)
        assertThat(saved.type).isEqualTo(MessageType.USER)
        assertThat(saved.masked).isFalse()
    }

    @Test
    fun `INQUIRY 단계에서 전화번호가 섞이면 마스킹해서 저장하고 위반 카테고리를 돌려준다`() = runTest {
        whenever(chatRoomRepository.findById(ROOM_ID)).thenReturn(inquiryRoom())
        val captor = argumentCaptor<Message>()
        whenever(messageRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = chatMessageWriteService.send(ROOM_ID, ChatPrincipal(SENDER_ID, "손님"), "010-1234-5678로 연락주세요")

        val sent = result as SendResult.Sent
        assertThat(sent.violations).containsExactly(ViolationCategory.PHONE)
        assertThat(captor.firstValue.masked).isTrue()
        assertThat(captor.firstValue.content).doesNotContain("1234", "5678")
    }

    @Test
    fun `TRADE 단계에서는 전화번호가 섞여도 정책을 적용하지 않는다`() = runTest {
        whenever(chatRoomRepository.findById(ROOM_ID)).thenReturn(tradeRoom())
        val captor = argumentCaptor<Message>()
        whenever(messageRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = chatMessageWriteService.send(ROOM_ID, ChatPrincipal(SENDER_ID, "손님"), "010-1234-5678로 연락주세요")

        val sent = result as SendResult.Sent
        assertThat(sent.violations).isEmpty()
        assertThat(captor.firstValue.masked).isFalse()
        assertThat(captor.firstValue.content).contains("1234")
    }

    @Test
    fun `음소거 중이면 저장하지 않고 Muted를 반환한다`() = runTest {
        val muted = RoomParticipant(
            id = 1L,
            roomId = ROOM_ID,
            userId = SENDER_ID,
            nickname = "손님",
            mutedUntil = Instant.now().plusSeconds(3600),
        )
        whenever(roomParticipantRepository.findByRoomIdAndUserId(ROOM_ID, SENDER_ID)).thenReturn(muted)

        val result = chatMessageWriteService.send(ROOM_ID, ChatPrincipal(SENDER_ID, "손님"), "안녕하세요")

        assertThat(result).isEqualTo(SendResult.Muted)
        verify(messageRepository, org.mockito.kotlin.never()).save(org.mockito.kotlin.any())
    }

    private fun inquiryRoom() = ChatRoom(
        id = ROOM_ID, equipmentId = 1L, equipmentName = "드릴", ownerId = 10L, requesterId = SENDER_ID,
        stage = RoomStage.INQUIRY,
    )

    private fun tradeRoom() = ChatRoom(
        id = ROOM_ID, equipmentId = 1L, equipmentName = "드릴", ownerId = 10L, requesterId = SENDER_ID,
        stage = RoomStage.TRADE,
    )
}
