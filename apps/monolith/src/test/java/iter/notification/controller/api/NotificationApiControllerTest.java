package iter.notification.controller.api;

import iter.auth.domain.entity.User;
import iter.common.dto.response.CursorPageResponse;
import iter.common.exception.CustomException;
import iter.common.exception.ErrorCode;
import iter.common.exception.GlobalExceptionHandler;
import iter.common.security.CustomUserDetails;
import iter.common.security.CustomUserDetailsService;
import iter.common.security.JwtTokenProvider;
import iter.config.RestApiSecurityTestConfig;
import iter.notification.domain.entity.NotificationType;
import iter.notification.dto.response.MarkAllReadResponse;
import iter.notification.dto.response.NotificationResponse;
import iter.notification.dto.response.SseTicketResponse;
import iter.notification.dto.response.UnreadCountResponse;
import iter.notification.service.NotificationService;
import iter.notification.sse.NotificationSseService;
import iter.notification.sse.SseTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// subscribe()는 SSE 스트림(무한 응답)이라 MockMvc로 동기 검증하기 부적합해 이 테스트에서는 뺀다
// (실제 스트리밍 동작은 NotificationSseService/InMemorySseEmitterRegistry 단위 테스트가 커버).
@WebMvcTest(NotificationApiController.class)
@Import({GlobalExceptionHandler.class, RestApiSecurityTestConfig.class})
class NotificationApiControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    @MockitoBean
    private SseTicketService sseTicketService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private CustomUserDetails principal;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(USER_ID)
                .email("user@iter.test")
                .password("encoded-password")
                .name("사용자")
                .nickname("사용자닉네임")
                .build();

        principal = CustomUserDetails.builder()
                .user(user.toAuthUser())
                .build();
    }

    @Test
    void SSE_티켓_발급을_요청하면_티켓을_반환한다() throws Exception {
        when(sseTicketService.issue(USER_ID)).thenReturn("ticket-value");

        mockMvc.perform(post("/api/v1/notifications/sse-ticket")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").value("ticket-value"));
    }

    @Test
    void 알림_목록을_조회한다() throws Exception {
        NotificationResponse response = new NotificationResponse(
                1L, NotificationType.RENTAL_APPROVED, java.util.Map.of(), 10L, false, LocalDateTime.of(2026, 8, 1, 10, 0)
        );
        when(notificationService.getNotifications(eq(USER_ID), eq(false), isNull(), eq(20)))
                .thenReturn(CursorPageResponse.from(java.util.List.of(response), 20, r -> r, r -> null));

        mockMvc.perform(get("/api/v1/notifications")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].rentalId").value(10));
    }

    @Test
    void 읽지_않은_알림_개수를_조회한다() throws Exception {
        when(notificationService.getUnreadCount(USER_ID)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    void 알림을_읽음처리한다() throws Exception {
        NotificationResponse response = new NotificationResponse(
                1L, NotificationType.RENTAL_APPROVED, java.util.Map.of(), 10L, true, LocalDateTime.of(2026, 8, 1, 10, 0)
        );
        when(notificationService.markRead(USER_ID, 1L)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/notifications/1/read")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        verify(notificationService).markRead(USER_ID, 1L);
    }

    @Test
    void 본인_알림이_아니면_읽음처리시_403() throws Exception {
        when(notificationService.markRead(USER_ID, 1L)).thenThrow(new CustomException(ErrorCode.FORBIDDEN));

        mockMvc.perform(patch("/api/v1/notifications/1/read")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 알림을_전체_읽음처리한다() throws Exception {
        when(notificationService.markAllRead(USER_ID)).thenReturn(5);

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(5));
    }
}
