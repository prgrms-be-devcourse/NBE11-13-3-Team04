package iter.payment.api

import java.util.Optional

// payment 가 다른 도메인에게 공개하는 결제 변경 창구.
interface PaymentCommandPort {

    // 결제가 완료된 건이면 취소(환불)하고, 그렇지 않으면 아무것도 하지 않는다.
    // 어느 쪽이든 처리 "후" 상태를 돌려준다. 결제 기록이 없으면 빈 값.
    //
    // 이 메서드는 단순 위임이 아니라 소유권 이전이다. 이전에는 reservation 이
    // 토스 취소 API 호출과 멱등키 생성(ensureCancelIdempotencyKey), markRefunded 를
    // 직접 했다. 멱등키 생성 규칙이 대여 코드에서 트리거되는 것은 경계 침범이다.
    //
    // !! 토스 취소 실패는 예외로 전파해야 한다 !!
    // 돈이 안 빠졌는데 대여만 취소되면 상태가 어긋난다.
    // TossApiException -> CustomException(TOSS_PAYMENT_FAILED) 매핑을 구현이 유지한다.
    //
    // rentalId 는 nullable 로 선언한다 — 구현체(JpaPaymentCommandAdapter)가 이번 라운드에서는
    // 아직 자바라 Long 파라미터가 boxed 참조형이다. 코틀린 non-null Long 은 바이트코드에서
    // primitive long 으로 컴파일돼 자바 구현체의 override 시그니처가 깨진다.
    //
    // reason 도 nullable 이다 — RentalService.rejectRental 이 사유 없이 거절되면 null 을
    // 그대로 흘려보낸다. 자바 시그니처도 원래 boxed 참조형이라 null 을 그대로 허용했다.
    fun cancelIfPaid(rentalId: Long?, reason: String?): Optional<PaymentStatus>
}
