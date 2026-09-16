package com.example.iter.dispute.util;

import com.example.iter.auth.api.UserSummary;
import com.example.iter.dispute.domain.entity.Report;
import com.example.iter.dispute.domain.entity.ReportStatus;
import com.example.iter.dispute.domain.entity.ReportTargetType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AdminReportMapperTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 8, 2, 11, 0);
    private static final LocalDateTime RESOLVED_AT = LocalDateTime.of(2026, 8, 2, 10, 0);

    private final ReportMapper reportMapper = new ReportMapper();
    private final AdminReportMapper adminReportMapper = new AdminReportMapper(reportMapper);

    @Test
    void 신고와_신고자를_관리자_목록_응답으로_변환한다() {
        Report report = report();
        UserSummary reporter = reporter();

        var response = adminReportMapper.toSummary(report, reporter);

        assertThat(response.reportId()).isEqualTo(10L);
        assertThat(response.reporter().userId()).isEqualTo(2L);
        assertThat(response.reporter().nickName()).isEqualTo("신고자");
        assertThat(response.targetType()).isEqualTo(ReportTargetType.EQUIPMENT);
        assertThat(response.targetId()).isEqualTo(100L);
        assertThat(response.reason()).isEqualTo("허위 장비");
        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void 기본_신고_상세에_관리자_메모와_수정시각을_추가한다() {
        Report report = report();
        UserSummary reporter = reporter();

        var response = adminReportMapper.toDetail(report, reporter);

        assertThat(response.report().reportId()).isEqualTo(10L);
        assertThat(response.report().description()).isEqualTo("허위로 등록된 장비입니다.");
        assertThat(response.report().resolvedAt()).isEqualTo(RESOLVED_AT);
        assertThat(response.adminMemo()).isEqualTo("위반 사항 확인 완료");
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
    }

    private Report report() {
        Report report = new Report(
                2L,
                ReportTargetType.EQUIPMENT,
                100L,
                "허위 장비",
                "허위로 등록된 장비입니다.",
                ReportStatus.RESOLVED,
                "위반 사항 확인 완료",
                RESOLVED_AT,
                10L
        );
        ReflectionTestUtils.setField(report, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(report, "updatedAt", UPDATED_AT);
        return report;
    }

    private UserSummary reporter() {
        return new UserSummary(2L, "신고자");
    }
}
