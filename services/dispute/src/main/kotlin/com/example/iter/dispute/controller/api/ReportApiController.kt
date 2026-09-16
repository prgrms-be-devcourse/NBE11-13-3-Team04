package com.example.iter.dispute.controller.api

import com.example.iter.common.dto.response.PageResponse
import com.example.iter.common.security.CustomUserDetails
import com.example.iter.dispute.controller.api.spec.ReportApiSpec
import com.example.iter.dispute.dto.request.ReportCreateRequest
import com.example.iter.dispute.dto.request.ReportSearchRequest
import com.example.iter.dispute.dto.response.ReportDetailResponse
import com.example.iter.dispute.dto.response.ReportSummaryResponse
import com.example.iter.dispute.service.ReportService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/reports")
class ReportApiController(private val reportService: ReportService) : ReportApiSpec {

    @PostMapping
    override fun createReport(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @RequestBody
        request: ReportCreateRequest
    ): ResponseEntity<ReportDetailResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(reportService.createReport(principal.user.id, request))

    @GetMapping("/me")
    override fun getMyReports(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @ModelAttribute
        request: ReportSearchRequest
    ): ResponseEntity<PageResponse<ReportSummaryResponse>> =
        ResponseEntity.ok(reportService.getMyReports(principal.user.id, request))

    @GetMapping("/{reportId}")
    override fun getMyReport(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        reportId: Long
    ): ResponseEntity<ReportDetailResponse> =
        ResponseEntity.ok(reportService.getMyReport(principal.user.id, reportId))
}
