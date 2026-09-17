package com.example.iter.admin.payment.service

import com.example.iter.admin.payment.dto.AdminPaymentDetailResponse
import com.example.iter.admin.payment.dto.AdminPaymentSummaryResponse
import com.example.iter.admin.payment.model.AdminPaymentSummaryRow
import com.example.iter.admin.payment.repository.AdminPaymentQueryRepository
import com.example.iter.admin.payment.util.AdminPaymentMapper
import com.example.iter.common.dto.response.CursorPageResponse
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.pagination.CursorCodec
import com.example.iter.common.pagination.CursorKey
import com.example.iter.payment.dto.request.AdminPaymentSearchRequest
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class AdminPaymentQueryService(
    private val adminPaymentQueryRepository: AdminPaymentQueryRepository,
    private val adminPaymentMapper: AdminPaymentMapper
) {
    // 검색 조건을 정규화하고 결제 생성 시각·ID 기반 커서로 관리자 결제 목록을 조회합니다.
    @Transactional(readOnly = true)
    fun getPayments(request: AdminPaymentSearchRequest): CursorPageResponse<AdminPaymentSummaryResponse> {
        val keyword = normalize(request.keyword)
        val cursorKey = CursorCodec.decode(request.cursor)
        val pageRequest = PageRequest.of(0, request.size + 1)

        // 검색어가 없을 때는 LOCATE가 없는 전용 쿼리를 사용해 정렬 인덱스 활용 가능성을 보존합니다.
        val payments: List<AdminPaymentSummaryRow> = if (keyword == null) {
            adminPaymentQueryRepository.searchWithoutKeywordForAdminByCursor(
                request.status,
                toStartOfDay(request.fromDate),
                toNextStartOfDay(request.toDate),
                cursorKey?.createdAt,
                cursorKey?.id,
                pageRequest
            )
        } else {
            adminPaymentQueryRepository.searchForAdminByCursor(
                keyword,
                request.status,
                toStartOfDay(request.fromDate),
                toNextStartOfDay(request.toDate),
                cursorKey?.createdAt,
                cursorKey?.id,
                pageRequest
            )
        }

        return CursorPageResponse.from(
            payments,
            request.size,
            adminPaymentMapper::toSummary
        ) { payment -> CursorKey(payment.createdAt, payment.paymentId) }
    }

    // 결제 상세에 필요한 대여·회원 스냅샷을 단일 projection 쿼리로 조회합니다.
    @Transactional(readOnly = true)
    fun getPayment(paymentId: Long): AdminPaymentDetailResponse {
        val payment = adminPaymentQueryRepository.findDetailById(paymentId).orElseThrow { CustomException(ErrorCode.PAYMENT_NOT_FOUND) }

        return adminPaymentMapper.toDetail(payment)
    }

    private fun normalize(value: String?): String? = value?.takeIf(StringUtils::hasText)?.trim()
    private fun toStartOfDay(date: LocalDate?): LocalDateTime? = date?.atStartOfDay()
    // 종료일 다음 날 0시 미만으로 비교해 시간 정밀도와 관계없이 종료일 전체를 포함합니다.
    private fun toNextStartOfDay(date: LocalDate?): LocalDateTime? = date?.plusDays(1)?.atStartOfDay()
}
