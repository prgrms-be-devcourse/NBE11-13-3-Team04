package iter.reservation.service

import iter.common.dto.request.PagingRequest
import iter.common.dto.response.PageResponse
import iter.reservation.dto.request.ReturnConfirmationRequest
import iter.reservation.dto.response.ReturnComparisonResponse
import iter.reservation.dto.response.ReturnConfirmationResponse
import iter.reservation.dto.response.ReturnTargetResponse
import org.springframework.stereotype.Service

// 기존 컨트롤러 계약을 유지하면서 조회와 상태 변경을 각각의 서비스에 위임합니다.
@Service
class ReturnService(
    private val returnQueryService: ReturnQueryService,
    private val returnConfirmationService: ReturnConfirmationService,
) {
    fun getReturnTargets(ownerId: Long, request: PagingRequest): PageResponse<ReturnTargetResponse> =
        returnQueryService.getReturnTargets(ownerId, request)

    fun getReturnComparison(userId: Long, rentalId: Long): ReturnComparisonResponse =
        returnQueryService.getReturnComparison(userId, rentalId)

    fun confirmReturn(ownerId: Long, rentalId: Long, request: ReturnConfirmationRequest): ReturnConfirmationResponse =
        returnConfirmationService.confirmReturn(ownerId, rentalId, request)
}
