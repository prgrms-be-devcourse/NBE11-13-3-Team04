package com.example.iter.reservation;

import com.example.iter.auth.domain.entity.User;
import com.example.iter.auth.domain.repository.UserRepository;
import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import com.example.iter.device.domain.entity.Equipment;
import com.example.iter.device.domain.entity.EquipmentCategory;
import com.example.iter.device.domain.repository.EquipmentRepository;
import com.example.iter.reservation.api.RentalStatus;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.domain.repository.RentalRepository;
import com.example.iter.reservation.dto.request.RentalCreateRequest;
import com.example.iter.reservation.service.RentalService;
import com.example.iter.support.MonolithIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlRentalCreationConcurrencyIntegrationTest extends MonolithIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private RentalService rentalService;

    @Test
    void 같은_기간에_동시에_대여하면_MySQL에서_한_건만_성공한다() throws Exception {
        int requesterCount = 3;
        User owner = userRepository.saveAndFlush(user("owner"));
        Equipment equipment = equipmentRepository.saveAndFlush(equipment(owner.getId(), "소니 A7C2"));
        List<User> renters = List.of(
                userRepository.saveAndFlush(user("renter-1")),
                userRepository.saveAndFlush(user("renter-2")),
                userRepository.saveAndFlush(user("renter-3"))
        );

        RentalCreateRequest request = request(equipment.getId());
        ExecutorService executor = Executors.newFixedThreadPool(requesterCount);
        CountDownLatch ready = new CountDownLatch(requesterCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<CompletableFuture<Attempt>> futures = renters.stream()
                    .map(renter -> CompletableFuture.supplyAsync(
                            () -> attemptCreate(renter.getId(), request, ready, start), executor))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Attempt> attempts = futures.stream().map(CompletableFuture::join).toList();
            assertThat(attempts).filteredOn(Attempt::success).hasSize(1);
            assertThat(attempts)
                    .filteredOn(attempt -> !attempt.success())
                    .extracting(Attempt::errorCode)
                    .containsOnly(ErrorCode.RENTAL_PERIOD_CONFLICT);

            List<Rental> createdRentals = rentalRepository.findAll().stream()
                    .filter(rental -> Objects.equals(rental.getEquipmentId(), equipment.getId()))
                    .toList();
            assertThat(createdRentals).singleElement()
                    .extracting(Rental::getStatus)
                    .isEqualTo(RentalStatus.PENDING);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Attempt attemptCreate(
            Long renterId,
            RentalCreateRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            rentalService.createRental(renterId, request);
            return new Attempt(true, null);
        } catch (CustomException exception) {
            return new Attempt(false, exception.getErrorCode());
        }
    }

    private RentalCreateRequest request(Long equipmentId) {
        return new RentalCreateRequest(
                equipmentId,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(15),
                "수령인",
                "010-0000-0000",
                "12345",
                "서울시 테스트구",
                "101호",
                "문 앞",
                true
        );
    }

    private Equipment equipment(Long ownerId, String name) {
        return new Equipment(ownerId, EquipmentCategory.CAMERA, name, null, BigDecimal.valueOf(30_000));
    }

    private User user(String tag) {
        return User.builder()
                .email(tag + "-" + System.nanoTime() + "@integration.test")
                .password("test-password")
                .name("MySQL 동시성 테스트")
                .pointBalance(BigDecimal.ZERO)
                .build();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("동시 요청 시작 신호를 제한 시간 안에 받지 못했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시 요청 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }

    private record Attempt(boolean success, ErrorCode errorCode) {
    }
}
