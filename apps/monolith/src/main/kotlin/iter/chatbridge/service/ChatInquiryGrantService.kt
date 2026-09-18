package iter.chatbridge.service

import iter.auth.api.UserQueryPort
import iter.chatbridge.redis.ChatInquiryGrantPayload
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import iter.device.api.EquipmentQueryPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID

// chat은 device/auth DB를 보지 않는다. "이 장비에 문의해도 되는가"는 오직 monolith만
// 판정할 수 있어서, 여기서 검증을 마친 뒤 필요한 정보를 통째로 Redis에 실어 보낸다.
// chat은 이 값을 그대로 믿고 방을 만든다(ChatRestApiController#createRoom, apps:chat).
@Service
class ChatInquiryGrantService(
    private val redisTemplate: StringRedisTemplate,
    private val equipmentQueryPort: EquipmentQueryPort,
    private val userQueryPort: UserQueryPort,
    private val jsonMapper: JsonMapper,
) {

    fun issue(requesterId: Long, equipmentId: Long): String {
        val equipment = equipmentQueryPort.find(equipmentId)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }

        if (!equipment.isActive() || equipment.deleted) {
            throw CustomException(ErrorCode.CHAT_EQUIPMENT_NOT_INQUIRABLE)
        }
        if (equipment.isOwnedBy(requesterId)) {
            throw CustomException(ErrorCode.CHAT_INQUIRY_SELF_NOT_ALLOWED)
        }

        val owner = userQueryPort.findSummary(equipment.ownerId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        val requester = userQueryPort.findSummary(requesterId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        val grantToken = UUID.randomUUID().toString()
        val payload = ChatInquiryGrantPayload(
            equipment.equipmentId,
            equipment.name,
            owner.userId,
            owner.nickName,
            requester.userId,
            requester.nickName,
        )
        redisTemplate.opsForValue().set(KEY_PREFIX + grantToken, jsonMapper.writeValueAsString(payload), GRANT_VALIDITY)
        return grantToken
    }

    companion object {
        private const val KEY_PREFIX = "chat:grant:"
        private val GRANT_VALIDITY: Duration = Duration.ofSeconds(60)
    }
}
