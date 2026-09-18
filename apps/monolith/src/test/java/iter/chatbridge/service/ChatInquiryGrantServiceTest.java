package iter.chatbridge.service;

import iter.auth.api.UserQueryPort;
import iter.auth.api.UserSummary;
import iter.common.exception.CustomException;
import iter.common.exception.ErrorCode;
import iter.device.api.EquipmentInfo;
import iter.device.api.EquipmentQueryPort;
import java.math.BigDecimal;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatInquiryGrantServiceTest {

    private static final Long EQUIPMENT_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long REQUESTER_ID = 20L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private EquipmentQueryPort equipmentQueryPort;

    @Mock
    private UserQueryPort userQueryPort;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @InjectMocks
    private ChatInquiryGrantService chatInquiryGrantService;

    @Test
    void 활성_장비에_소유자가_아닌_사람이_문의하면_그랜트를_발급한다() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(equipmentQueryPort.find(EQUIPMENT_ID)).thenReturn(Optional.of(activeEquipment()));
        when(userQueryPort.findSummary(OWNER_ID)).thenReturn(Optional.of(new UserSummary(OWNER_ID, "사장님")));
        when(userQueryPort.findSummary(REQUESTER_ID)).thenReturn(Optional.of(new UserSummary(REQUESTER_ID, "손님")));

        String grantToken = chatInquiryGrantService.issue(REQUESTER_ID, EQUIPMENT_ID);

        assertThat(grantToken).isNotBlank();
        verify(valueOperations).set(
                eq("chat:grant:" + grantToken),
                argThat(json -> json != null
                        && json.contains("\"ownerId\":10")
                        && json.contains("\"ownerNickname\":\"사장님\"")
                        && json.contains("\"requesterId\":20")
                        && json.contains("\"requesterNickname\":\"손님\"")),
                eq(Duration.ofSeconds(60))
        );
    }

    @Test
    void 존재하지_않는_장비면_EQUIPMENT_NOT_FOUND를_던진다() {
        when(equipmentQueryPort.find(EQUIPMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatInquiryGrantService.issue(REQUESTER_ID, EQUIPMENT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EQUIPMENT_NOT_FOUND);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void 비활성_장비면_CHAT_EQUIPMENT_NOT_INQUIRABLE을_던진다() {
        EquipmentInfo inactive = new EquipmentInfo(EQUIPMENT_ID, OWNER_ID, "드릴", "공구", BigDecimal.TEN, false, false);
        when(equipmentQueryPort.find(EQUIPMENT_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> chatInquiryGrantService.issue(REQUESTER_ID, EQUIPMENT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_EQUIPMENT_NOT_INQUIRABLE);
    }

    @Test
    void 삭제된_장비면_CHAT_EQUIPMENT_NOT_INQUIRABLE을_던진다() {
        EquipmentInfo deleted = new EquipmentInfo(EQUIPMENT_ID, OWNER_ID, "드릴", "공구", BigDecimal.TEN, true, true);
        when(equipmentQueryPort.find(EQUIPMENT_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> chatInquiryGrantService.issue(REQUESTER_ID, EQUIPMENT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_EQUIPMENT_NOT_INQUIRABLE);
    }

    @Test
    void 본인_장비에_문의하면_CHAT_INQUIRY_SELF_NOT_ALLOWED를_던진다() {
        when(equipmentQueryPort.find(EQUIPMENT_ID)).thenReturn(Optional.of(activeEquipment()));

        assertThatThrownBy(() -> chatInquiryGrantService.issue(OWNER_ID, EQUIPMENT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_INQUIRY_SELF_NOT_ALLOWED);
    }

    private EquipmentInfo activeEquipment() {
        return new EquipmentInfo(EQUIPMENT_ID, OWNER_ID, "전동 드릴", "공구", BigDecimal.valueOf(5000), true, false);
    }
}
