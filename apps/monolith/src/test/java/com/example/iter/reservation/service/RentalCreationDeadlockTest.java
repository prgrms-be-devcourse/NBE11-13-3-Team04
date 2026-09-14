package com.example.iter.reservation.service;

import com.example.iter.auth.domain.entity.User;
import com.example.iter.auth.domain.repository.UserRepository;
import com.example.iter.device.domain.entity.Equipment;
import com.example.iter.device.domain.entity.EquipmentCategory;
import com.example.iter.device.domain.repository.EquipmentRepository;
import com.example.iter.reservation.dto.request.RentalCreateRequest;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

// createRental()은 대여자와 장비 소유자 두 회원 행에 SELECT ... FOR UPDATE 를 건다.
// 두 트랜잭션이 같은 두 회원을 "반대 순서"로 잠그면 MySQL 데드락이 난다.
//
// A가 B의 장비를, B가 A의 장비를 동시에 빌리는 상황이 정확히 그 경우다:
//   스레드 1: renter=A, owner=B  ->  A, B 순으로 잠그고 싶어진다
//   스레드 2: renter=B, owner=A  ->  B, A 순으로 잠그고 싶어진다
//
// RentalService 는 Math.min/Math.max 로 항상 ID 오름차순으로 잠가 이를 피한다.
// 그런데 그 3줄을 지워도 컴파일과 나머지 테스트 전체가 통과한다 —
// 기존 RentalCreationConcurrencyTest 는 소유자 1명·대여자 3명 구조라
// 모든 스레드가 같은 상대 순서로 잠그기 때문에 정렬 분기를 타지 않는다.
//
// 이 테스트가 그 3줄의 유일한 안전망이다. 정렬을 없애면 여기서 깨져야 한다.
//
// 데드락은 타이밍에 의존하므로 @RepeatedTest 로 여러 번 시도한다.
// 두 대여 모두 성공해야 하며, 데드락이 나면 MySQL 이 한쪽을 희생시켜
// CannotAcquireLockException 계열로 실패한다.
@ActiveProfiles("test")
@SpringBootTest
class RentalCreationDeadlockTest {

    private static final int ATTEMPTS = 10;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EquipmentRepository equipmentRepository;
    @Autowired
    private RentalService rentalService;

    @RepeatedTest(ATTEMPTS)
    void 서로의_장비를_동시에_대여해도_데드락이_나지_않는다() throws InterruptedException {
        User first = userRepository.save(user("deadlock-a-" + System.nanoTime()));
        User second = userRepository.save(user("deadlock-b-" + System.nanoTime()));

        Equipment ownedByFirst = equipmentRepository.save(equipment(first.getId(), "A의 카메라"));
        Equipment ownedBySecond = equipmentRepository.save(equipment(second.getId(), "B의 카메라"));

        // second 가 A의 장비를, first 가 B의 장비를 동시에 빌린다.
        // 각 스레드가 잠가야 하는 회원 쌍은 같고(first, second) 역할만 반대다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Throwable> secondRentsFromFirst =
                attempt(executor, ready, start, second.getId(), ownedByFirst.getId());
        CompletableFuture<Throwable> firstRentsFromSecond =
                attempt(executor, ready, start, first.getId(), ownedBySecond.getId());

        ready.await();
        start.countDown();

        List<Throwable> failures = List.of(secondRentsFromFirst, firstRentsFromSecond).stream()
                .map(CompletableFuture::join)
                .filter(java.util.Objects::nonNull)
                .toList();
        executor.shutdown();

        assertThat(failures)
                .as("두 대여는 서로 다른 장비를 쓰므로 기간 충돌이 없다. 실패했다면 회원 행 락 순서 문제다.")
                .isEmpty();
    }

    private CompletableFuture<Throwable> attempt(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            Long renterId,
            Long equipmentId
    ) {
        RentalCreateRequest request = new RentalCreateRequest(
                equipmentId, LocalDate.now().plusDays(10), LocalDate.now().plusDays(15),
                "홍길동", "010-0000-0000", "12345", "서울시", "101동", "문 앞", true);

        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            awaitUninterruptibly(start);
            try {
                rentalService.createRental(renterId, request);
                return null;
            } catch (Throwable e) {
                return e;
            }
        }, executor);
    }

    private Equipment equipment(Long ownerId, String name) {
        return Equipment.builder()
                .ownerId(ownerId)
                .category(EquipmentCategory.CAMERA)
                .name(name)
                .dailyPrice(BigDecimal.valueOf(30000))
                .build();
    }

    private User user(String tag) {
        return User.builder()
                .email(tag + "@test.com")
                .password("test-password")
                .name("데드락테스트")
                .pointBalance(BigDecimal.ZERO)
                .build();
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
