package com.example.iter.notification;

import com.example.iter.auth.domain.entity.User;
import com.example.iter.auth.domain.repository.UserRepository;
import com.example.iter.common.security.JwtTokenProvider;
import com.example.iter.device.domain.entity.Equipment;
import com.example.iter.device.domain.entity.EquipmentCategory;
import com.example.iter.device.domain.entity.EquipmentStatus;
import com.example.iter.device.domain.entity.ProductConditionType;
import com.example.iter.device.domain.repository.EquipmentRepository;
import com.example.iter.notification.domain.entity.NotificationType;
import com.example.iter.notification.domain.repository.NotificationRepository;
import com.example.iter.notification.service.NotificationService;
import com.example.iter.reservation.api.RentalStatus;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.domain.repository.RentalRepository;
import com.example.iter.reservation.event.RentalApprovedEvent;
import com.example.iter.support.ApiTestClient;
import com.example.iter.support.ApiTestClient.ApiResponse;
import com.example.iter.support.MonolithIntegrationTest;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// IT-NOTI-001 (INTEGRATION_TEST_DESIGN.md) — 도메인 이벤트 커밋 후 알림 생성과 SSE 전달.
//
// 알림 개수 자체(결제/승인/거절 등 각 이벤트당 몇 건 생성되는지)는
// RentalPaymentFlowIntegrationTest가 실제 결제 흐름 전체에서 이미 검증한다. 여기서는
// 그 테스트가 다루지 않는 두 가지에 집중한다: (1) 실시간 SSE 전달 자체가 실제 HTTP
// 스트림으로 끝까지 이어지는지, (2) AFTER_COMMIT 리스너가 이름 그대로 "커밋 후에만"
// 반응하는지 — 커밋되면 알림이 생기고 롤백되면 전혀 생기지 않는지를 같은 이벤트로 대조한다.
class NotificationSseFlowIntegrationTest extends MonolithIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private EquipmentRepository equipmentRepository;
    @Autowired
    private RentalRepository rentalRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void sse_티켓을_받아_구독하면_알림_생성이_실시간으로_전달된다() throws Exception {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        User receiver = createUser("noti-sse@integration.test", "알림수신자");
        String token = jwtTokenProvider.generateAccessToken(receiver.toAuthUser());

        ApiResponse ticketResponse = api.post("/api/v1/notifications/sse-ticket", Map.of(), token);
        assertThat(ticketResponse.statusCode()).isEqualTo(200);
        String ticket = ticketResponse.json().get("ticket").asText();

        SseStream stream = openSseStream(ticket);
        try {
            // subscribe() 직후 서버가 보내는 더미 connect 이벤트를 먼저 흘려보낸다.
            stream.awaitEventContaining("connect", 5);

            notificationService.create(
                    receiver.getId(), receiver.getEmail(), NotificationType.RENTAL_APPROVED,
                    "알림 제목", "알림 본문", Map.of("rentalId", 555L), 555L);

            String pushed = stream.awaitEventContaining("RENTAL_APPROVED", 10);
            assertThat(pushed).contains("RENTAL_APPROVED");
        } finally {
            stream.close();
        }

        ApiResponse unreadCount = api.get(
                "/api/v1/notifications/unread-count", Map.of("Authorization", "Bearer " + token));
        assertThat(unreadCount.statusCode()).isEqualTo(200);
        assertThat(unreadCount.json().get("unreadCount").asLong()).isEqualTo(1L);
    }

    @Test
    void 읽음_처리는_본인만_할_수_있고_처리후_미읽음_수가_줄어든다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        User receiver = createUser("noti-read@integration.test", "읽음테스트");
        User stranger = createUser("noti-read-stranger@integration.test", "타인");
        String receiverToken = jwtTokenProvider.generateAccessToken(receiver.toAuthUser());
        String strangerToken = jwtTokenProvider.generateAccessToken(stranger.toAuthUser());

        notificationService.create(
                receiver.getId(), receiver.getEmail(), NotificationType.RENTAL_APPROVED,
                "알림 제목", "알림 본문", Map.of("rentalId", 556L), 556L);

        ApiResponse list = api.get(
                "/api/v1/notifications?unreadOnly=true&size=10", Map.of("Authorization", "Bearer " + receiverToken));
        assertThat(list.statusCode()).isEqualTo(200);
        long notificationId = list.json().get("content").get(0).get("id").asLong();

        ApiResponse forbidden = api.patch(
                "/api/v1/notifications/" + notificationId + "/read", strangerToken);
        assertThat(forbidden.statusCode()).isEqualTo(403);

        ApiResponse marked = api.patch(
                "/api/v1/notifications/" + notificationId + "/read", receiverToken);
        assertThat(marked.statusCode()).isEqualTo(200);

        ApiResponse afterRead = api.get(
                "/api/v1/notifications/unread-count", Map.of("Authorization", "Bearer " + receiverToken));
        assertThat(afterRead.json().get("unreadCount").asLong()).isZero();
    }

    @Test
    void 알림_트리거_이벤트가_커밋되면_알림이_생기고_롤백되면_전혀_생기지_않는다() {
        User owner = createUser("noti-tx-owner@integration.test", "등록자");
        User renter = createUser("noti-tx-renter@integration.test", "대여자");
        Equipment equipment = equipmentRepository.saveAndFlush(new Equipment(
                owner.getId(), EquipmentCategory.CAMERA, "알림 트랜잭션 테스트 장비", null,
                BigDecimal.valueOf(10_000), LocalDate.now().plusDays(1), LocalDate.now().plusMonths(1),
                EquipmentStatus.ACTIVE, ProductConditionType.NORMAL, null));

        Rental rolledBackRental = saveApprovedRental(equipment, owner, renter);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // 롤백되는 트랜잭션 안에서 이벤트를 발행한다 — AFTER_COMMIT 리스너는 커밋이
        // 일어나지 않았으므로 아예 호출되지 않아야 한다.
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new RentalApprovedEvent(rolledBackRental.getId()));
            status.setRollbackOnly();
            return null;
        });
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(renter.getId())).isZero();

        Rental committedRental = saveApprovedRental(equipment, owner, renter);
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new RentalApprovedEvent(committedRental.getId()));
            return null;
        });
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(renter.getId())).isEqualTo(1L);
    }

    // ------------------------------------------------------------------

    private Rental saveApprovedRental(Equipment equipment, User owner, User renter) {
        return rentalRepository.saveAndFlush(new Rental(
                equipment.getId(), owner.getId(), renter.getId(),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(6),
                equipment.getName(), equipment.getDailyPrice(), 5,
                equipment.getDailyPrice().multiply(BigDecimal.valueOf(5)),
                null, null, null, null, null, null, null, null,
                RentalStatus.APPROVED, null, null, null));
    }

    private User createUser(String email, String name) {
        User user = User.builder()
                .email(email)
                .password("not-used-in-this-test")
                .name(name)
                .nickname(name)
                .phone("010-0000-0000")
                .build();
        return userRepository.saveAndFlush(user);
    }

    // SSE(text/event-stream)는 ApiTestClient의 BodyHandlers.ofString()으로 읽으면 스트림이
    // 끝날 때까지(최대 30분) 블로킹된다. 청크를 즉시 큐로 흘려보내는 별도의 최소 클라이언트가
    // 필요하다 — 디자인 문서 7.3(고정 sleep 대신 폴링)에 맞춰 큐에서 타임아웃 대기한다.
    private SseStream openSseStream(String ticket) {
        HttpClient client = HttpClient.newHttpClient();
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/v1/notifications/subscribe?ticket=" + ticket))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        CompletableFuture<HttpResponse<java.io.InputStream>> future =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        Thread reader = new Thread(() -> {
            try (var body = future.get(20, TimeUnit.SECONDS).body();
                    var buffered = new BufferedReader(new InputStreamReader(body))) {
                buffered.lines().forEach(lines::add);
            } catch (Exception ignored) {
                // 스트림 종료/타임아웃은 테스트 종료 시 정상적으로 발생한다.
            }
        });
        reader.setDaemon(true);
        reader.start();
        return new SseStream(lines, reader, future, client);
    }

    private record SseStream(
            BlockingQueue<String> lines,
            Thread reader,
            CompletableFuture<HttpResponse<java.io.InputStream>> response,
            HttpClient client
    ) {
        String awaitEventContaining(String marker, int timeoutSeconds) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
            StringBuilder buffer = new StringBuilder();
            while (System.currentTimeMillis() < deadline) {
                String line = lines.poll(500, TimeUnit.MILLISECONDS);
                if (line == null) {
                    continue;
                }
                buffer.append(line).append('\n');
                if (line.contains(marker)) {
                    return buffer.toString();
                }
            }
            throw new AssertionError("SSE 스트림에서 '" + marker + "'을(를) " + timeoutSeconds + "초 안에 받지 못했다. 수신: " + buffer);
        }

        void close() {
            response.thenAccept(result -> {
                try {
                    result.body().close();
                } catch (IOException ignored) {
                    // 테스트 종료 중 이미 닫힌 스트림일 수 있다.
                }
            });
            response.cancel(true);
            reader.interrupt();
            client.close();
        }
    }
}
