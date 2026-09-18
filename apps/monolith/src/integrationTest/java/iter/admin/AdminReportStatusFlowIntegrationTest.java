package iter.admin;

import iter.auth.domain.entity.User;
import iter.auth.domain.repository.UserRepository;
import iter.common.audit.domain.entity.AdminAction;
import iter.common.audit.domain.entity.AdminActionTargetType;
import iter.common.audit.domain.entity.AdminActionType;
import iter.common.audit.domain.repository.AdminActionRepository;
import iter.common.security.JwtTokenProvider;
import iter.common.security.Role;
import iter.dispute.domain.repository.ReportRepository;
import iter.support.ApiTestClient;
import iter.support.ApiTestClient.ApiResponse;
import iter.support.MonolithIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// IT-ADMIN-001 (INTEGRATION_TEST_DESIGN.md) — 신고 조회와 상태 변경.
//
// 관리자 권한, 상태 전이 규칙(TERMINAL_STATUSES 기반), 감사 기록까지 실제 HTTP ->
// 실제 MySQL 경로로 검증한다. 자가 서비스 회원가입은 항상 USER 역할만 만들 수 있어서
// (AuthenticationFlowIntegrationTest가 이미 증명한 경로), 관리자 계정은 리포지토리로
// 직접 심어 둔다 — 이 저장소에 이런 통합 테스트 전례가 아직 없어 새로 만든다.
class AdminReportStatusFlowIntegrationTest extends MonolithIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private AdminActionRepository adminActionRepository;

    @Test
    void 관리자는_신고를_검토중으로_전환한_뒤_처리완료로_종결하고_감사기록이_남는다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        String reporterToken = createUserToken("report-reporter@integration.test", "신고자");
        User target = createUser("report-target@integration.test", "피신고자");
        String adminToken = createAdminToken("report-admin@integration.test", "관리자");

        ApiResponse created = api.post(
                "/api/v1/reports",
                Map.of(
                        "targetType", "USER",
                        "targetId", target.getId(),
                        "reason", "부적절한 행동",
                        "description", "통합 테스트에서 생성한 신고입니다."),
                reporterToken);
        assertThat(created.statusCode()).isEqualTo(201);
        long reportId = created.json().get("reportId").asLong();
        assertThat(created.json().get("status").asText()).isEqualTo("RECEIVED");

        // 관리자가 아닌 사용자는 상태를 바꿀 수 없다.
        ApiResponse forbidden = api.patch(
                "/api/v1/admin/reports/" + reportId + "/status",
                Map.of("status", "UNDER_REVIEW", "adminMemo", "권한 없는 시도"),
                reporterToken);
        assertThat(forbidden.statusCode()).isEqualTo(403);

        ApiResponse startReview = api.patch(
                "/api/v1/admin/reports/" + reportId + "/status",
                Map.of("status", "UNDER_REVIEW", "adminMemo", "검토를 시작합니다."),
                adminToken);
        assertThat(startReview.statusCode()).isEqualTo(200);
        assertThat(reportRepository.findById(reportId).orElseThrow().getStatus().name()).isEqualTo("UNDER_REVIEW");

        ApiResponse resolve = api.patch(
                "/api/v1/admin/reports/" + reportId + "/status",
                Map.of("status", "RESOLVED", "adminMemo", "확인 결과 문제 없음으로 종결합니다."),
                adminToken);
        assertThat(resolve.statusCode()).isEqualTo(200);
        assertThat(reportRepository.findById(reportId).orElseThrow().getStatus().name()).isEqualTo("RESOLVED");
        assertThat(reportRepository.findById(reportId).orElseThrow().getResolvedAt()).isNotNull();

        // 감사 기록: 검토 시작과 종결 각각 한 건씩, 총 두 건이 남는다.
        List<AdminAction> actions = adminActionRepository.searchForAdminByCursor(
                AdminActionTargetType.REPORT, reportId, null, null, null, PageRequest.of(0, 10));
        assertThat(actions).hasSize(2);
        assertThat(actions).extracting(AdminAction::getAction)
                .containsExactlyInAnyOrder(AdminActionType.REVIEW_REPORT, AdminActionType.RESOLVE_REPORT);
        assertThat(actions).allMatch(action -> action.getReason() != null && !action.getReason().isBlank());

        // 종결된 신고는 다시 상태를 바꿀 수 없고, 감사 기록도 추가로 남지 않는다.
        ApiResponse reopenAttempt = api.patch(
                "/api/v1/admin/reports/" + reportId + "/status",
                Map.of("status", "UNDER_REVIEW", "adminMemo", "다시 열어보려는 시도"),
                adminToken);
        assertThat(reopenAttempt.statusCode()).isEqualTo(409);
        assertThat(reopenAttempt.json().get("code").asText()).isEqualTo("INVALID_REPORT_STATUS_TRANSITION");

        List<AdminAction> actionsAfterInvalidAttempt = adminActionRepository.searchForAdminByCursor(
                AdminActionTargetType.REPORT, reportId, null, null, null, PageRequest.of(0, 10));
        assertThat(actionsAfterInvalidAttempt).hasSize(2);
    }

    @Test
    void 관리자_신고_목록_조회는_상태와_대상유형으로_걸러진다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        String reporterToken = createUserToken("report-list-reporter@integration.test", "목록신고자");
        User target = createUser("report-list-target@integration.test", "목록피신고자");
        String adminToken = createAdminToken("report-list-admin@integration.test", "목록관리자");

        ApiResponse created = api.post(
                "/api/v1/reports",
                Map.of(
                        "targetType", "USER",
                        "targetId", target.getId(),
                        "reason", "목록 조회 확인용",
                        "description", "목록 조회 통합 테스트용 신고입니다."),
                reporterToken);
        assertThat(created.statusCode()).isEqualTo(201);
        long reportId = created.json().get("reportId").asLong();

        ApiResponse listByStatus = api.get(
                "/api/v1/admin/reports?status=RECEIVED&targetType=USER&size=50",
                Map.of("Authorization", "Bearer " + adminToken));
        assertThat(listByStatus.statusCode()).isEqualTo(200);
        List<Long> ids = new java.util.ArrayList<>();
        listByStatus.json().get("content").forEach(node -> ids.add(node.get("reportId").asLong()));
        assertThat(ids).contains(reportId);

        ApiResponse listResolvedOnly = api.get(
                "/api/v1/admin/reports?status=RESOLVED&size=50",
                Map.of("Authorization", "Bearer " + adminToken));
        assertThat(listResolvedOnly.statusCode()).isEqualTo(200);
        List<Long> resolvedIds = new java.util.ArrayList<>();
        listResolvedOnly.json().get("content").forEach(node -> resolvedIds.add(node.get("reportId").asLong()));
        assertThat(resolvedIds).doesNotContain(reportId);
    }

    // ------------------------------------------------------------------

    private String createUserToken(String email, String name) {
        User user = createUser(email, name);
        return jwtTokenProvider.generateAccessToken(user.toAuthUser());
    }

    private String createAdminToken(String email, String name) {
        User admin = User.builder()
                .email(email)
                .password("not-used-in-this-test")
                .name(name)
                .nickname(name)
                .phone("010-0000-0000")
                .role(Role.ADMIN)
                .build();
        userRepository.saveAndFlush(admin);
        return jwtTokenProvider.generateAccessToken(admin.toAuthUser());
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
