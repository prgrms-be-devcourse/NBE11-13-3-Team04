package iter.chat.service

import iter.chat.domain.Message
import iter.chat.domain.MessageType
import iter.chat.domain.Violation
import iter.chat.policy.ViolationCategory
import iter.chat.redis.RoomBroadcaster
import iter.chat.repository.MessageRepository
import iter.chat.repository.RoomParticipantRepository
import iter.chat.repository.ViolationRepository
import iter.chat.ws.OutgoingFrame
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

private val log = LoggerFactory.getLogger(ViolationService::class.java)

// 계정 제재는 이 서비스가 하지 않는다 — 계정 정지는 auth/admin의 권한이다. 여기서는
// 사실(위반 기록)만 쌓고, 방 단위 전송 제한과 시스템 메시지까지만 한다.
//
// 집계 창은 최근 30일 누적이다("남은 정책 판단"에서 전체 누적 vs 30일 중 30일로 확정).
@Service
class ViolationService(
    private val violationRepository: ViolationRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
    private val messageRepository: MessageRepository,
    private val roomBroadcaster: RoomBroadcaster,
    private val jsonMapper: JsonMapper,
) {
    suspend fun recordAndEscalate(roomId: Long, userId: Long, messageId: Long, categories: List<ViolationCategory>) {
        categories.forEach { category ->
            violationRepository.save(
                Violation(roomId = roomId, userId = userId, messageId = messageId, category = category.name),
            )
        }

        systemMessage(roomId, "개인정보 공유 또는 직거래 유도로 보이는 내용이 감지되어 일부가 차단되었습니다.")

        val recentCount = violationRepository.countByUserIdAndOccurredAtAfter(userId, thirtyDaysAgo())

        if (recentCount == 3L) {
            muteRoom(roomId, userId)
            systemMessage(roomId, "반복적인 위반으로 이 방에서 24시간 동안 메시지를 보낼 수 없습니다.")
        }
        if (recentCount >= 5L) {
            // ChatPolicyViolatedEvent를 관리자 신고 큐로 발행하는 부분은 CH7에서 연결한다
            // (libs:event-contract와 monolith 발행자/리스너가 그때 생긴다). 지금은 기록만
            // 남기고 로그로 표시해서 놓치지 않게 한다.
            log.warn("사용자 {}가 최근 30일 내 {}회 위반 — 관리자 검토 대상", userId, recentCount)
        }
    }

    private suspend fun muteRoom(roomId: Long, userId: Long) {
        val participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, userId) ?: return
        roomParticipantRepository.save(participant.copy(mutedUntil = Instant.now().plus(MUTE_DURATION)))
    }

    private suspend fun systemMessage(roomId: Long, content: String) {
        val saved = messageRepository.save(
            Message(roomId = roomId, type = MessageType.SYSTEM, content = content, masked = false),
        )
        val payload = jsonMapper.writeValueAsString(OutgoingFrame.message(saved))
        runCatching { roomBroadcaster.publish(roomId, payload) }
            .onFailure { e -> log.error("chat 시스템 메시지 발행 실패 roomId={}", roomId, e) }
    }

    private fun thirtyDaysAgo(): Instant = Instant.now().minus(Duration.ofDays(30))

    companion object {
        private val MUTE_DURATION = Duration.ofHours(24)
    }
}
