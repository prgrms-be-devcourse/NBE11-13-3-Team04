package com.example.iter.dispute.support;

import com.example.iter.dispute.domain.entity.ReportStatus;
import com.example.iter.dispute.domain.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// "미처리 신고 = RECEIVED" 라는 판단이 AdminStatsService 에서 이 어댑터로 옮겨왔다.
// 원래 AdminStatsServiceTest 가 지키던 보장을 여기서 이어받는다.
@ExtendWith(MockitoExtension.class)
class JpaReportQueryAdapterTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private JpaReportQueryAdapter adapter;

    @Test
    void 접수_상태의_신고만_센다() {
        when(reportRepository.countByStatus(ReportStatus.RECEIVED)).thenReturn(7L);

        assertThat(adapter.countReceived()).isEqualTo(7L);

        verify(reportRepository).countByStatus(ReportStatus.RECEIVED);
        verify(reportRepository, never()).countByStatus(ReportStatus.UNDER_REVIEW);
        verify(reportRepository, never()).countByStatus(ReportStatus.RESOLVED);
        verify(reportRepository, never()).countByStatus(ReportStatus.REJECTED);
    }
}
