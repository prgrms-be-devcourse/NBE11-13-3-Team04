package com.example.iter.admin.action.controller

import com.example.iter.admin.action.controller.spec.AdminActionApiSpec
import com.example.iter.admin.action.dto.request.AdminActionSearchRequest
import com.example.iter.admin.action.dto.response.AdminActionResponse
import com.example.iter.admin.action.service.AdminActionQueryService
import com.example.iter.common.dto.response.CursorPageResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/admin/actions")
@PreAuthorize("hasRole('ADMIN')")
class AdminActionApiController(private val adminActionQueryService: AdminActionQueryService) : AdminActionApiSpec {
    @GetMapping
    override fun getAdminActions(@ModelAttribute request: AdminActionSearchRequest): ResponseEntity<CursorPageResponse<AdminActionResponse>> =
        ResponseEntity.ok(adminActionQueryService.getAdminActions(request))
}
