package iter.dispute.controller.admin

import iter.common.dto.response.CursorPageResponse
import iter.common.security.CustomUserDetails
import iter.dispute.controller.admin.spec.AdminReportApiSpec
import iter.dispute.dto.request.AdminReportSearchRequest
import iter.dispute.dto.request.AdminReportUpdateRequest
import iter.dispute.dto.response.AdminReportDetailResponse
import iter.dispute.dto.response.ReportSummaryResponse
import iter.dispute.service.AdminReportService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
class AdminReportApiController(private val adminReportService: AdminReportService) : AdminReportApiSpec {

    @GetMapping
    override fun getReports(@ModelAttribute request: AdminReportSearchRequest): ResponseEntity<CursorPageResponse<ReportSummaryResponse>> =
        ResponseEntity.ok(adminReportService.getReports(request))

    @GetMapping("/{reportId}")
    override fun getReport(
        @PathVariable
        reportId: Long
    ): ResponseEntity<AdminReportDetailResponse> =
        ResponseEntity.ok(adminReportService.getReport(reportId))

    @PatchMapping("/{reportId}/status")
    override fun updateReportStatus(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        reportId: Long,

        @RequestBody
        request: AdminReportUpdateRequest
    ): ResponseEntity<AdminReportDetailResponse> =
        ResponseEntity.ok(adminReportService.updateReportStatus(principal.user.id, reportId, request))
}
