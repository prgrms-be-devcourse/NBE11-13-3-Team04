package com.example.iter.chatbridge.service;

import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.auth.api.UserSummary;
import com.example.iter.chatbridge.redis.ChatTicketPayload;
import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// apps:chat은 JWT 비밀키를 갖지 않는다. 대신 monolith가 로그인 사용자를 확인한 뒤
// 이 짧은 티켓을 발급하고, chat은 Redis에서 그 값만 조회해서 신뢰한다.
//
// SSE의 1회용 티켓(SseTicketService)과 달리 여기는 REST에도 재사용해야 해서
// GET으로 소비하고(삭제하지 않음) TTL로만 만료시킨다 — 1회용으로 막으면
// 재연결·과거 메시지 조회가 매번 새 티켓을 요구하게 되어 비실용적이다.
@Service
@RequiredArgsConstructor
public class ChatTicketService {

    private static final String KEY_PREFIX = "chat:ticket:";
    private static final Duration TICKET_VALIDITY = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final UserQueryPort userQueryPort;
    private final JsonMapper jsonMapper;

    public String issue(Long userId) {
        UserSummary summary = userQueryPort.findSummary(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String ticket = UUID.randomUUID().toString();
        String payload = jsonMapper.writeValueAsString(new ChatTicketPayload(userId, summary.nickName()));
        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, payload, TICKET_VALIDITY);
        return ticket;
    }
}
