package iter.device;

import iter.auth.domain.entity.User;
import iter.auth.domain.repository.UserRepository;
import iter.common.image.CaptureView;
import iter.common.security.JwtTokenProvider;
import iter.device.domain.entity.EquipmentCategory;
import iter.device.domain.entity.EquipmentImage;
import iter.device.domain.repository.EquipmentImageRepository;
import iter.device.domain.repository.EquipmentRepository;
import iter.support.ApiTestClient;
import iter.support.ApiTestClient.ApiResponse;
import iter.support.MonolithIntegrationTest;
import java.math.BigDecimal;
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

// IT-DEVICE-001 (INTEGRATION_TEST_DESIGN.md) — 장비 등록 -> 이미지 연결 -> 공개 조회·검색.
//
// Given
//   활성 등록자가 있고, presigned URL로 발급받은 3장(정면·측면·후면)의 임시 업로드가
//   실제로 S3에 존재하는 것처럼(S3Client 스텁) 확인된다.
// When
//   등록자가 장비를 등록하면서 세 이미지를 제출 순서와 다르게(후면·정면·측면) 연결한다.
// Then
//   이미지는 촬영 방향 기준(정면·측면·후면)으로 재정렬되어 저장되고, 정면 사진이 썸네일이 되며,
//   공개 검색·상세 조회에서 실제 소유권과 함께 노출된다.
class EquipmentRegistrationFlowIntegrationTest extends MonolithIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private EquipmentRepository equipmentRepository;
    @Autowired
    private EquipmentImageRepository equipmentImageRepository;
    @MockitoBean
    private S3Client s3Client;

    @Test
    void 장비를_등록하면_사진이_촬영방향_순서로_연결되고_공개_검색과_상세조회에_노출된다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        String ownerToken = createOwnerToken();

        stubValidJpegObjects();
        List<Map<String, Object>> uploads = presign(api, ownerToken);
        String frontKey = objectKeyFor(uploads, "FRONT");
        String sideKey = objectKeyFor(uploads, "SIDE");
        String rearKey = objectKeyFor(uploads, "REAR");

        // 일부러 제출 순서를 뒤섞는다 — 서버가 촬영 방향 기준으로 재정렬하는지 확인하기 위함.
        List<Map<String, Object>> images = List.of(
                Map.of("captureView", "REAR", "objectKey", rearKey),
                Map.of("captureView", "FRONT", "objectKey", frontKey),
                Map.of("captureView", "SIDE", "objectKey", sideKey));

        ApiResponse created = api.post(
                "/api/v1/devices",
                Map.of(
                        "category", "CAMERA",
                        "name", "통합 테스트 카메라",
                        "description", "등록 통합 테스트용 장비",
                        "dailyPrice", 30000,
                        "availableFrom", LocalDate.now().plusDays(1).toString(),
                        "availableTo", LocalDate.now().plusMonths(1).toString(),
                        "productCondition", "NORMAL",
                        "images", images),
                ownerToken);
        assertThat(created.statusCode()).isEqualTo(201);
        long equipmentId = created.json().get("id").asLong();

        // 도메인 상태: 촬영 방향(FRONT -> SIDE -> REAR) 순서로 sortOrder가 매겨지고,
        // FRONT가 썸네일이다 — 제출 순서(REAR, FRONT, SIDE)와 무관하다. promote() 과정에서
        // objectKey는 새 공개 키로 바뀌므로(임시 키와 다름) 원본 키가 아니라 촬영 방향
        // 순서로 재배치됐는지를 확인한다.
        List<EquipmentImage> storedImages =
                equipmentImageRepository.findByEquipmentIdOrderBySortOrderAscIdAsc(equipmentId);
        assertThat(storedImages).hasSize(3);
        assertThat(storedImages).extracting(EquipmentImage::getCaptureView)
                .containsExactly(CaptureView.FRONT, CaptureView.SIDE, CaptureView.REAR);
        assertThat(storedImages).allMatch(image -> image.getObjectKey() != null && !image.getObjectKey().isBlank());
        assertThat(storedImages.get(0).getThumbnail()).isTrue();
        assertThat(storedImages.get(1).getThumbnail()).isFalse();
        assertThat(storedImages.get(2).getThumbnail()).isFalse();

        assertThat(equipmentRepository.findById(equipmentId).orElseThrow().getOwnerId())
                .isEqualTo(userRepository.findByEmail(OWNER_EMAIL).orElseThrow().getId());

        // 공개 검색: 키워드/카테고리/가격 범위 조건에 실제로 걸린다.
        ApiResponse searched = api.get(
                "/api/v1/devices?keyword=" + java.net.URLEncoder.encode("통합 테스트", java.nio.charset.StandardCharsets.UTF_8)
                        + "&category=CAMERA&minPrice=10000&maxPrice=50000",
                Map.of());
        assertThat(searched.statusCode()).isEqualTo(200);
        List<Long> foundIds = new java.util.ArrayList<>();
        searched.json().get("content").forEach(node -> foundIds.add(node.get("id").asLong()));
        assertThat(foundIds).contains(equipmentId);

        ApiResponse searchedOutOfRange = api.get(
                "/api/v1/devices?minPrice=100000&maxPrice=200000",
                Map.of());
        assertThat(searchedOutOfRange.statusCode()).isEqualTo(200);
        List<Long> outOfRangeIds = new java.util.ArrayList<>();
        searchedOutOfRange.json().get("content").forEach(node -> outOfRangeIds.add(node.get("id").asLong()));
        assertThat(outOfRangeIds).doesNotContain(equipmentId);

        // 공개 상세 조회: 이미지가 촬영 방향 순서 그대로 내려오고, 소유자 정보가 함께 붙는다.
        ApiResponse detail = api.get("/api/v1/devices/" + equipmentId, Map.of());
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.json().get("images")).hasSize(3);
        assertThat(detail.json().get("images").get(0).get("captureView").asText()).isEqualTo("FRONT");
        assertThat(detail.json().get("images").get(0).get("thumbnail").asBoolean()).isTrue();
        assertThat(detail.json().get("images").get(2).get("captureView").asText()).isEqualTo("REAR");
    }

    // ------------------------------------------------------------------

    private static final String OWNER_EMAIL = "device-owner@integration.test";

    private String createOwnerToken() {
        User owner = User.builder()
                .email(OWNER_EMAIL)
                .password("not-used-in-this-test")
                .name("장비등록자")
                .nickname("등록자")
                .phone("010-1111-2222")
                .build();
        userRepository.saveAndFlush(owner);
        return jwtTokenProvider.generateAccessToken(owner.toAuthUser());
    }

    private List<Map<String, Object>> presign(ApiTestClient api, String token) {
        List<Map<String, Object>> files = List.of(
                fileRequest("FRONT"),
                fileRequest("SIDE"),
                fileRequest("REAR"));
        ApiResponse response = api.post(
                "/api/v1/devices/images/presigned-urls",
                Map.of("files", files),
                token);
        assertThat(response.statusCode()).isEqualTo(200);

        List<Map<String, Object>> uploads = new java.util.ArrayList<>();
        response.json().get("uploads").forEach(node -> {
            Map<String, Object> upload = new LinkedHashMap<>();
            upload.put("captureView", node.get("captureView").asText());
            upload.put("objectKey", node.get("objectKey").asText());
            uploads.add(upload);
        });
        return uploads;
    }

    private Map<String, Object> fileRequest(String captureView) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("captureView", captureView);
        file.put("fileName", captureView.toLowerCase() + ".jpg");
        file.put("contentType", "image/jpeg");
        file.put("size", 1024);
        return file;
    }

    private String objectKeyFor(List<Map<String, Object>> uploads, String captureView) {
        return uploads.stream()
                .filter(upload -> captureView.equals(upload.get("captureView")))
                .findFirst()
                .orElseThrow()
                .get("objectKey")
                .toString();
    }

    // 실제 파일 내용을 S3에 올리지 않으므로, 검증 시점에 S3가 "정상적인 JPEG가 이미
    // 업로드돼 있다"고 답하도록 헤더/바이트를 스텁한다 — S3EquipmentImageStorageTest의
    // 검증 스텁과 동일한 레시피다.
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
