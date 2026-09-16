package com.example.iter.chatbridge.service;

import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.auth.api.UserSummary;
import com.example.iter.chatbridge.redis.ChatInquiryGrantPayload;
import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import com.example.iter.device.api.EquipmentInfo;
import com.example.iter.device.api.EquipmentQueryPort;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// chat은 device/auth DB를 보지 않는다. "이 장비에 문의해도 되는가"는 오직 monolith만
// 판정할 수 있어서, 여기서 검증을 마친 뒤 필요한 정보를 통째로 Redis에 실어 보낸다.
// chat은 이 값을 그대로 믿고 방을 만든다(ChatRestApiController#createRoom, apps:chat).
@Service
@RequiredArgsConstructor
public class ChatInquiryGrantService {

    private static final String KEY_PREFIX = "chat:grant:";
    private static final Duration GRANT_VALIDITY = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final EquipmentQueryPort equipmentQueryPort;
    private final UserQueryPort userQueryPort;
    private final JsonMapper jsonMapper;

    public String issue(Long requesterId, Long equipmentId) {
        EquipmentInfo equipment = equipmentQueryPort.find(equipmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));

        if (!equipment.isActive() || equipment.deleted()) {
            throw new CustomException(ErrorCode.CHAT_EQUIPMENT_NOT_INQUIRABLE);
        }
        if (equipment.isOwnedBy(requesterId)) {
            throw new CustomException(ErrorCode.CHAT_INQUIRY_SELF_NOT_ALLOWED);
        }

        UserSummary owner = userQueryPort.findSummary(equipment.ownerId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        UserSummary requester = userQueryPort.findSummary(requesterId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String grantToken = UUID.randomUUID().toString();
        ChatInquiryGrantPayload payload = new ChatInquiryGrantPayload(
                equipment.equipmentId(),
                equipment.name(),
                owner.userId(),
                owner.nickName(),
                requester.userId(),
                requester.nickName()
        );
        redisTemplate.opsForValue().set(KEY_PREFIX + grantToken, jsonMapper.writeValueAsString(payload), GRANT_VALIDITY);
        return grantToken;
    }
}
