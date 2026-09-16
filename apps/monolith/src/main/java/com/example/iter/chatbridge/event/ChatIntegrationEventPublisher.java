package com.example.iter.chatbridge.event;

import com.example.iter.event.contract.PaymentConfirmedIntegrationEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// chat이 소비하는 유일한 통합 이벤트 채널. Redis Stream(XADD)을 쓰는 이유는 Pub/Sub과
// 달리 구독자가 그 순간 안 떠 있어도 유실되지 않기 때문이다(chat이 재시작 중이어도
// 다시 뜨면 이어서 읽는다) — 결제 확정처럼 놓치면 안 되는 이벤트에 맞는 선택이다.
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatIntegrationEventPublisher {

    // apps:chat의 소비자 그룹 이름과 반드시 맞아야 한다(ChatIntegrationEventConsumer).
    private static final String STREAM_KEY = "iter.events.chat";
    private static final String TYPE_PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public void publishPaymentConfirmed(PaymentConfirmedIntegrationEvent event) {
        try {
            redisTemplate.opsForStream().add(STREAM_KEY, Map.of(
                    "type", TYPE_PAYMENT_CONFIRMED,
                    "payload", jsonMapper.writeValueAsString(event)
            ));
        } catch (Exception e) {
            // 여기서 실패하면 이 이벤트는 유실된다(아웃박스 없음) — 알려진 한계다.
            // rentalId를 로그에 남겨서 필요하면 수동으로 재처리할 수 있게 한다.
            log.error("chat 통합 이벤트 발행 실패 rentalId={}", event.getRentalId(), e);
        }
    }
}
