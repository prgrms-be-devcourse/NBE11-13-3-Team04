package iter.device.service;

import iter.auth.api.UserQueryPort;
import iter.auth.api.UserSummary;
import iter.common.audit.domain.entity.AdminActionTargetType;
import iter.common.audit.domain.entity.AdminActionType;
import iter.common.audit.service.AdminActionService;
import iter.common.exception.CustomException;
import iter.common.exception.ErrorCode;
import iter.device.domain.entity.Equipment;
import iter.device.domain.entity.EquipmentCategory;
import iter.device.domain.entity.EquipmentStatus;
import iter.device.domain.entity.ProductConditionType;
import iter.device.domain.repository.EquipmentImageRepository;
import iter.device.domain.repository.EquipmentRepository;
import iter.device.dto.request.AdminEquipmentStatusRequest;
import iter.device.util.AdminEquipmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEquipmentServiceStatusTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long OWNER_ID = 2L;
    private static final Long EQUIPMENT_ID = 10L;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private EquipmentImageRepository equipmentImageRepository;

    @Mock
    private UserQueryPort userQueryPort;

    @Mock
    private AdminActionService adminActionService;

    @Mock
    private AdminEquipmentMapper adminEquipmentMapper;

    @InjectMocks
    private AdminEquipmentService adminEquipmentService;

    @Test
    void 차단을_해제하면_INACTIVE_상태로_복구한다() {
        Equipment equipment = equipment(EquipmentStatus.SUSPENDED);
        UserSummary owner = new UserSummary(OWNER_ID, "등록자");
        AdminEquipmentStatusRequest request = new AdminEquipmentStatusRequest(
                EquipmentStatus.INACTIVE,
                "차단 사유 해소"
        );

        when(equipmentRepository.findByIdForUpdate(EQUIPMENT_ID)).thenReturn(Optional.of(equipment));
        when(userQueryPort.findSummary(OWNER_ID)).thenReturn(Optional.of(owner));
        when(equipmentImageRepository.findByEquipmentIdOrderBySortOrderAsc(EQUIPMENT_ID))
                .thenReturn(List.of());

        assertThat(request.isAllowedStatus()).isTrue();
        adminEquipmentService.updateEquipmentStatus(ADMIN_ID, EQUIPMENT_ID, request);

        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.INACTIVE);
        verify(adminActionService).record(
                ADMIN_ID,
                AdminActionTargetType.EQUIPMENT,
                EQUIPMENT_ID,
                AdminActionType.RESTORE_EQUIPMENT,
                "차단 사유 해소"
        );
        verify(equipmentRepository).flush();
    }

    @Test
    void 차단된_장비를_ACTIVE로_바로_복구할_수_없다() {
        Equipment equipment = equipment(EquipmentStatus.SUSPENDED);
        AdminEquipmentStatusRequest request = new AdminEquipmentStatusRequest(
                EquipmentStatus.ACTIVE,
                "잘못된 복구 요청"
        );
        when(equipmentRepository.findByIdForUpdate(EQUIPMENT_ID)).thenReturn(Optional.of(equipment));

        assertThat(request.isAllowedStatus()).isFalse();
        assertThatThrownBy(() -> adminEquipmentService.updateEquipmentStatus(
                ADMIN_ID,
                EQUIPMENT_ID,
                request
        ))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_EQUIPMENT_STATUS_TRANSITION)
                );

        verify(adminActionService, never()).record(
                ADMIN_ID,
                AdminActionTargetType.EQUIPMENT,
                EQUIPMENT_ID,
                AdminActionType.RESTORE_EQUIPMENT,
                "잘못된 복구 요청"
        );
    }

    private Equipment equipment(EquipmentStatus status) {
        return new Equipment(
                OWNER_ID,
                EquipmentCategory.CAMERA,
                "테스트 장비",
                null,
                BigDecimal.valueOf(10_000),
                null,
                null,
                status,
                ProductConditionType.NORMAL,
                null,
                EQUIPMENT_ID);
    }
}
