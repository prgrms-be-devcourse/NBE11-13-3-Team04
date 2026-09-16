package com.example.iter.chat.service

import com.example.iter.chat.domain.ChatRoom
import com.example.iter.chat.domain.RoomParticipant
import com.example.iter.chat.exception.ChatErrorCode
import com.example.iter.chat.exception.ChatException
import com.example.iter.chat.redis.GrantPayload
import com.example.iter.chat.redis.GrantStore
import com.example.iter.chat.repository.ChatRoomRepository
import com.example.iter.chat.repository.MessageRepository
import com.example.iter.chat.repository.RoomParticipantRepository
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val EQUIPMENT_ID = 1L
private const val OWNER_ID = 10L
private const val REQUESTER_ID = 20L
private const val ROOM_ID = 100L

@ExtendWith(MockitoExtension::class)
class ChatRoomServiceTest {

    @Mock
    private lateinit var grantStore: GrantStore

    @Mock
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Mock
    private lateinit var roomParticipantRepository: RoomParticipantRepository

    @Mock
    private lateinit var messageRepository: MessageRepository

    @InjectMocks
    private lateinit var chatRoomService: ChatRoomService

    @Test
    fun `그랜트가 없거나 만료됐으면 GRANT_INVALID_OR_EXPIRED를 던진다`() = runTest {
        whenever(grantStore.consume("bad-token")).thenReturn(null)

        val exception = assertThrowsChatException { chatRoomService.createRoom(REQUESTER_ID, "bad-token") }
        assertThat(exception.errorCode).isEqualTo(ChatErrorCode.GRANT_INVALID_OR_EXPIRED)
    }

    @Test
    fun `그랜트의 requesterId와 요청자가 다르면 GRANT_USER_MISMATCH를 던진다`() = runTest {
        whenever(grantStore.consume("token")).thenReturn(grant())

        val exception = assertThrowsChatException { chatRoomService.createRoom(999L, "token") }
        assertThat(exception.errorCode).isEqualTo(ChatErrorCode.GRANT_USER_MISMATCH)
    }

    @Test
    fun `이미 같은 장비+요청자 방이 있으면 그 방을 그대로 반환하고 참여자를 새로 만들지 않는다`() = runTest {
        val existingRoom = ChatRoom(
            id = ROOM_ID,
            equipmentId = EQUIPMENT_ID,
            equipmentName = "드릴",
            ownerId = OWNER_ID,
            requesterId = REQUESTER_ID,
        )
        whenever(grantStore.consume("token")).thenReturn(grant())
        whenever(chatRoomRepository.findByEquipmentIdAndRequesterId(EQUIPMENT_ID, REQUESTER_ID))
            .thenReturn(existingRoom)

        val room = chatRoomService.createRoom(REQUESTER_ID, "token")

        assertThat(room.id).isEqualTo(ROOM_ID)
        verify(chatRoomRepository, never()).save(any())
        verify(roomParticipantRepository, never()).save(any())
    }

    @Test
    fun `방이 없으면 새로 만들고 참여자 두 명을 등록한다`() = runTest {
        whenever(grantStore.consume("token")).thenReturn(grant())
        whenever(chatRoomRepository.findByEquipmentIdAndRequesterId(EQUIPMENT_ID, REQUESTER_ID))
            .thenReturn(null)
        val savedRoom = ChatRoom(
            id = ROOM_ID,
            equipmentId = EQUIPMENT_ID,
            equipmentName = "드릴",
            ownerId = OWNER_ID,
            requesterId = REQUESTER_ID,
        )
        whenever(chatRoomRepository.save(any())).thenReturn(savedRoom)
        whenever(roomParticipantRepository.save(any())).thenAnswer { it.arguments[0] }

        val room = chatRoomService.createRoom(REQUESTER_ID, "token")

        assertThat(room.id).isEqualTo(ROOM_ID)
        verify(roomParticipantRepository).save(
            org.mockito.kotlin.argThat<RoomParticipant> { roomId == ROOM_ID && userId == OWNER_ID },
        )
        verify(roomParticipantRepository).save(
            org.mockito.kotlin.argThat<RoomParticipant> { roomId == ROOM_ID && userId == REQUESTER_ID },
        )
    }

    @Test
    fun `참여자가 아니면 markRead에서 ROOM_ACCESS_DENIED를 던진다`() = runTest {
        whenever(roomParticipantRepository.findByRoomIdAndUserId(ROOM_ID, REQUESTER_ID)).thenReturn(null)

        val exception = assertThrowsChatException { chatRoomService.markRead(REQUESTER_ID, ROOM_ID, 5L) }
        assertThat(exception.errorCode).isEqualTo(ChatErrorCode.ROOM_ACCESS_DENIED)
    }

    // AssertJ의 assertThatThrownBy는 suspend 람다를 못 받는다. runTest를 중첩 호출하면
    // "Only a single call to runTest can be performed during one test"로 깨진다
    // (실제로 겪음) — 이미 열려 있는 suspend 컨텍스트 안에서 그냥 try/catch로 잡는다.
    private suspend fun assertThrowsChatException(block: suspend () -> Unit): ChatException {
        try {
            block()
        } catch (e: ChatException) {
            return e
        }
        throw AssertionError("ChatException이 발생해야 하는데 발생하지 않았습니다.")
    }

    private fun grant() = GrantPayload(
        equipmentId = EQUIPMENT_ID,
        equipmentName = "드릴",
        ownerId = OWNER_ID,
        ownerNickname = "사장님",
        requesterId = REQUESTER_ID,
        requesterNickname = "손님",
    )
}
