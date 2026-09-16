package com.example.iter.admin.stats.controller

import com.example.iter.admin.stats.controller.spec.AdminStatsApiSpec
import com.example.iter.admin.stats.dto.response.AdminStatsResponse
import com.example.iter.admin.stats.service.AdminStatsService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/stats")
@PreAuthorize("hasRole('ADMIN')")
class AdminStatsApiController(private val adminStatsService: AdminStatsService) : AdminStatsApiSpec {
    @GetMapping
    override fun getStats(): ResponseEntity<AdminStatsResponse> =
        ResponseEntity.ok(adminStatsService.getStats())
}
