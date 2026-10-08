package iter.reservation;

import iter.auth.domain.entity.User;
import iter.auth.domain.repository.UserRepository;
import iter.device.domain.entity.Equipment;
import iter.device.domain.entity.EquipmentCategory;
import iter.device.domain.repository.EquipmentRepository;
import iter.reservation.dto.request.RentalCreateRequest;
import iter.reservation.service.RentalService;
import iter.support.MonolithIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlRentalCreationDeadlockIntegrationTest extends MonolithIntegrationTest {

    private static final int ATTEMPTS = 5;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private RentalService rentalService;

    @RepeatedTest(ATTEMPTS)
    void 서로의_장비를_동시에_대여해도_MySQL_데드락이_발생하지_않는다() throws Exception {
        User first = userRepository.saveAndFlush(user("deadlock-a"));
        User second = userRepository.saveAndFlush(user("deadlock-b"));
        Equipment ownedByFirst = equipmentRepository.saveAndFlush(equipment(first.getId(), "A의 카메라"));
        Equipment ownedBySecond = equipmentRepository.saveAndFlush(equipment(second.getId(), "B의 카메라"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            CompletableFuture<Throwable> secondRentsFromFirst =
                    attempt(executor, ready, start, second.getId(), ownedByFirst.getId());
            CompletableFuture<Throwable> firstRentsFromSecond =
                    attempt(executor, ready, start, first.getId(), ownedBySecond.getId());

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Throwable> failures = List.of(secondRentsFromFirst, firstRentsFromSecond).stream()
                    .map(CompletableFuture::join)
                    .filter(java.util.Objects::nonNull)
                    .toList();

            assertThat(failures)
                    .as("서로 다른 장비의 대여는 MySQL 행 잠금 순서와 무관하게 둘 다 성공해야 한다")
                    .isEmpty();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private CompletableFuture<Throwable> attempt(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            Long renterId,
            Long equipmentId
    ) {
        RentalCreateRequest request = new RentalCreateRequest(
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

        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            await(start);
            try {
                rentalService.createRental(renterId, request);
                return null;
            } catch (Throwable throwable) {
                return throwable;
            }
        }, executor);
    }

    private Equipment equipment(Long ownerId, String name) {
        return new Equipment(ownerId, EquipmentCategory.CAMERA, name, null, BigDecimal.valueOf(30_000));
    }

    private User user(String tag) {
        return User.builder()
                .email(tag + "-" + System.nanoTime() + "@integration.test")
                .password("test-password")
                .name("MySQL 데드락 테스트")
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
}
