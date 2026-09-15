package com.example.iter.device.support;

import com.example.iter.device.domain.entity.Equipment;
import com.example.iter.device.domain.entity.EquipmentCategory;
import com.example.iter.device.domain.entity.EquipmentImage;
import com.example.iter.device.domain.entity.EquipmentStatus;
import com.example.iter.device.domain.entity.ProductConditionType;
import com.example.iter.device.domain.repository.EquipmentImageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// "장비마다 대표 썸네일은 sortOrder 가 앞선 것" 이라는 규칙이 reservation 에서 이 어댑터로 옮겨왔다.
// RentalHistoryServiceTest 와 ReturnServiceTest 가 지키던 보장을 여기서 이어받는다.
@ExtendWith(MockitoExtension.class)
class JpaEquipmentThumbnailQueryAdapterTest {

    @Mock
    private EquipmentImageRepository equipmentImageRepository;

    @InjectMocks
    private JpaEquipmentThumbnailQueryAdapter adapter;

    @Test
    void 장비마다_앞선_썸네일_하나만_대표로_고른다() {
        Equipment first = equipment(1L);
        Equipment second = equipment(2L);
        // 쿼리가 sortOrder, id 오름차순으로 돌려주는 것을 전제한다.
        when(equipmentImageRepository.findByEquipment_IdInAndThumbnailTrueOrderBySortOrderAscIdAsc(any()))
                .thenReturn(List.of(
                        image(first, "first-old.jpg"),
                        image(first, "first-new.jpg"),
                        image(second, "second.jpg")
                ));

        Map<Long, String> thumbnails = adapter.findThumbnailUrls(Set.of(1L, 2L));

        assertThat(thumbnails).containsExactlyInAnyOrderEntriesOf(
                Map.of(1L, "first-old.jpg", 2L, "second.jpg"));
    }

    @Test
    void 썸네일이_없는_장비는_결과에서_빠진다() {
        when(equipmentImageRepository.findByEquipment_IdInAndThumbnailTrueOrderBySortOrderAscIdAsc(any()))
                .thenReturn(List.of());

        assertThat(adapter.findThumbnailUrls(Set.of(1L))).isEmpty();
    }

    // 빈 입력으로 쿼리를 날리면 where id in () 가 되어 DB 마다 동작이 다르다.
    @Test
    void 조회할_장비가_없으면_쿼리를_날리지_않는다() {
        assertThat(adapter.findThumbnailUrls(Set.of())).isEmpty();

        verifyNoInteractions(equipmentImageRepository);
    }

    @Test
    void 일괄_조회를_한_건씩_쪼개지_않는다() {
        when(equipmentImageRepository.findByEquipment_IdInAndThumbnailTrueOrderBySortOrderAscIdAsc(any()))
                .thenReturn(List.of());

        adapter.findThumbnailUrls(Set.of(1L, 2L, 3L));

        verify(equipmentImageRepository)
                .findByEquipment_IdInAndThumbnailTrueOrderBySortOrderAscIdAsc(any());
    }

    // 이 테스트가 보는 건 id 뿐이다. 나머지는 생성자의 non-null 요구를 채우는 값이다.
    private Equipment equipment(Long id) {
        return new Equipment(
                1L,
                EquipmentCategory.CAMERA,
                "테스트 장비",
                null,
                BigDecimal.valueOf(10_000),
                null,
                null,
                EquipmentStatus.ACTIVE,
                ProductConditionType.NORMAL,
                null,
                id);
    }

    private EquipmentImage image(Equipment equipment, String url) {
        return EquipmentImage.builder()
                .equipment(equipment)
                .imageUrl(url)
                .thumbnail(true)
                .build();
    }
}
