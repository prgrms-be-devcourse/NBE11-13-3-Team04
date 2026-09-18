package iter.chatbridge.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import iter.event.contract.PaymentConfirmedIntegrationEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ChatIntegrationEventPublisherTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @InjectMocks
    private ChatIntegrationEventPublisher publisher;

    @Test
    @SuppressWarnings("unchecked")
    void 결제확정_이벤트를_iter_events_chat_스트림에_PAYMENT_CONFIRMED_타입으로_발행한다() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(eq("iter.events.chat"), any(Map.class))).thenReturn(RecordId.autoGenerate());

        PaymentConfirmedIntegrationEvent event = new PaymentConfirmedIntegrationEvent(
                "evt-1", 1L, 2L, 3L, 4L, Instant.parse("2026-01-01T00:00:00Z")
        );

        publisher.publishPaymentConfirmed(event);

        verify(streamOperations).add(eq("iter.events.chat"), argThat((Map<String, String> map) ->
                map != null
                        && "PAYMENT_CONFIRMED".equals(map.get("type"))
                        && String.valueOf(map.get("payload")).contains("\"rentalId\":1")
        ));
    }
}
