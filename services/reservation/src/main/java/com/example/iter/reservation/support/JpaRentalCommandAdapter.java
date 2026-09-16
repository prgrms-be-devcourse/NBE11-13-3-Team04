package com.example.iter.reservation.support;

import com.example.iter.reservation.api.RentalCommandPort;
import com.example.iter.reservation.api.RentalInfo;
import com.example.iter.reservation.api.RentalStatus;
import com.example.iter.reservation.domain.repository.RentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// reservation/api/RentalCommandPort 의 모놀리스 구현.
//
// !! propagation = MANDATORY 다 !!
// 호출자(PaymentService.confirm, TossWebhookService.handle)의 트랜잭션에 참여해야
// 결제 기록 변경과 대여 상태 변경이 함께 커밋되거나 함께 롤백된다.
// 근거는 auth/support/JpaUserLockAdapter 주석 참고.
//
// 상태 변경은 dirty checking 으로 커밋 시점에 반영된다. 호출자와 같은 영속성 컨텍스트를
// 쓰기 때문이며, REQUIRES_NEW 나 별도 EntityManager 를 쓰면 이 전제가 깨진다.
@Component
@RequiredArgsConstructor
public class JpaRentalCommandAdapter implements RentalCommandPort {

    private final RentalRepository rentalRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RentalInfo> markPaymentConfirmed(Long rentalId) {
        return rentalRepository.findById(rentalId)
                .map(rental -> {
                    rental.changeStatus(RentalStatus.REQUESTED);
                    // 변경 후 상태를 담아 돌려준다 — 호출부 응답이 전 상태를 싣지 않도록.
                    return new RentalInfo(
                            rental.getId(),
                            rental.getEquipmentId(),
                            rental.getOwnerIdSnapshot(),
                            rental.getRenterId(),
                            rental.getProductNameSnapshot(),
                            rental.getRejectReason(),
                            rental.getStatus(),
                            rental.getTotalPrice(),
                            rental.getStartDate(),
                            rental.getEndDate()
                    );
                });
    }
}
