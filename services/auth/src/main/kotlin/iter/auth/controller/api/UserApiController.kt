package iter.auth.controller.api

import iter.auth.controller.api.spec.UserApiSpec
import iter.auth.dto.request.AddressUpdateRequest
import iter.auth.dto.request.PasswordChangeRequest
import iter.auth.dto.request.UserDeleteRequest
import iter.auth.dto.request.UserUpdateRequest
import iter.auth.dto.response.AddressResponse
import iter.auth.dto.response.UserResponse
import iter.auth.service.UserAccountService
import iter.auth.service.UserAddressService
import iter.auth.support.RefreshTokenCookieManager
import iter.common.security.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserApiController(
    private val userAddressService: UserAddressService,
    private val userAccountService: UserAccountService,
    private val refreshTokenCookieManager: RefreshTokenCookieManager,
) : UserApiSpec {

    @GetMapping("/me")
    override fun getMe(@AuthenticationPrincipal principal: CustomUserDetails): ResponseEntity<UserResponse> =
        ResponseEntity.ok(userAccountService.getMyProfile(principal.user.id))

    @PatchMapping("/me")
    override fun updateMe(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @Valid @RequestBody request: UserUpdateRequest,
    ): ResponseEntity<UserResponse> =
        ResponseEntity.ok(userAccountService.updateMyProfile(principal.user.id, request))

    @PatchMapping("/me/password")
    override fun changePassword(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @Valid @RequestBody request: PasswordChangeRequest,
    ): ResponseEntity<Void> {
        userAccountService.changePassword(principal.user.id, request)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me/address")
    override fun getDefaultAddress(
        @AuthenticationPrincipal principal: CustomUserDetails,
    ): ResponseEntity<AddressResponse> =
        ResponseEntity.ok(userAddressService.getDefaultAddress(principal.user.id))

    @PutMapping("/me/address")
    override fun updateDefaultAddress(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @Valid @RequestBody request: AddressUpdateRequest,
    ): ResponseEntity<AddressResponse> =
        ResponseEntity.ok(userAddressService.updateDefaultAddress(principal.user.id, request))

    @DeleteMapping("/me")
    override fun withdraw(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @Valid @RequestBody(required = false) request: UserDeleteRequest?,
    ): ResponseEntity<Void> {
        userAccountService.withdraw(principal.user.id, request)
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.delete().toString())
            .build()
    }
}
