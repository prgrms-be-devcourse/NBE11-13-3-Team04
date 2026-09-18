package iter.ai.controller

import iter.ai.dto.ReportAnalysisRequest
import iter.ai.dto.ReportAnalysisResponse
import iter.ai.service.ReportAnalysisService
import iter.common.security.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/reports/{reportId}/ai-analysis")
@PreAuthorize("hasRole('ADMIN') and principal.user.isActive()")
class ReportAnalysisController(private val service: ReportAnalysisService) {
    // 관리자가 개인정보를 정리한 신고 텍스트로 AI 검토 보조 작업을 접수한다.
    @Operation(summary = "관리자가 검토한 신고 텍스트 분석 접수 (동일 신고는 같은 작업 재사용)")
    @PostMapping
    fun create(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        reportId: Long,

        @Valid
        @RequestBody
        request: ReportAnalysisRequest
    ): ResponseEntity<ReportAnalysisResponse> = ResponseEntity.accepted().body(service.create(principal.user.id, reportId, request))

    // 관리자 화면에 표시할 신고 AI 상태와 참고 결과를 조회한다.
    @Operation(summary = "관리자용 신고 AI 상태와 참고 결과 조회")
    @GetMapping
    fun get(@PathVariable reportId: Long): ReportAnalysisResponse = service.get(reportId)

    // 최초 입력과 작업 ID를 그대로 재사용해 접수 여부만 다시 확인한다.
    @Operation(summary = "기존 신고 AI 작업을 최초 입력과 같은 ID로 재접수 (재분석 아님)")
    @PostMapping("/retry")
    fun retry(@PathVariable reportId: Long): ResponseEntity<ReportAnalysisResponse> = ResponseEntity.accepted().body(service.retry(reportId))
}
