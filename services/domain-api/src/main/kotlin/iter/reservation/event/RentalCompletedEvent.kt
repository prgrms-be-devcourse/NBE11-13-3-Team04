package iter.reservation.event

// 반납 확인까지 끝나 거래가 최종 완료(COMPLETED)됐을 때 발행한다.
// 반납에 이상이 있어 DISPUTED로 넘어간 경우는 거래가 끝난 게 아니므로 발행하지 않는다.
@JvmRecord
data class RentalCompletedEvent(val rentalId: Long)
