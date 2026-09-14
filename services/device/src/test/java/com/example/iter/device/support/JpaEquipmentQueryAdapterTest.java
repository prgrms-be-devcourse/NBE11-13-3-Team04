package com.example.iter.device.support;

import com.example.iter.device.domain.repository.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaEquipmentQueryAdapterTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @InjectMocks
    private JpaEquipmentQueryAdapter adapter;

    // 포트 주석의 "삭제된 장비를 제외하지 않는다"를 지키는 테스트.
    @Test
    void 전체_장비_수를_거르지_않고_그대로_돌려준다() {
        when(equipmentRepository.count()).thenReturn(45L);

        assertThat(adapter.count()).isEqualTo(45L);

        verify(equipmentRepository).count();
        verifyNoMoreInteractions(equipmentRepository);
    }
}
