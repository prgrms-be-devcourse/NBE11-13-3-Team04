package com.example.iter.chat.security

import com.example.iter.chat.exception.ChatErrorCode
import com.example.iter.chat.exception.ChatException
import org.springframework.web.reactive.function.server.ServerRequest

// TicketAuthWebFilter가 exchange attribute에 넣어 둔 principal을 꺼낸다.
// 없으면(티켓이 없거나, 있어도 무효/만료) 401로 응답할 ChatException을 던진다.
fun ServerRequest.requirePrincipal(): ChatPrincipal =
    this.exchange().attributes[CHAT_PRINCIPAL_ATTRIBUTE] as? ChatPrincipal
        ?: throw ChatException(ChatErrorCode.TICKET_INVALID)
