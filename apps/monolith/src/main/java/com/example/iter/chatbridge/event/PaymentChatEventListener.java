package com.example.iter.chatbridge.event;

import com.example.iter.device.api.EquipmentQueryPort;
import com.example.iter.event.contract.PaymentConfirmedIntegrationEvent;
import com.example.iter.payment.event.PaymentConfirmedEvent;
import com.example.iter.reservation.api.RentalInfo;
import com.example.iter.reservation.api.RentalQueryPort;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 기존 PaymentConfirmedEvent(services/domain-api)를 새로 구독하는 리스너다 — 그 이벤트를
// 이미 듣는 NotificationEventListener는 건드리지 않는다(파일 수정 0, 신규 파일만 추가).
// Spring은 한 이벤트에 리스너가 여러 개 붙는 걸 그냥 지원한다.
//
// PaymentConfirmedEvent는 의도적으로 rentalId 하나만 싣는다(리스너가 커밋 후 재조회하는
// 설계) — 그래서 여기서도 같은 방식으로 포트를 통해 살을 붙인다.
@Component
@RequiredArgsConstructor
public class PaymentChatEventListener {

    private final RentalQueryPort rentalQueryPort;
    private final EquipmentQueryPort equipmentQueryPort;
    private final ChatIntegrationEventPublisher chatIntegrationEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        RentalInfo rental = rentalQueryPort.find(event.rentalId()).orElse(null);
        if (rental == null) {
            return;
        }
        Long ownerId = equipmentQueryPort.findOwnerId(rental.equipmentId()).orElse(null);
        if (ownerId == null) {
            return;
        }

        PaymentConfirmedIntegrationEvent integrationEvent = new PaymentConfirmedIntegrationEvent(
                UUID.randomUUID().toString(),
                rental.rentalId(),
                rental.equipmentId(),
                rental.renterId(),
                ownerId,
                Instant.now()
        );
        chatIntegrationEventPublisher.publishPaymentConfirmed(integrationEvent);
    }
}
