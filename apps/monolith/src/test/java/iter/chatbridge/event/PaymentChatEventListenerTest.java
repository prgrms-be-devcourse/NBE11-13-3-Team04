package iter.chatbridge.event;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import iter.device.api.EquipmentQueryPort;
import iter.event.contract.PaymentConfirmedIntegrationEvent;
import iter.payment.event.PaymentConfirmedEvent;
import iter.reservation.api.RentalInfo;
import iter.reservation.api.RentalQueryPort;
import iter.reservation.api.RentalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentChatEventListenerTest {

    private static final Long RENTAL_ID = 1L;
    private static final Long EQUIPMENT_ID = 2L;
    private static final Long RENTER_ID = 3L;
    private static final Long OWNER_ID = 4L;

    @Mock
    private RentalQueryPort rentalQueryPort;

    @Mock
    private EquipmentQueryPort equipmentQueryPort;

    @Mock
    private ChatIntegrationEventPublisher chatIntegrationEventPublisher;

    @InjectMocks
    private PaymentChatEventListener listener;

    @Test
    void 결제확정_이벤트를_받으면_대여_장비_주인_정보를_채워_통합이벤트를_발행한다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        when(equipmentQueryPort.findOwnerId(EQUIPMENT_ID)).thenReturn(Optional.of(OWNER_ID));

        listener.onPaymentConfirmed(new PaymentConfirmedEvent(RENTAL_ID));

        verify(chatIntegrationEventPublisher).publishPaymentConfirmed(argThat((PaymentConfirmedIntegrationEvent event) ->
                event.getRentalId() == RENTAL_ID
                        && event.getEquipmentId() == EQUIPMENT_ID
                        && event.getRenterId() == RENTER_ID
                        && event.getOwnerId() == OWNER_ID
        ));
    }

    @Test
    void 대여_정보를_찾을_수_없으면_발행하지_않는다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.empty());

        listener.onPaymentConfirmed(new PaymentConfirmedEvent(RENTAL_ID));

        verify(chatIntegrationEventPublisher, never()).publishPaymentConfirmed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 장비_주인을_찾을_수_없으면_발행하지_않는다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        when(equipmentQueryPort.findOwnerId(EQUIPMENT_ID)).thenReturn(Optional.empty());

        listener.onPaymentConfirmed(new PaymentConfirmedEvent(RENTAL_ID));

        verify(chatIntegrationEventPublisher, never()).publishPaymentConfirmed(org.mockito.ArgumentMatchers.any());
    }

    private RentalInfo rental() {
        return new RentalInfo(
                RENTAL_ID, EQUIPMENT_ID, OWNER_ID, RENTER_ID, "전동 드릴", null,
                RentalStatus.APPROVED, BigDecimal.valueOf(10000),
                LocalDate.now(), LocalDate.now().plusDays(3)
        );
    }
}
