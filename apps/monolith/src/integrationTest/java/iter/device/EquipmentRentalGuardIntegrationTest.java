package iter.device;

import iter.auth.domain.entity.User;
import iter.auth.domain.repository.UserRepository;
import iter.common.security.JwtTokenProvider;
import iter.device.domain.entity.Equipment;
import iter.device.domain.entity.EquipmentCategory;
import iter.device.domain.entity.EquipmentStatus;
import iter.device.domain.entity.ProductConditionType;
import iter.device.domain.repository.EquipmentRepository;
import iter.reservation.api.RentalStatus;
import iter.reservation.domain.entity.Rental;
import iter.reservation.domain.repository.RentalRepository;
import iter.support.ApiTestClient;
import iter.support.ApiTestClient.ApiResponse;
import iter.support.MonolithIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// IT-DEVICE-002 (INTEGRATION_TEST_DESIGN.md) — 대여 중인 장비의 상태 변경·삭제 시도.
//
// 실제 코드를 확인한 결과, 대여 중 여부는 삭제(delete)와 "대여 가능 기간을 좁히는 수정
// (update)" 두 지점에서만 막힌다 — 공개/비공개 상태 토글(PATCH .../status)은 대여 여부와
// 무관하게 항상 허용된다(EquipmentManagementService에 그런 검증이 없다). 없는 정책을
// 있다고 가정해 단언하지 않고, 실제로 존재하는 두 지점만 검증한다.
class EquipmentRentalGuardIntegrationTest extends MonolithIntegrationTest {

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
    private RentalRepository rentalRepository;

    @Test
    void 대여_중인_장비는_삭제할_수_없고_대여_기간을_벗어나는_수정도_거절된다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        User owner = createUser("device-guard-owner@integration.test", "장비등록자");
        User renter = createUser("device-guard-renter@integration.test", "대여자");
        String ownerToken = jwtTokenProvider.generateAccessToken(owner.toAuthUser());

        LocalDate availableFrom = LocalDate.now().plusDays(1);
        LocalDate availableTo = LocalDate.now().plusMonths(2);
        Equipment equipment = equipmentRepository.saveAndFlush(new Equipment(
                owner.getId(), EquipmentCategory.CAMERA, "대여 중 카메라",
                "삭제·수정 제약 검증용 장비", BigDecimal.valueOf(20_000),
                availableFrom, availableTo, EquipmentStatus.ACTIVE, ProductConditionType.NORMAL, null));

        LocalDate rentalStart = availableFrom.plusDays(1);
        LocalDate rentalEnd = rentalStart.plusDays(5);
        rentalRepository.saveAndFlush(new Rental(
                equipment.getId(), owner.getId(), renter.getId(), rentalStart, rentalEnd,
                equipment.getName(), equipment.getDailyPrice(), 5,
                equipment.getDailyPrice().multiply(BigDecimal.valueOf(5)),
                null, null, null, null, null, null, null, null,
                RentalStatus.RENTING, null, null, null));

        // 삭제 시도: 진행 중인 거래가 있어 거절된다.
        ApiResponse deleteAttempt = api.delete("/api/v1/devices/" + equipment.getId(), ownerToken);
        assertThat(deleteAttempt.statusCode()).isEqualTo(409);
        assertThat(deleteAttempt.json().get("code").asText()).isEqualTo("ACTIVE_RENTAL_EXISTS");
        assertThat(equipmentRepository.findById(equipment.getId()).orElseThrow().getStatus())
                .isNotEqualTo(EquipmentStatus.DELETED);

        // 대여 기간을 포함하지 않도록 좁히는 수정: 기존 예약이 새 기간 밖으로 밀려나므로 거절된다.
        ApiResponse narrowingUpdate = api.patch(
                "/api/v1/devices/" + equipment.getId(),
                Map.of(
                        "name", equipment.getName(),
                        "description", equipment.getDescription(),
                        "dailyPrice", equipment.getDailyPrice(),
                        "availableFrom", availableFrom.toString(),
                        "availableTo", rentalStart.minusDays(1).toString(),
                        "productCondition", "NORMAL"),
                ownerToken);
        assertThat(narrowingUpdate.statusCode()).isEqualTo(409);
        assertThat(narrowingUpdate.json().get("code").asText()).isEqualTo("ACTIVE_RENTAL_EXISTS");
        assertThat(equipmentRepository.findById(equipment.getId()).orElseThrow().getAvailableTo())
                .isEqualTo(availableTo);

        // 대여 기간과 무관한 일반 수정(설명만 변경)은 대여 중이어도 그대로 허용된다.
        ApiResponse harmlessUpdate = api.patch(
                "/api/v1/devices/" + equipment.getId(),
                Map.of(
                        "name", equipment.getName(),
                        "description", "설명만 바꾸는 수정",
                        "dailyPrice", equipment.getDailyPrice(),
                        "availableFrom", availableFrom.toString(),
                        "availableTo", availableTo.toString(),
                        "productCondition", "NORMAL"),
                ownerToken);
        assertThat(harmlessUpdate.statusCode()).isEqualTo(200);
        assertThat(equipmentRepository.findById(equipment.getId()).orElseThrow().getDescription())
                .isEqualTo("설명만 바꾸는 수정");

        // 참고용 확인(정책 단언 아님): 공개/비공개 상태 토글은 대여 중이어도 현재 코드상 막히지 않는다.
        ApiResponse statusToggle = api.patch(
                "/api/v1/devices/" + equipment.getId() + "/status",
                Map.of("status", "INACTIVE"),
                ownerToken);
        assertThat(statusToggle.statusCode()).isEqualTo(200);
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
}
