package iter.chatbridge.event

import iter.event.contract.RentalCompletedIntegrationEvent
import iter.reservation.api.RentalQueryPort
import iter.reservation.event.RentalCompletedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Instant
import java.util.UUID

// 거래가 끝나면 chat이 그 방의 stage를 TRADE → INQUIRY로 되돌려야 한다 — 방은
// (equipmentId, requesterId)로 재사용되므로, 되돌리지 않으면 그 조합은 이후 새 문의를
// 해도 정책 필터(전화번호·계좌 마스킹)가 영영 꺼진 채로 남는다.
//
// PaymentChatEventListener와 같은 구조다: 도메인 이벤트는 rentalId만 싣고,
// 커밋 후 여기서 포트로 재조회해 통합 이벤트에 살을 붙인다.
@Component
class RentalChatEventListener(
    private val rentalQueryPort: RentalQueryPort,
    private val chatIntegrationEventPublisher: ChatIntegrationEventPublisher,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalCompleted(event: RentalCompletedEvent) {
        val rental = rentalQueryPort.find(event.rentalId).orElse(null) ?: return

        chatIntegrationEventPublisher.publishRentalCompleted(
            RentalCompletedIntegrationEvent(
                UUID.randomUUID().toString(),
                rental.rentalId,
                rental.equipmentId,
                rental.renterId,
                Instant.now(),
            ),
        )
    }
}
