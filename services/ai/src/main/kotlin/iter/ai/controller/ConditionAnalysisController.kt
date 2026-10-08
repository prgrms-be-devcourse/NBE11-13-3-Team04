package iter.ai.controller

import iter.ai.dto.ConditionAnalysisRequest
import iter.ai.dto.ConditionAnalysisResponse
import iter.ai.service.ConditionAnalysisService
import iter.common.security.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rentals/{rentalId}/condition-analysis")
class ConditionAnalysisController(private val service: ConditionAnalysisService) {
    // 장비 소유자가 촬영 방향별 수령·반납 사진의 AI 비교 작업을 접수한다.
    @Operation(summary = "장비 소유자가 수령·반납 사진의 방향별 AI 비교 요청")
    @PostMapping
    fun create(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        rentalId: Long,

        @Valid
        @RequestBody
        request: ConditionAnalysisRequest
    ): ResponseEntity<ConditionAnalysisResponse> = ResponseEntity.accepted().body(service.create(principal.user.id, rentalId, request))

    // 화면 polling을 위해 해당 대여의 AI 비교 상태와 결과를 조회한다.
    @GetMapping
    fun get(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        rentalId: Long
    ): ConditionAnalysisResponse = service.get(principal.user.id, rentalId)

    // 저장된 최초 요청과 같은 작업 ID로 접수 여부만 다시 확인한다.
    @PostMapping("/retry")
    fun retry(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        rentalId: Long
    ): ResponseEntity<ConditionAnalysisResponse> = ResponseEntity.accepted().body(service.retry(principal.user.id, rentalId))
}
