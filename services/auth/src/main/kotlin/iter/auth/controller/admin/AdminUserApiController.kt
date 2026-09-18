package iter.auth.controller.admin

import iter.auth.controller.admin.spec.AdminUserApiSpec
import iter.auth.dto.request.AdminUserSearchRequest
import iter.auth.dto.request.AdminUserStatusRequest
import iter.auth.dto.response.AdminUserDetailResponse
import iter.auth.dto.response.AdminUserStatusResponse
import iter.auth.dto.response.AdminUserSummaryResponse
import iter.auth.service.AdminUserService
import iter.common.dto.response.CursorPageResponse
import iter.common.security.CustomUserDetails
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
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserApiController(private val adminUserService: AdminUserService) : AdminUserApiSpec {

    // 관리자 회원 목록을 조회합니다.
    @GetMapping
    override fun getUsers(@ModelAttribute request: AdminUserSearchRequest): ResponseEntity<CursorPageResponse<AdminUserSummaryResponse>> =
        ResponseEntity.ok(adminUserService.getUsers(request))

    // 특정 회원의 정보와 거래 요약을 조회합니다.
    @GetMapping("/{userId}")
    override fun getUser(@PathVariable userId: Long): ResponseEntity<AdminUserDetailResponse> =
        ResponseEntity.ok(adminUserService.getUser(userId))

    // 회원 상태를 정지 또는 정지 해제로 변경합니다.
    @PatchMapping("/{userId}/status")
    override fun updateStatus(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        userId: Long,

        @RequestBody
        request: AdminUserStatusRequest
    ): ResponseEntity<AdminUserStatusResponse> =
        ResponseEntity.ok(adminUserService.updateStatus(principal.user.id, userId, request))
}
