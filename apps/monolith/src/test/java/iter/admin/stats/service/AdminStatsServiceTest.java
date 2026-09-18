package iter.admin.stats.service;

import iter.admin.stats.dto.response.AdminStatsResponse;
import iter.auth.api.UserQueryPort;
import iter.device.api.EquipmentQueryPort;
import iter.dispute.api.ReportQueryPort;
import iter.payment.api.PaymentQueryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock
    private UserQueryPort userQueryPort;

    @Mock
    private EquipmentQueryPort equipmentQueryPort;

    @Mock
    private ReportQueryPort reportQueryPort;

    @Mock
    private PaymentQueryPort paymentQueryPort;

    @InjectMocks
    private AdminStatsService adminStatsService;

    @Test
    void 회원_장비_미처리_신고_결제_건수를_각_도메인_포트에서_집계한다() {
        when(userQueryPort.count()).thenReturn(120L);
        when(equipmentQueryPort.count()).thenReturn(45L);
        when(reportQueryPort.countReceived()).thenReturn(3L);
        when(paymentQueryPort.count()).thenReturn(300L);

        AdminStatsResponse result = adminStatsService.getStats();

        assertThat(result).isEqualTo(AdminStatsResponse.of(120L, 45L, 3L, 300L));

        verify(userQueryPort).count();
        verify(equipmentQueryPort).count();
        verify(reportQueryPort).countReceived();
        verify(paymentQueryPort).count();
    }

    @Test
    void 각_건수를_담당_도메인_포트에_각각_묻는다() {
        when(userQueryPort.count()).thenReturn(1L);
        when(equipmentQueryPort.count()).thenReturn(2L);
        when(reportQueryPort.countReceived()).thenReturn(3L);
        when(paymentQueryPort.count()).thenReturn(4L);

        AdminStatsResponse result = adminStatsService.getStats();

        // 인자 순서가 뒤바뀌면 컴파일은 되지만 대시보드 숫자가 서로 바뀐다.
        // 네 값을 서로 다르게 주어 자리를 고정한다.
        assertThat(result.userCount()).isEqualTo(1L);
        assertThat(result.equipmentCount()).isEqualTo(2L);
        assertThat(result.unresolvedReportCount()).isEqualTo(3L);
        assertThat(result.paymentCount()).isEqualTo(4L);
    }
}
