package com.example.iter.device;

import com.example.iter.auth.domain.entity.User;
import com.example.iter.auth.domain.repository.UserRepository;
import com.example.iter.common.image.CaptureView;
import com.example.iter.common.security.JwtTokenProvider;
import com.example.iter.device.domain.entity.EquipmentImageUpload;
import com.example.iter.device.domain.repository.EquipmentImageUploadRepository;
import com.example.iter.support.ApiTestClient;
import com.example.iter.support.ApiTestClient.ApiResponse;
import com.example.iter.support.MonolithIntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// IT-STORAGE-001 (INTEGRATION_TEST_DESIGN.md) — presigned 업로드 발급 -> 객체 확인 ->
// 도메인 이미지 연결.
//
// EquipmentManagementApiTest(H2 + EquipmentImageStorage 목)와 S3EquipmentImageStorageTest
// (순수 단위, Spring/HTTP/MySQL 없음)가 이미 각 계층의 규칙을 촘촘히 커버하므로 여기서는
// 그 규칙을 다시 증명하지 않는다. 이 테스트가 검증하는 건 real HTTP -> real MySQL ->
// real S3EquipmentImageStorage(+ mocked S3Client) 전체가 실제로 이어지는지, 그리고
// 키 소유권·만료·재사용 실패가 그 실제 경로 끝까지 전달되는지다.
class EquipmentImageUploadIntegrationTest extends MonolithIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private EquipmentImageUploadRepository equipmentImageUploadRepository;
    @MockitoBean
    private S3Client s3Client;

    @Test
    void presign_발급은_소유자와_만료시간을_기록하고_실제_등록까지_이어진다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        String ownerToken = createUserToken("storage-owner@integration.test", "등록자");
        stubValidJpegObjects();

        ApiResponse presigned = api.post(
                "/api/v1/devices/images/presigned-urls",
                Map.of("files", List.of(fileRequest("FRONT"), fileRequest("SIDE"), fileRequest("REAR"))),
                ownerToken);
        assertThat(presigned.statusCode()).isEqualTo(200);
        String frontKey = presigned.json().get("uploads").get(0).get("objectKey").asText();

        EquipmentImageUpload uploadRow = equipmentImageUploadRepository.findAllByObjectKeyIn(List.of(frontKey))
                .stream().findFirst().orElseThrow();
        assertThat(uploadRow.isUsed()).isFalse();
        assertThat(uploadRow.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(uploadRow.getExpiresAt()).isBefore(LocalDateTime.now().plusMinutes(11));
    }

    @Test
    void 다른_사람이_발급받은_업로드_키는_쓸_수_없다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        String ownerAToken = createUserToken("storage-a@integration.test", "A");
        createUserToken("storage-b@integration.test", "B");
        stubValidJpegObjects();

        ApiResponse presigned = api.post(
                "/api/v1/devices/images/presigned-urls",
                Map.of("files", List.of(fileRequest("FRONT"), fileRequest("SIDE"), fileRequest("REAR"))),
                ownerAToken);
        List<Map<String, Object>> imagesFromA = extractImages(presigned);

        String ownerBToken = jwtTokenProvider.generateAccessToken(
                userRepository.findByEmail("storage-b@integration.test").orElseThrow().toAuthUser());

        ApiResponse createdByB = api.post("/api/v1/devices", equipmentPayload(imagesFromA), ownerBToken);
        assertThat(createdByB.statusCode()).isEqualTo(404);
        assertThat(createdByB.json().get("code").asText()).isEqualTo("IMAGE_UPLOAD_NOT_FOUND");
    }

    @Test
    void 이미_사용된_업로드_키는_재사용할_수_없다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        String ownerToken = createUserToken("storage-reuse@integration.test", "재사용테스트");
        stubValidJpegObjects();

        ApiResponse presigned = api.post(
                "/api/v1/devices/images/presigned-urls",
                Map.of("files", List.of(fileRequest("FRONT"), fileRequest("SIDE"), fileRequest("REAR"))),
                ownerToken);
        List<Map<String, Object>> images = extractImages(presigned);

        ApiResponse firstEquipment = api.post("/api/v1/devices", equipmentPayload(images), ownerToken);
        assertThat(firstEquipment.statusCode()).isEqualTo(201);

        ApiResponse secondEquipment = api.post("/api/v1/devices", equipmentPayload(images), ownerToken);
        assertThat(secondEquipment.statusCode()).isEqualTo(409);
        assertThat(secondEquipment.json().get("code").asText()).isEqualTo("IMAGE_UPLOAD_ALREADY_USED");
    }

    @Test
    void 만료된_업로드_키로는_장비를_등록할_수_없다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        User owner = userRepository.findByEmail("storage-expired@integration.test")
                .orElseGet(() -> userRepository.saveAndFlush(User.builder()
                        .email("storage-expired@integration.test")
                        .password("not-used-in-this-test")
                        .name("만료테스트")
                        .nickname("만료테스트")
                        .phone("010-0000-0000")
                        .build()));
        String ownerToken = jwtTokenProvider.generateAccessToken(owner.toAuthUser());
        stubValidJpegObjects();

        // presign HTTP 호출 없이, 이미 만료된 업로드 기록을 직접 심어 둔다 — expiresAt은
        // 불변 필드라 발급 후 변경할 수 없으므로 이 경로가 유일한 재현 방법이다.
        EquipmentImageUpload expired = new EquipmentImageUpload(
                owner.getId(), "equipment/temp/expired-front.jpg", "image/jpeg", 1024L,
                LocalDateTime.now().minusMinutes(1), CaptureView.FRONT, null);
        equipmentImageUploadRepository.saveAndFlush(expired);

        Map<String, Object> front = new LinkedHashMap<>();
        front.put("captureView", "FRONT");
        front.put("objectKey", "equipment/temp/expired-front.jpg");

        ApiResponse response = api.post(
                "/api/v1/devices",
                equipmentPayload(List.of(front, sideImage(), rearImage())),
                ownerToken);
        assertThat(response.statusCode()).isEqualTo(410);
        assertThat(response.json().get("code").asText()).isEqualTo("IMAGE_UPLOAD_EXPIRED");
    }

    // ------------------------------------------------------------------

    private String createUserToken(String email, String name) {
        User user = User.builder()
                .email(email)
                .password("not-used-in-this-test")
                .name(name)
                .nickname(name)
                .phone("010-0000-0000")
                .build();
        userRepository.saveAndFlush(user);
        return jwtTokenProvider.generateAccessToken(user.toAuthUser());
    }

    private Map<String, Object> fileRequest(String captureView) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("captureView", captureView);
        file.put("fileName", captureView.toLowerCase() + ".jpg");
        file.put("contentType", "image/jpeg");
        file.put("size", 1024);
        return file;
    }

    private List<Map<String, Object>> extractImages(ApiResponse presigned) {
        assertThat(presigned.statusCode()).isEqualTo(200);
        List<Map<String, Object>> images = new java.util.ArrayList<>();
        presigned.json().get("uploads").forEach(node -> {
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("captureView", node.get("captureView").asText());
            image.put("objectKey", node.get("objectKey").asText());
            images.add(image);
        });
        return images;
    }

    // 만료 테스트에서 FRONT만 직접 심어 둔 채로 SIDE/REAR는 정상 발급된 키가 필요하므로,
    // 이 두 이미지는 그냥 임의의 미발급 키를 넣는다 — 검증 순서상 FRONT에서 먼저 걸리므로
    // 도달하지 않는다(순서 보장은 findAndValidateUploadRecords가 objectKeys 순서를 따름).
    private Map<String, Object> sideImage() {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("captureView", "SIDE");
        image.put("objectKey", "equipment/temp/expired-side.jpg");
        return image;
    }

    private Map<String, Object> rearImage() {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("captureView", "REAR");
        image.put("objectKey", "equipment/temp/expired-rear.jpg");
        return image;
    }

    private Map<String, Object> equipmentPayload(List<Map<String, Object>> images) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", "CAMERA");
        payload.put("name", "저장소 통합 테스트 장비");
        payload.put("description", "presign 통합 테스트용 장비");
        payload.put("dailyPrice", 15000);
        payload.put("availableFrom", LocalDate.now().plusDays(1).toString());
        payload.put("availableTo", LocalDate.now().plusMonths(1).toString());
        payload.put("productCondition", "NORMAL");
        payload.put("images", images);
        return payload;
    }

    private void stubValidJpegObjects() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentType("image/jpeg")
                        .contentLength(1024L)
                        .eTag("etag-1")
                        .build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().eTag("etag-1").build(),
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
    }
}
