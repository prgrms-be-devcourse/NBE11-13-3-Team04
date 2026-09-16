package com.example.iter.chatbridge.service;

import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.auth.api.UserSummary;
import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatTicketServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserQueryPort userQueryPort;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @InjectMocks
    private ChatTicketService chatTicketService;

    @Test
    void 티켓을_발급하면_Redis에_userId와_nickname이_담긴_JSON을_TTL_10분으로_저장한다() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userQueryPort.findSummary(1L)).thenReturn(Optional.of(new UserSummary(1L, "재준")));

        String ticket = chatTicketService.issue(1L);

        assertThat(ticket).isNotBlank();
        verify(valueOperations).set(
                eq("chat:ticket:" + ticket),
                argThat(json -> json != null && json.contains("\"userId\":1") && json.contains("\"nickname\":\"재준\"")),
                eq(Duration.ofMinutes(10))
        );
    }

    @Test
    void 존재하지_않는_유저면_USER_NOT_FOUND를_던진다() {
        when(userQueryPort.findSummary(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatTicketService.issue(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
