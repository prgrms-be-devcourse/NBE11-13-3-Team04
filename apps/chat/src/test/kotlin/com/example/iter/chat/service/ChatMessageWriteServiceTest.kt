package com.example.iter.chat.service

import com.example.iter.chat.domain.Message
import com.example.iter.chat.domain.MessageType
import com.example.iter.chat.repository.MessageRepository
import com.example.iter.chat.security.ChatPrincipal
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

@ExtendWith(MockitoExtension::class)
class ChatMessageWriteServiceTest {

    @Mock
    private lateinit var messageRepository: MessageRepository

    @InjectMocks
    private lateinit var chatMessageWriteService: ChatMessageWriteService

    @Test
    fun `보낸 사람 정보를 그대로 담아 USER 메시지로 마스킹 없이 저장한다`() = runTest {
        val captor = argumentCaptor<Message>()
        whenever(messageRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        chatMessageWriteService.send(1L, ChatPrincipal(20L, "손님"), "안녕하세요")

        val saved = captor.firstValue
        assertThat(saved.roomId).isEqualTo(1L)
        assertThat(saved.senderId).isEqualTo(20L)
        assertThat(saved.senderNickname).isEqualTo("손님")
        assertThat(saved.type).isEqualTo(MessageType.USER)
        assertThat(saved.content).isEqualTo("안녕하세요")
        assertThat(saved.masked).isFalse()
    }
}
