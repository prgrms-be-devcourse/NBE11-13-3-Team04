package com.example.iter.chat.domain

import java.time.Instant
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

// PHONE / ACCOUNT / MESSENGER_ID / EXTERNAL_LINK / DIRECT_DEAL — 실제 enum은
// policy 패키지(CH6)에서 정의한다. 여기서는 문자열로만 들고 있어서 정책 사전이
// 바뀌어도 이력 테이블 스키마가 흔들리지 않는다.
@Table("chat_violations")
data class Violation(
    @Id
    val id: Long? = null,
    val roomId: Long,
    val userId: Long,
    val messageId: Long,
    val category: String,
    val occurredAt: Instant = Instant.now(),
)
