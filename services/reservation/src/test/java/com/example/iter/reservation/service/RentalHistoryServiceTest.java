package com.example.iter.reservation.service;

import com.example.iter.common.security.Role;
import com.example.iter.common.security.UserStatus;
import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.auth.api.UserSummary;
import com.example.iter.common.dto.request.PagingRequest;
import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import com.example.iter.device.api.EquipmentThumbnailQueryPort;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.api.RentalStatus;
import com.example.iter.reservation.domain.repository.RentalHistoryRepository;
import com.example.iter.reservation.dto.request.RentalHistorySearchRequest;
import com.example.iter.reservation.util.RentalHistoryMapper;
import com.example.iter.reservation.util.RentalOverduePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RentalHistoryServiceTest {

    private static final Long RENTER_ID = 1L;
    private static final Long OWNER_ID = 2L;
    private static final Long EQUIPMENT_ID = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Mock
    private RentalHistoryRepository rentalHistoryRepository;

    @Mock
    private EquipmentThumbnailQueryPort equipmentThumbnailQueryPort;

    @Mock
    private UserQueryPort userQueryPort;

    @Spy
    private RentalHistoryMapper rentalHistoryMapper = new RentalHistoryMapper();

    @Mock
    private Clock clock;

    @InjectMocks
    private RentalHistoryService rentalHistoryService;

    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-08-20T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void 빌린_장비_이력은_예약_스냅샷과_장비_등록자를_반환한다() {
        LocalDate today = TODAY;
        Rental rental = rental(
                100L,
                EQUIPMENT_ID,
                RENTER_ID,
                RentalStatus.RENTING,
                today.minusDays(3),
                "예약 당시 맥북"
        );
        UserSummary owner = user(OWNER_ID, "등록자");


        when(rentalHistoryRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(page(rental));
        when(userQueryPort.findSummaries(any())).thenReturn(Map.of(owner.userId(), owner));
        when(equipmentThumbnailQueryPort.findThumbnailUrls(anyCollection()))
                .thenReturn(Map.of(EQUIPMENT_ID, "https://example.com/macbook.jpg"));

        var response = rentalHistoryService.getBorrowedHistory(
                RENTER_ID,
                new RentalHistorySearchRequest(RentalStatus.RENTING, "  맥북  ", 0, 20)
        );

        assertThat(response.content()).hasSize(1);
        var history = response.content().getFirst();
        assertThat(history.rentalId()).isEqualTo(100L);
        assertThat(history.equipmentId()).isEqualTo(EQUIPMENT_ID);
        assertThat(history.equipmentName()).isEqualTo("예약 당시 맥북");
        assertThat(history.equipmentName()).isNotEqualTo("현재 변경된 장비명");
        assertThat(history.thumbnailUrl()).isEqualTo("https://example.com/macbook.jpg");
        assertThat(history.counterparty().userId()).isEqualTo(OWNER_ID);
        assertThat(history.counterparty().nickName()).isEqualTo("등록자");
        assertThat(history.totalPrice()).isEqualByComparingTo("30000");
        assertThat(history.overdueDays()).isEqualTo(3);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(rentalHistoryRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture()
        );
        assertThat(sortDescription(pageableCaptor.getValue()))
                .containsExactly("createdAt: DESC", "id: DESC");
    }

    @Test
    void 빌려준_장비_이력은_대여자를_상대방으로_반환한다() {
        Rental rental = rental(
                101L,
                EQUIPMENT_ID,
                RENTER_ID,
                RentalStatus.COMPLETED,
                TODAY.minusDays(10),
                "예약 당시 카메라"
        );
        UserSummary renter = user(RENTER_ID, "대여자");

        when(rentalHistoryRepository.findLentHistory(
                eq(OWNER_ID),
                eq(RentalStatus.COMPLETED),
                eq("카메라"),
                any(Pageable.class)
        )).thenReturn(page(rental));
        when(userQueryPort.findSummaries(any())).thenReturn(Map.of(renter.userId(), renter));
        when(equipmentThumbnailQueryPort.findThumbnailUrls(anyCollection()))
                .thenReturn(Map.of(EQUIPMENT_ID, "https://example.com/camera.jpg"));

        var response = rentalHistoryService.getLentHistory(
                OWNER_ID,
                new RentalHistorySearchRequest(RentalStatus.COMPLETED, "카메라", 0, 20)
        );

        assertThat(response.content()).singleElement().satisfies(history -> {
            assertThat(history.counterparty().userId()).isEqualTo(RENTER_ID);
            assertThat(history.counterparty().nickName()).isEqualTo("대여자");
            assertThat(history.equipmentName()).isEqualTo("예약 당시 카메라");
            assertThat(history.thumbnailUrl()).isEqualTo("https://example.com/camera.jpg");
            assertThat(history.overdueDays()).isZero();
        });
    }

    @Test
    void 빈_검색어는_null로_정규화하고_빈_페이지는_추가_조회하지_않는다() {
        Page<Rental> emptyPage = Page.empty(PageRequest.of(2, 5));
        when(rentalHistoryRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(emptyPage);

        var response = rentalHistoryService.getBorrowedHistory(
                RENTER_ID,
                new RentalHistorySearchRequest(null, "   ", 2, 5)
        );

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        verifyNoInteractions(equipmentThumbnailQueryPort, userQueryPort);
        verify(rentalHistoryMapper, never()).toResponse(any(), any(), any(), any(Integer.class));
    }

    @Test
    void 현재_장비가_없어도_빌린_이력은_등록자_스냅샷으로_조회한다() {
        Rental rental = rental(
                102L,
                EQUIPMENT_ID,
                RENTER_ID,
                RentalStatus.COMPLETED,
                TODAY,
                "삭제된 장비"
        );
        when(rentalHistoryRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(page(rental));
        when(userQueryPort.findSummaries(any())).thenReturn(Map.of(OWNER_ID, user(OWNER_ID, "등록자")));
        when(equipmentThumbnailQueryPort.findThumbnailUrls(anyCollection())).thenReturn(Map.of());

        var response = rentalHistoryService.getBorrowedHistory(
                RENTER_ID,
                new RentalHistorySearchRequest(null, null, 0, 20)
        );

        assertThat(response.content()).singleElement().satisfies(history -> {
            assertThat(history.equipmentName()).isEqualTo("삭제된 장비");
            assertThat(history.counterparty().userId()).isEqualTo(OWNER_ID);
            assertThat(history.thumbnailUrl()).isNull();
        });
    }

    @Test
    void 빌린_이력의_장비_등록자가_없으면_예외가_발생한다() {
        Rental rental = rental(
                103L,
                EQUIPMENT_ID,
                RENTER_ID,
                RentalStatus.COMPLETED,
                TODAY,
                "장비"
        );
        when(rentalHistoryRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(page(rental));
        when(userQueryPort.findSummaries(any())).thenReturn(Map.of());
        when(equipmentThumbnailQueryPort.findThumbnailUrls(anyCollection()))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> rentalHistoryService.getBorrowedHistory(
                RENTER_ID,
                new RentalHistorySearchRequest(null, null, 0, 20)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 빌려준_이력의_대여자가_없으면_예외가_발생한다() {
        Rental rental = rental(
                104L,
                EQUIPMENT_ID,
                RENTER_ID,
                RentalStatus.COMPLETED,
                TODAY,
                "장비"
        );
        when(rentalHistoryRepository.findLentHistory(
                eq(OWNER_ID),
                isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(page(rental));
        when(userQueryPort.findSummaries(any())).thenReturn(Map.of());
        when(equipmentThumbnailQueryPort.findThumbnailUrls(anyCollection()))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> rentalHistoryService.getLentHistory(
                OWNER_ID,
                new RentalHistorySearchRequest(null, null, 0, 20)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 빌린_장비_연체_이력은_연체_상태와_종료일_순으로_조회한다() {
        UserSummary owner = user(OWNER_ID, "등록자");

        when(rentalHistoryRepository.findByRenterIdAndEndDateBeforeAndStatusIn(
                eq(RENTER_ID),
                any(LocalDate.class),
                eq(RentalOverduePolicy.statuses()),
                any(Pageable.class)
        )).thenAnswer(invocation -> {
            LocalDate today = invocation.getArgument(1);
            Pageable pageable = invocation.getArgument(3);
            Rental overdueRental = rental(
                    105L,
                    EQUIPMENT_ID,
                    RENTER_ID,
                    RentalStatus.RETURNING,
                    today.minusDays(4),
                    "연체 장비"
            );
            return new PageImpl<>(List.of(overdueRental), pageable, 1);
        });
        when(userQueryPort.findSummaries(any())).thenReturn(Map.of(owner.userId(), owner));
        when(equipmentThumbnailQueryPort.findThumbnailUrls(anyCollection()))
                .thenReturn(Map.of());

        var response = rentalHistoryService.getBorrowedOverdueHistory(
                RENTER_ID,
                new PagingRequest(0, 20)
        );

        assertThat(response.content()).singleElement()
                .extracting(history -> history.overdueDays())
                .isEqualTo(4);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(rentalHistoryRepository).findByRenterIdAndEndDateBeforeAndStatusIn(
                eq(RENTER_ID),
                dateCaptor.capture(),
                eq(RentalOverduePolicy.statuses()),
                pageableCaptor.capture()
        );
        assertThat(dateCaptor.getValue()).isEqualTo(TODAY);
        assertThat(sortDescription(pageableCaptor.getValue()))
                .containsExactly("endDate: ASC", "createdAt: DESC", "id: DESC");
    }

    @Test
    void 빌려준_장비_연체_이력은_등록자_ID로_조회한다() {
        UserSummary renter = user(RENTER_ID, "대여자");
        when(rentalHistoryRepository.findByOwnerIdSnapshotAndEndDateBeforeAndStatusIn(
                eq(OWNER_ID),
                any(LocalDate.class),
                eq(RentalOverduePolicy.statuses()),
                any(Pageable.class)
        )).thenAnswer(invocation -> {
            LocalDate today = invocation.getArgument(1);
            Pageable pageable = invocation.getArgument(3);
            Rental overdueRental = rental(
                    106L,
                    EQUIPMENT_ID,
                    RENTER_ID,
                    RentalStatus.RENTING,
                    today.minusDays(2),
                    "연체 장비"
            );
            return new PageImpl<>(List.of(overdueRental), pageable, 1);
        });
        when(userQueryPort.findSummaries(any())).thenReturn(Map.of(renter.userId(), renter));
        when(equipmentThumbnailQueryPort.findThumbnailUrls(anyCollection()))
                .thenReturn(Map.of());

        var response = rentalHistoryService.getLentOverdueHistory(
                OWNER_ID,
                new PagingRequest(0, 20)
        );

        assertThat(response.content()).singleElement().satisfies(history -> {
            assertThat(history.counterparty().userId()).isEqualTo(RENTER_ID);
            assertThat(history.overdueDays()).isEqualTo(2);
        });
        verify(rentalHistoryRepository).findByOwnerIdSnapshotAndEndDateBeforeAndStatusIn(
                eq(OWNER_ID),
                any(LocalDate.class),
                eq(RentalOverduePolicy.statuses()),
                any(Pageable.class)
        );
    }

    @Test
    void 여러_빌린_이력도_장비_회원_썸네일을_각각_한_번만_일괄_조회한다() {
        Rental firstRental = rentalWithOwner(
                201L,
                10L,
                2L,
                RENTER_ID,
                RentalStatus.COMPLETED,
                TODAY,
                "첫 장비 스냅샷"
        );
        Rental secondRental = rentalWithOwner(
                202L,
                20L,
                3L,
                RENTER_ID,
                RentalStatus.COMPLETED,
                TODAY,
                "둘째 장비 스냅샷"
        );

        when(rentalHistoryRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(firstRental, secondRental), PageRequest.of(0, 20), 2));
        when(userQueryPort.findSummaries(any())).thenReturn(Map.of(2L, user(2L, "첫 등록자"), 3L, user(3L, "둘째 등록자")));
        // 장비마다 대표 썸네일을 고르는 규칙(sortOrder 가 앞선 것이 이김)은
        // JpaEquipmentThumbnailQueryAdapterTest 로 옮겼다. 여기서는 포트가 준 값을 그대로 쓰는지만 본다.
        when(equipmentThumbnailQueryPort.findThumbnailUrls(anyCollection()))
                .thenReturn(Map.of(10L, "first-old.jpg", 20L, "second.jpg"));

        var response = rentalHistoryService.getBorrowedHistory(
                RENTER_ID,
                new RentalHistorySearchRequest(null, null, 0, 20)
        );

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).thumbnailUrl()).isEqualTo("first-old.jpg");
        assertThat(response.content().get(1).thumbnailUrl()).isEqualTo("second.jpg");
        verify(userQueryPort, times(1)).findSummaries(any());
        verify(equipmentThumbnailQueryPort, times(1))
                .findThumbnailUrls(anyCollection());
    }

    private Page<Rental> page(Rental rental) {
        return new PageImpl<>(List.of(rental), PageRequest.of(0, 20), 1);
    }

    private Rental rental(
            Long id,
            Long equipmentId,
            Long renterId,
            RentalStatus status,
            LocalDate endDate,
            String productNameSnapshot
    ) {
        return rentalWithOwner(id, equipmentId, OWNER_ID, renterId, status, endDate, productNameSnapshot);
    }

    private Rental rentalWithOwner(
            Long id,
            Long equipmentId,
            Long ownerId,
            Long renterId,
            RentalStatus status,
            LocalDate endDate,
            String productNameSnapshot
    ) {
        return Rental.builder()
                .id(id)
                .equipmentId(equipmentId)
                .ownerIdSnapshot(ownerId)
                .renterId(renterId)
                .startDate(endDate.minusDays(2))
                .endDate(endDate)
                .productNameSnapshot(productNameSnapshot)
                .categorySnapshot("디지털기기")
                .dailyPriceSnapshot(BigDecimal.valueOf(10_000))
                .rentalDays(3)
                .totalPrice(BigDecimal.valueOf(30_000))
                .status(status)
                .build();
    }

    private UserSummary user(Long id, String nickname) {
        return new UserSummary(id, nickname);
    }


    private List<String> sortDescription(Pageable pageable) {
        return pageable.getSort().stream()
                .map(order -> order.getProperty() + ": " + order.getDirection())
                .toList();
    }
}
