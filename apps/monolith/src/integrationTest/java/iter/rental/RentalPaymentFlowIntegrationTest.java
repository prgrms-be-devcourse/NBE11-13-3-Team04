package iter.rental;

import iter.auth.domain.entity.User;
import iter.auth.domain.repository.UserRepository;
import iter.common.security.JwtTokenProvider;
import iter.device.domain.entity.Equipment;
import iter.device.domain.entity.EquipmentCategory;
import iter.device.domain.entity.EquipmentStatus;
import iter.device.domain.entity.ProductConditionType;
import iter.device.domain.repository.EquipmentOccupancyRepository;
import iter.device.domain.repository.EquipmentRepository;
import iter.delivery.domain.entity.ShippingType;
import iter.delivery.domain.repository.ShippingRepository;
import iter.dispute.domain.repository.DisputeRepository;
import iter.dispute.domain.repository.ReportRepository;
import iter.notification.domain.repository.NotificationRepository;
import iter.payment.api.PaymentStatus;
import iter.payment.domain.entity.Payment;
import iter.payment.domain.repository.PaymentRepository;
import iter.common.image.CaptureView;
import iter.reservation.api.RentalStatus;
import iter.reservation.domain.entity.EvidenceUploadPhase;
import iter.reservation.domain.entity.Rental;
import iter.reservation.domain.entity.RentalEvidenceUpload;
import iter.reservation.domain.repository.ReceiptImageRepository;
import iter.reservation.domain.repository.ReceiptRepository;
import iter.reservation.domain.repository.RentalEvidenceUploadRepository;
import iter.reservation.domain.repository.RentalRepository;
import iter.reservation.domain.repository.RentalReviewRepository;
import iter.reservation.domain.repository.ReturnReceiptImageRepository;
import iter.reservation.domain.repository.ReturnReceiptRepository;
import iter.support.ApiTestClient;
import iter.support.ApiTestClient.ApiResponse;
import iter.support.MonolithIntegrationTest;
import iter.support.TossStub.RecordedRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RentalPaymentFlowIntegrationTest extends MonolithIntegrationTest {

    private static final String CHAT_STREAM_KEY = "iter.events.chat";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EquipmentRepository equipmentRepository;
    @Autowired
    private RentalRepository rentalRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private EquipmentOccupancyRepository equipmentOccupancyRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private RentalEvidenceUploadRepository evidenceUploadRepository;
    @Autowired
    private ReceiptRepository receiptRepository;
    @Autowired
    private ReceiptImageRepository receiptImageRepository;
    @Autowired
    private ReturnReceiptRepository returnReceiptRepository;
    @Autowired
    private ReturnReceiptImageRepository returnReceiptImageRepository;
    @Autowired
    private RentalReviewRepository rentalReviewRepository;
    @Autowired
    private ShippingRepository shippingRepository;
    @Autowired
    private DisputeRepository disputeRepository;
    @Autowired
    private ReportRepository reportRepository;
    @MockitoBean
    private S3Client s3Client;

    @Test
    void 승인부터_발송_수령_반납_완료와_쌍방_후기까지_증빙을_연결한다() {
        Fixture fixture = fixture();
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        Long rentalId = createPaidRental(api, fixture).rentalId();
        String path = "/api/v1/rentals/" + rentalId;

        assertThat(api.post(path + "/reviews", Map.of("rating", 5, "content", "너무 빨라요"),
                fixture.renterToken()).statusCode()).isEqualTo(409);
        assertThat(api.patch(path + "/approve", fixture.ownerToken()).statusCode()).isEqualTo(200);
        assertThat(api.post(path + "/shipping", Map.of("carrier", "통합택배", "trackingNumber", "12345"),
                fixture.renterToken()).statusCode()).isEqualTo(403);
        assertThat(api.post(path + "/shipping", Map.of("carrier", "통합택배", "trackingNumber", "12345"),
                fixture.ownerToken()).statusCode()).isEqualTo(200);
        assertThat(rentalRepository.findById(rentalId).orElseThrow().getStatus()).isEqualTo(RentalStatus.SHIPPING);

        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("image/jpeg").contentLength(12L).build());
        List<Map<String, String>> receiptImages = evidence(rentalId, fixture.renter().getId(), EvidenceUploadPhase.RECEIPT);
        assertThat(api.post(path + "/receipt", Map.of(
                "productCondition", "NORMAL", "conditionDetail", "수령 상태 양호", "images", receiptImages),
                fixture.renterToken()).statusCode()).isEqualTo(200);
        assertThat(rentalRepository.findById(rentalId).orElseThrow().getStatus()).isEqualTo(RentalStatus.RENTING);
        var receipt = receiptRepository.findByRentalId(rentalId).orElseThrow();
        assertThat(receiptImageRepository.findByReceipt_IdOrderBySortOrderAscIdAsc(receipt.getId()))
                .extracting(image -> image.getImageUrl()).containsExactlyElementsOf(
                        receiptImages.stream().map(image -> image.get("objectKey")).toList());

        assertThat(api.post(path + "/return-request", Map.of(), fixture.renterToken()).statusCode()).isEqualTo(200);
        assertThat(rentalRepository.findById(rentalId).orElseThrow().getStatus())
                .isEqualTo(RentalStatus.RETURN_REQUESTED);
        List<Map<String, String>> returnImages = evidence(rentalId, fixture.renter().getId(), EvidenceUploadPhase.RETURN);
        assertThat(api.post(path + "/return-evidence", Map.of(
                "productCondition", "NORMAL", "conditionDetail", "반납 상태 양호", "images", returnImages),
                fixture.renterToken()).statusCode()).isEqualTo(200);
        assertThat(rentalRepository.findById(rentalId).orElseThrow().getStatus()).isEqualTo(RentalStatus.RETURNED);
        var returnReceipt = returnReceiptRepository.findByRentalId(rentalId).orElseThrow();
        assertThat(returnReceiptImageRepository.findByReturnReceipt_IdOrderBySortOrderAscIdAsc(returnReceipt.getId()))
                .extracting(image -> image.getImageUrl()).containsExactlyElementsOf(
                        returnImages.stream().map(image -> image.get("objectKey")).toList());
        assertThat(shippingRepository.findByRentalId(rentalId)).hasSize(2)
                .extracting(shipping -> shipping.getType())
                .containsExactlyInAnyOrder(ShippingType.OUTBOUND, ShippingType.RETURN);
        assertThat(evidenceUploadRepository.findAll()).allMatch(RentalEvidenceUpload::isUsed);

        assertThat(api.post(path + "/return-confirmation", Map.of("hasIssue", false),
                fixture.renterToken()).statusCode()).isEqualTo(403);
        assertThat(api.post(path + "/return-confirmation", Map.of("hasIssue", false),
                fixture.ownerToken()).statusCode()).isEqualTo(200);
        assertThat(rentalRepository.findById(rentalId).orElseThrow().getStatus()).isEqualTo(RentalStatus.COMPLETED);
        assertThat(equipmentOccupancyRepository.count()).isZero();
        assertThat(paymentRepository.findByRentalId(rentalId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(api.post(path + "/return-confirmation", Map.of("hasIssue", false),
                fixture.ownerToken()).statusCode()).isEqualTo(409);

        assertThat(api.post(path + "/reviews", Map.of("rating", 5, "content", "좋은 장비"),
                fixture.renterToken()).statusCode()).isEqualTo(200);
        assertThat(api.post(path + "/reviews", Map.of("rating", 4, "content", "반납 완료"),
                fixture.ownerToken()).statusCode()).isEqualTo(200);
        assertThat(rentalReviewRepository.findAllByRentalId(rentalId)).hasSize(2)
                .extracting(review -> review.getRevieweeId())
                .containsExactlyInAnyOrder(fixture.owner().getId(), fixture.renter().getId());
        assertThat(api.post(path + "/reviews", Map.of("rating", 5, "content", "중복"),
                fixture.renterToken()).statusCode()).isEqualTo(409);
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.owner().getId())).isEqualTo(4);
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.renter().getId())).isEqualTo(3);
        verify(s3Client, times(6)).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void 반납_이상_신고는_대여와_분쟁과_신고를_함께_저장하고_점유를_유지한다() {
        Fixture fixture = fixture();
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        Long rentalId = createPaidRental(api, fixture).rentalId();
        String path = "/api/v1/rentals/" + rentalId;

        assertThat(api.patch(path + "/approve", fixture.ownerToken()).statusCode()).isEqualTo(200);
        assertThat(api.post(path + "/shipping", Map.of("carrier", "통합택배", "trackingNumber", "67890"),
                fixture.ownerToken()).statusCode()).isEqualTo(200);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("image/jpeg").contentLength(12L).build());
        assertThat(api.post(path + "/receipt", Map.of(
                "productCondition", "NORMAL", "images",
                evidence(rentalId, fixture.renter().getId(), EvidenceUploadPhase.RECEIPT)),
                fixture.renterToken()).statusCode()).isEqualTo(200);
        assertThat(api.post(path + "/return-request", Map.of(), fixture.renterToken()).statusCode()).isEqualTo(200);
        assertThat(api.post(path + "/return-evidence", Map.of(
                "productCondition", "DAMAGED", "conditionDetail", "측면 긁힘", "images",
                evidence(rentalId, fixture.renter().getId(), EvidenceUploadPhase.RETURN)),
                fixture.renterToken()).statusCode()).isEqualTo(200);

        ApiResponse disputed = api.post(path + "/return-confirmation", Map.of(
                "hasIssue", true, "disputeReason", "외관 손상", "disputeDescription", "반납 시 측면에 긁힘 발견"),
                fixture.ownerToken());
        assertThat(disputed.statusCode()).isEqualTo(200);
        assertThat(disputed.json().get("disputeId").longValue()).isPositive();
        assertThat(disputed.json().get("reportId").longValue()).isPositive();
        assertThat(rentalRepository.findById(rentalId).orElseThrow().getStatus()).isEqualTo(RentalStatus.DISPUTED);
        assertThat(disputeRepository.count()).isEqualTo(1);
        assertThat(reportRepository.count()).isEqualTo(1);
        assertThat(equipmentOccupancyRepository.count()).isEqualTo(1);
        assertThat(api.post(path + "/return-confirmation", Map.of("hasIssue", false),
                fixture.ownerToken()).statusCode()).isEqualTo(409);
        assertThat(disputeRepository.count()).isEqualTo(1);
        assertThat(reportRepository.count()).isEqualTo(1);
    }

    private List<Map<String, String>> evidence(Long rentalId, Long renterId, EvidenceUploadPhase phase) {
        return Stream.of(CaptureView.FRONT, CaptureView.SIDE, CaptureView.REAR)
                .map(view -> {
                    String key = "integration/private/rental-evidence/" + rentalId + "/" + renterId + "/"
                            + phase.name().toLowerCase() + "/" + view.name().toLowerCase() + "/photo.jpg";
                    evidenceUploadRepository.saveAndFlush(new RentalEvidenceUpload(
                            rentalId, renterId, phase, key, view, "image/jpeg", 12L,
                            LocalDateTime.now().plusMinutes(10)));
                    return Map.of("captureView", view.name(), "objectKey", key);
                }).toList();
    }

    @Test
    void 대여_생성부터_결제와_등록자_승인까지_전체_흐름이_연결된다() {
        Fixture fixture = fixture();
        ApiTestClient api = new ApiTestClient(port, jsonMapper);

        ApiResponse created = createRental(api, fixture);
        assertThat(created.statusCode()).isEqualTo(201);
        Long rentalId = created.json().get("rentalId").longValue();

        ApiResponse ready = api.post(
                "/api/v1/rentals/" + rentalId + "/payment/ready",
                Map.of(),
                fixture.renterToken());
        assertThat(ready.statusCode()).isEqualTo(200);

        String orderId = ready.json().get("orderId").textValue();
        BigDecimal amount = ready.json().get("amount").decimalValue();
        ApiResponse confirmed = confirm(api, rentalId, fixture.renterToken(), orderId, amount);
        assertThat(confirmed.statusCode()).isEqualTo(200);

        ApiResponse approved = api.patch(
                "/api/v1/rentals/" + rentalId + "/approve",
                fixture.ownerToken());
        assertThat(approved.statusCode()).isEqualTo(200);

        Rental rental = rentalRepository.findById(rentalId).orElseThrow();
        Payment payment = paymentRepository.findByRentalId(rentalId).orElseThrow();
        assertThat(rental.getStatus()).isEqualTo(RentalStatus.APPROVED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(equipmentOccupancyRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.owner().getId())).isEqualTo(2);
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.renter().getId())).isEqualTo(2);
        assertThat(redisTemplate.opsForStream().size(CHAT_STREAM_KEY)).isEqualTo(1L);

        assertThat(TOSS.requests()).hasSize(1);
        RecordedRequest tossRequest = TOSS.requests().getFirst();
        assertThat(tossRequest.method()).isEqualTo("POST");
        assertThat(tossRequest.path()).isEqualTo("/v1/payments/confirm");
        assertThat(tossRequest.authorization()).startsWith("Basic ");
        assertThat(tossRequest.idempotencyKey()).isNotBlank();
        assertThat(tossRequest.body()).contains(orderId, amount.toBigIntegerExact().toString());
    }

    @Test
    void Toss_장애_후_재시도해도_같은_멱등키를_사용하고_후속_이벤트는_한번만_생긴다() {
        Fixture fixture = fixture();
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        Long rentalId = createRental(api, fixture).json().get("rentalId").longValue();
        JsonNode ready = api.post(
                "/api/v1/rentals/" + rentalId + "/payment/ready",
                Map.of(),
                fixture.renterToken()).json();
        String orderId = ready.get("orderId").textValue();
        BigDecimal amount = ready.get("amount").decimalValue();

        TOSS.failConfirm();
        ApiResponse failed = confirm(api, rentalId, fixture.renterToken(), orderId, amount);
        assertThat(failed.statusCode()).isEqualTo(502);
        assertThat(paymentRepository.findByRentalId(rentalId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(rentalRepository.findById(rentalId).orElseThrow().getStatus())
                .isEqualTo(RentalStatus.PENDING);
        assertThat(notificationRepository.count()).isZero();
        assertThat(redisTemplate.opsForStream().size(CHAT_STREAM_KEY)).isZero();

        TOSS.succeedConfirm();
        ApiResponse retried = confirm(api, rentalId, fixture.renterToken(), orderId, amount);
        assertThat(retried.statusCode()).isEqualTo(200);

        assertThat(paymentRepository.findByRentalId(rentalId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
        assertThat(rentalRepository.findById(rentalId).orElseThrow().getStatus())
                .isEqualTo(RentalStatus.REQUESTED);
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.owner().getId())).isEqualTo(2);
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.renter().getId())).isEqualTo(1);
        assertThat(redisTemplate.opsForStream().size(CHAT_STREAM_KEY)).isEqualTo(1L);

        assertThat(TOSS.requests()).hasSize(2);
        assertThat(TOSS.requests().get(0).idempotencyKey()).isNotBlank();
        assertThat(TOSS.requests().get(1).idempotencyKey())
                .isEqualTo(TOSS.requests().get(0).idempotencyKey());
    }

    @Test
    void 결제_전_대여를_취소하면_외부_환불_없이_점유만_해제된다() {
        Fixture fixture = fixture();
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        Long rentalId = createRental(api, fixture).json().get("rentalId").longValue();

        ApiResponse canceled = api.delete(
                "/api/v1/rentals/" + rentalId + "/cancel",
                fixture.renterToken());

        assertThat(canceled.statusCode()).isEqualTo(200);
        assertThat(rentalRepository.findById(rentalId).orElseThrow().getStatus())
                .isEqualTo(RentalStatus.CANCELED);
        assertThat(paymentRepository.findByRentalId(rentalId)).isEmpty();
        assertThat(equipmentOccupancyRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
        assertThat(TOSS.requests()).isEmpty();
    }

    @Test
    void 결제_후_대여자가_취소하면_Toss_환불과_점유_해제와_등록자_알림이_함께_커밋된다() {
        Fixture fixture = fixture();
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        PaidRental paid = createPaidRental(api, fixture);

        ApiResponse canceled = api.delete(
                "/api/v1/rentals/" + paid.rentalId() + "/cancel",
                fixture.renterToken());

        assertThat(canceled.statusCode()).isEqualTo(200);
        Rental rental = rentalRepository.findById(paid.rentalId()).orElseThrow();
        Payment payment = paymentRepository.findByRentalId(paid.rentalId()).orElseThrow();
        assertThat(rental.getStatus()).isEqualTo(RentalStatus.CANCELED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isNotNull();
        assertThat(payment.getCancelIdempotencyKey()).isNotBlank();
        assertThat(equipmentOccupancyRepository.count()).isZero();
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.owner().getId())).isEqualTo(3);
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.renter().getId())).isEqualTo(1);
        assertThat(redisTemplate.opsForStream().size(CHAT_STREAM_KEY)).isEqualTo(1L);

        assertCancelRequest("대여 취소");
    }

    @Test
    void 결제_후_등록자가_거절하면_Toss_환불과_점유_해제와_대여자_알림이_함께_커밋된다() {
        Fixture fixture = fixture();
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        PaidRental paid = createPaidRental(api, fixture);

        ApiResponse rejected = api.patch(
                "/api/v1/rentals/" + paid.rentalId() + "/reject",
                Map.of("reason", "해당 기간에는 장비 점검이 필요합니다."),
                fixture.ownerToken());

        assertThat(rejected.statusCode()).isEqualTo(200);
        Rental rental = rentalRepository.findById(paid.rentalId()).orElseThrow();
        Payment payment = paymentRepository.findByRentalId(paid.rentalId()).orElseThrow();
        assertThat(rental.getStatus()).isEqualTo(RentalStatus.REJECTED);
        assertThat(rental.getRejectReason()).isEqualTo("해당 기간에는 장비 점검이 필요합니다.");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(equipmentOccupancyRepository.count()).isZero();
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.owner().getId())).isEqualTo(2);
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.renter().getId())).isEqualTo(2);

        assertCancelRequest("해당 기간에는 장비 점검이 필요합니다.");
    }

    @Test
    void Toss_환불_장애_후_취소를_재시도하면_같은_멱등키로_한번만_상태를_변경한다() {
        Fixture fixture = fixture();
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        PaidRental paid = createPaidRental(api, fixture);
        String storedCancelKey = paymentRepository.findByRentalId(paid.rentalId())
                .orElseThrow()
                .getCancelIdempotencyKey();

        TOSS.failCancel();
        ApiResponse failed = api.delete(
                "/api/v1/rentals/" + paid.rentalId() + "/cancel",
                fixture.renterToken());

        assertThat(failed.statusCode()).isEqualTo(502);
        assertThat(rentalRepository.findById(paid.rentalId()).orElseThrow().getStatus())
                .isEqualTo(RentalStatus.REQUESTED);
        assertThat(paymentRepository.findByRentalId(paid.rentalId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
        assertThat(equipmentOccupancyRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.count()).isEqualTo(3);

        TOSS.succeedCancel();
        ApiResponse retried = api.delete(
                "/api/v1/rentals/" + paid.rentalId() + "/cancel",
                fixture.renterToken());
        assertThat(retried.statusCode()).isEqualTo(200);

        assertThat(rentalRepository.findById(paid.rentalId()).orElseThrow().getStatus())
                .isEqualTo(RentalStatus.CANCELED);
        assertThat(paymentRepository.findByRentalId(paid.rentalId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(notificationRepository.count()).isEqualTo(4);
        assertThat(TOSS.requests()).hasSize(3);
        assertThat(TOSS.requests().get(1).idempotencyKey()).isEqualTo(storedCancelKey);
        assertThat(TOSS.requests().get(2).idempotencyKey()).isEqualTo(storedCancelKey);
    }

    @Test
    void 위조된_웹훅_본문은_무시하고_Toss_재조회_결과로만_결제를_반영한다() {
        Fixture fixture = fixture();
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        ReadyRental ready = createReadyRental(api, fixture);
        TOSS.lookupPayment(ready.orderId(), "DONE");

        Map<String, Object> forgedWebhook = Map.of(
                "eventType", "PAYMENT_STATUS_CHANGED",
                "data", Map.of(
                        "paymentKey", "toss-payment-key",
                        "orderId", "forged-order-id",
                        "status", "CANCELED"));
        ApiResponse first = api.post(
                "/api/v1/webhooks/toss",
                forgedWebhook,
                Map.of());
        ApiResponse duplicate = api.post(
                "/api/v1/webhooks/toss",
                forgedWebhook,
                Map.of());

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(duplicate.statusCode()).isEqualTo(200);
        Payment payment = paymentRepository.findByRentalId(ready.rentalId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaymentKey()).isEqualTo("toss-payment-key");
        assertThat(rentalRepository.findById(ready.rentalId()).orElseThrow().getStatus())
                .isEqualTo(RentalStatus.REQUESTED);
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.owner().getId())).isEqualTo(2);
        assertThat(notificationRepository.countByReceiverIdAndReadFalse(fixture.renter().getId())).isEqualTo(1);
        assertThat(redisTemplate.opsForStream().size(CHAT_STREAM_KEY)).isEqualTo(1L);
        assertThat(TOSS.requests()).hasSize(2)
                .allMatch(request -> request.method().equals("GET"))
                .allMatch(request -> request.path().equals("/v1/payments/toss-payment-key"));
    }

    @Test
    void 웹훅의_Toss_재조회_orderId가_저장된_결제와_다르면_상태를_변경하지_않는다() {
        Fixture fixture = fixture();
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        ReadyRental ready = createReadyRental(api, fixture);
        TOSS.lookupPayment("unknown-order-id", "DONE");

        ApiResponse response = api.post(
                "/api/v1/webhooks/toss",
                Map.of(
                        "eventType", "PAYMENT_STATUS_CHANGED",
                        "data", Map.of(
                                "paymentKey", "toss-payment-key",
                                "orderId", ready.orderId(),
                                "status", "DONE")),
                Map.of());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(paymentRepository.findByRentalId(ready.rentalId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(rentalRepository.findById(ready.rentalId()).orElseThrow().getStatus())
                .isEqualTo(RentalStatus.PENDING);
        assertThat(notificationRepository.count()).isZero();
        assertThat(redisTemplate.opsForStream().size(CHAT_STREAM_KEY)).isZero();
    }

    private PaidRental createPaidRental(ApiTestClient api, Fixture fixture) {
        ReadyRental ready = createReadyRental(api, fixture);
        assertThat(confirm(
                api,
                ready.rentalId(),
                fixture.renterToken(),
                ready.orderId(),
                ready.amount()).statusCode()).isEqualTo(200);
        return new PaidRental(ready.rentalId());
    }

    private ReadyRental createReadyRental(ApiTestClient api, Fixture fixture) {
        ApiResponse created = createRental(api, fixture);
        assertThat(created.statusCode()).isEqualTo(201);
        Long rentalId = created.json().get("rentalId").longValue();
        ApiResponse ready = api.post(
                "/api/v1/rentals/" + rentalId + "/payment/ready",
                Map.of(),
                fixture.renterToken());
        assertThat(ready.statusCode()).isEqualTo(200);
        String orderId = ready.json().get("orderId").textValue();
        BigDecimal amount = ready.json().get("amount").decimalValue();
        return new ReadyRental(rentalId, orderId, amount);
    }

    private void assertCancelRequest(String reason) {
        assertThat(TOSS.requests()).hasSize(2);
        RecordedRequest cancelRequest = TOSS.requests().get(1);
        assertThat(cancelRequest.method()).isEqualTo("POST");
        assertThat(cancelRequest.path()).isEqualTo("/v1/payments/toss-payment-key/cancel");
        assertThat(cancelRequest.idempotencyKey()).isNotBlank();
        assertThat(cancelRequest.body()).contains(reason);
    }

    private ApiResponse createRental(ApiTestClient api, Fixture fixture) {
        LocalDate startDate = LocalDate.now().plusDays(3);
        LocalDate endDate = startDate.plusDays(2);
        return api.post(
                "/api/v1/rentals",
                Map.of(
                        "equipmentId", fixture.equipment().getId(),
                        "startDate", startDate,
                        "endDate", endDate,
                        "receiverName", "통합 테스트 대여자",
                        "receiverPhone", "010-1234-5678",
                        "zipcode", "06236",
                        "address", "서울시 강남구 테헤란로",
                        "detailAddress", "통합 테스트 101호",
                        "requestMessage", "문 앞에서 연락해주세요.",
                        "useDefaultAddress", true),
                fixture.renterToken());
    }

    private ApiResponse confirm(
            ApiTestClient api,
            Long rentalId,
            String renterToken,
            String orderId,
            BigDecimal amount
    ) {
        return api.post(
                "/api/v1/rentals/" + rentalId + "/payment/confirm",
                Map.of(
                        "paymentKey", "toss-payment-key",
                        "orderId", orderId,
                        "amount", amount),
                renterToken);
    }

    private Fixture fixture() {
        User owner = userRepository.saveAndFlush(user("owner"));
        User renter = userRepository.saveAndFlush(user("renter"));
        Equipment equipment = equipmentRepository.saveAndFlush(new Equipment(
                owner.getId(),
                EquipmentCategory.CAMERA,
                "통합 테스트 카메라",
                "대여·결제 전체 흐름 검증용 장비",
                BigDecimal.valueOf(50_000),
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(1),
                EquipmentStatus.ACTIVE,
                ProductConditionType.NORMAL));
        return new Fixture(
                owner,
                renter,
                equipment,
                jwtTokenProvider.generateAccessToken(owner.toAuthUser()),
                jwtTokenProvider.generateAccessToken(renter.toAuthUser()));
    }

    private User user(String tag) {
        return User.builder()
                .email(tag + "-" + System.nanoTime() + "@integration.test")
                .password("integration-test-password")
                .name(tag)
                .nickname(tag)
                .phone("010-0000-0000")
                .pointBalance(BigDecimal.ZERO)
                .build();
    }

    private record Fixture(
            User owner,
            User renter,
            Equipment equipment,
            String ownerToken,
            String renterToken
    ) {
    }

    private record PaidRental(Long rentalId) {
    }

    private record ReadyRental(Long rentalId, String orderId, BigDecimal amount) {
    }
}
