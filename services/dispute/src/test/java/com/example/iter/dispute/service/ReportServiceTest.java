package com.example.iter.dispute.service;

import com.example.iter.common.security.Role;
import com.example.iter.auth.domain.entity.User;
import com.example.iter.auth.domain.entity.UserFixtures;
import com.example.iter.common.security.UserStatus;
import com.example.iter.auth.api.UserLockPort;
import com.example.iter.auth.api.UserLockView;
import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.auth.api.UserSummary;
import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import com.example.iter.dispute.domain.entity.Report;
import com.example.iter.dispute.domain.entity.ReportStatus;
import com.example.iter.dispute.domain.entity.ReportTargetType;
import com.example.iter.dispute.domain.repository.ReportRepository;
import com.example.iter.dispute.dto.request.ReportCreateRequest;
import com.example.iter.dispute.dto.request.ReportSearchRequest;
import com.example.iter.dispute.util.ReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long REPORTER_ID = 1L;
    private static final Long TARGET_ID = 100L;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserQueryPort userQueryPort;

    @Mock
    private UserLockPort userLockPort;

    @Mock
    private ReportTargetValidator reportTargetValidator;

    @Spy
    private ReportMapper reportMapper = new ReportMapper();

    @InjectMocks
    private ReportService reportService;

    @Test
    void ACTIVE_회원은_신고를_접수할_수_있다() {
        User reporter = reporter(UserStatus.ACTIVE);
        ReportCreateRequest request = createRequest();

        when(userLockPort.lockAll(List.of(REPORTER_ID)))
                .thenReturn(Map.of(REPORTER_ID, new UserLockView(REPORTER_ID, UserStatus.ACTIVE)));
        when(userQueryPort.findSummary(REPORTER_ID))
                .thenReturn(Optional.of(new UserSummary(REPORTER_ID, "신고자")));
        when(reportRepository.existsActiveReport(
                eq(REPORTER_ID),
                eq(ReportTargetType.EQUIPMENT),
                eq(TARGET_ID),
                anyCollection()
        )).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = reportService.createReport(REPORTER_ID, request);

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(reportCaptor.capture());
        Report savedReport = reportCaptor.getValue();

        assertThat(savedReport.getReporterId()).isEqualTo(REPORTER_ID);
        assertThat(savedReport.getTargetType()).isEqualTo(ReportTargetType.EQUIPMENT);
        assertThat(savedReport.getTargetId()).isEqualTo(TARGET_ID);
        assertThat(savedReport.getReason()).isEqualTo("허위 정보");
        assertThat(savedReport.getDescription()).isEqualTo("실제 장비 상태가 설명과 다릅니다.");
        assertThat(savedReport.getStatus()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(response.reporter().userId()).isEqualTo(REPORTER_ID);
        assertThat(response.status()).isEqualTo(ReportStatus.RECEIVED);

        verify(reportTargetValidator).validate(
                ReportTargetType.EQUIPMENT,
                TARGET_ID,
                REPORTER_ID
        );
    }

    @Test
    void ACTIVE_관리자도_일반_신고를_접수할_수_있다() {
        User admin = reporter(UserStatus.ACTIVE, Role.ADMIN);

        when(userLockPort.lockAll(List.of(REPORTER_ID)))
                .thenReturn(Map.of(REPORTER_ID, new UserLockView(REPORTER_ID, UserStatus.ACTIVE)));
        when(userQueryPort.findSummary(REPORTER_ID))
                .thenReturn(Optional.of(new UserSummary(REPORTER_ID, "신고자")));
        when(reportRepository.existsActiveReport(
                eq(REPORTER_ID),
                eq(ReportTargetType.EQUIPMENT),
                eq(TARGET_ID),
                anyCollection()
        )).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = reportService.createReport(REPORTER_ID, createRequest());

        assertThat(response.reporter().userId()).isEqualTo(REPORTER_ID);
        assertThat(response.status()).isEqualTo(ReportStatus.RECEIVED);
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void SUSPENDED_회원은_신고를_접수할_수_없다() {
        when(userLockPort.lockAll(List.of(REPORTER_ID)))
                .thenReturn(Map.of(REPORTER_ID, new UserLockView(REPORTER_ID, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> reportService.createReport(REPORTER_ID, createRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_SUSPENDED);

        verifyNoInteractions(reportTargetValidator, reportRepository);
    }

    @Test
    void DELETED_회원은_신고를_접수할_수_없다() {
        when(userLockPort.lockAll(List.of(REPORTER_ID)))
                .thenReturn(Map.of(REPORTER_ID, new UserLockView(REPORTER_ID, UserStatus.DELETED)));

        assertThatThrownBy(() -> reportService.createReport(REPORTER_ID, createRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_DELETED);

        verifyNoInteractions(reportTargetValidator, reportRepository);
    }

    @Test
    void 같은_대상에_처리_중인_신고가_있으면_중복_접수할_수_없다() {
        when(userLockPort.lockAll(List.of(REPORTER_ID)))
                .thenReturn(Map.of(REPORTER_ID, new UserLockView(REPORTER_ID, UserStatus.ACTIVE)));
        // 닉네임 조회를 스텁하지 않는다 — 중복으로 걸리면 응답을 조립하지 않으므로 호출되지 않는다.
        when(reportRepository.existsActiveReport(
                eq(REPORTER_ID),
                eq(ReportTargetType.EQUIPMENT),
                eq(TARGET_ID),
                anyCollection()
        )).thenReturn(true);

        assertThatThrownBy(() -> reportService.createReport(REPORTER_ID, createRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_ACTIVE_REPORT);

        verify(reportRepository, never()).save(any(Report.class));
    }

    @Test
    void 내_신고_목록은_검색_조건과_페이징_정보로_조회한다() {
        User reporter = reporter(UserStatus.SUSPENDED);
        Report report = report(10L, REPORTER_ID, ReportStatus.RECEIVED);
        ReportSearchRequest request = new ReportSearchRequest(
                ReportTargetType.EQUIPMENT,
                ReportStatus.RECEIVED,
                0,
                20
        );

        when(userQueryPort.findSummary(REPORTER_ID))
                .thenReturn(Optional.of(new UserSummary(REPORTER_ID, reporter.getNickname())));
        when(reportRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1));

        var response = reportService.getMyReports(REPORTER_ID, request);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().reportId()).isEqualTo(10L);
        assertThat(response.content().getFirst().reporter().userId()).isEqualTo(REPORTER_ID);
        assertThat(response.page()).isZero();
        assertThat(response.totalElements()).isEqualTo(1);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(reportRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void 내_신고_상세를_조회한다() {
        User reporter = reporter(UserStatus.ACTIVE);
        Report report = report(10L, REPORTER_ID, ReportStatus.UNDER_REVIEW);

        when(userQueryPort.findSummary(REPORTER_ID))
                .thenReturn(Optional.of(new UserSummary(REPORTER_ID, reporter.getNickname())));
        when(reportRepository.findByIdAndReporterId(10L, REPORTER_ID))
                .thenReturn(Optional.of(report));

        var response = reportService.getMyReport(REPORTER_ID, 10L);

        assertThat(response.reportId()).isEqualTo(10L);
        assertThat(response.reporter().userId()).isEqualTo(REPORTER_ID);
        assertThat(response.status()).isEqualTo(ReportStatus.UNDER_REVIEW);
    }

    @Test
    void 존재하지_않거나_다른_사용자의_신고는_상세_조회할_수_없다() {
        when(userQueryPort.findSummary(REPORTER_ID))
                .thenReturn(Optional.of(new UserSummary(REPORTER_ID, "신고자")));
        when(reportRepository.findByIdAndReporterId(10L, REPORTER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getMyReport(REPORTER_ID, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    private User reporter(UserStatus status) {
        return reporter(status, Role.USER);
    }

    private User reporter(UserStatus status, Role role) {
        return UserFixtures.user(REPORTER_ID, "신고자", "신고자닉네임", status, role);
    }

    private ReportCreateRequest createRequest() {
        return new ReportCreateRequest(
                ReportTargetType.EQUIPMENT,
                TARGET_ID,
                "  허위 정보  ",
                "  실제 장비 상태가 설명과 다릅니다.  "
        );
    }

    private Report report(Long reportId, Long reporterId, ReportStatus status) {
        return Report.builder()
                .id(reportId)
                .reporterId(reporterId)
                .targetType(ReportTargetType.EQUIPMENT)
                .targetId(TARGET_ID)
                .reason("허위 정보")
                .description("실제 장비 상태가 설명과 다릅니다.")
                .status(status)
                .build();
    }
}
