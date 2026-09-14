package com.example.iter.device.support;

import com.example.iter.device.domain.entity.EquipmentStatus;
import com.example.iter.device.domain.repository.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// "삭제 상태 = EquipmentStatus.DELETED" 판단이 auth(회원 탈퇴 처리)에서 이 어댑터로 옮겨왔다.
// 호출부는 이제 EquipmentStatus 를 모른다.
@ExtendWith(MockitoExtension.class)
class JpaEquipmentCommandAdapterTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @InjectMocks
    private JpaEquipmentCommandAdapter adapter;

    @Test
    void 소유자의_장비를_DELETED_상태로_바꾸고_변경_건수를_돌려준다() {
        LocalDateTime at = LocalDateTime.of(2026, 9, 14, 12, 0);
        when(equipmentRepository.updateStatusByOwnerId(7L, EquipmentStatus.DELETED, at)).thenReturn(3);

        assertThat(adapter.deactivateAllOwnedBy(7L, at)).isEqualTo(3);

        // 원본 호출과 인자가 같아야 한다. 상태를 다른 값으로 바꾸면 탈퇴한 회원의 장비가 계속 노출된다.
        verify(equipmentRepository).updateStatusByOwnerId(7L, EquipmentStatus.DELETED, at);
    }
}
