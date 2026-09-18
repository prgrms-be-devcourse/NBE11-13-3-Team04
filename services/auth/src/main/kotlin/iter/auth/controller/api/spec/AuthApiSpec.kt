package iter.auth.controller.api.spec

import iter.auth.dto.request.LoginRequest
import iter.auth.dto.request.SignUpRequest
import iter.auth.dto.response.AccessTokenResponse
import iter.auth.dto.response.UserResponse
import iter.common.security.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.web.csrf.CsrfToken
import java.util.Locale

@Tag(name = "Auth", description = "회원가입/로그인 API")
interface AuthApiSpec {

    @Operation(summary = "회원가입", description = "요청의 Accept-Language를 회원의 이메일 선호 언어(preferredLanguage) 초기값으로 저장합니다.")
    fun signUp(request: SignUpRequest, @Parameter(hidden = true) locale: Locale): ResponseEntity<UserResponse>

    @Operation(summary = "로그인", description = "Access Token은 응답 본문으로, Refresh Token은 HttpOnly Cookie로 발급합니다.")
    fun login(request: LoginRequest): ResponseEntity<AccessTokenResponse>

    @Operation(summary = "토큰 재발급", description = "Refresh Token Cookie를 회전하고 새로운 Access Token을 응답 본문으로 발급합니다.")
    @Parameters(
        Parameter(
            name = "__Secure-iter-refresh", `in` = ParameterIn.COOKIE, required = true,
            description = "로그인 또는 이전 재발급에서 받은 HttpOnly Refresh Token Cookie",
        ),
        Parameter(
            name = "X-XSRF-TOKEN", `in` = ParameterIn.HEADER, required = true,
            description = "XSRF-TOKEN Cookie와 동일한 CSRF Token",
        ),
    )
    fun refresh(@Parameter(hidden = true) request: HttpServletRequest): ResponseEntity<AccessTokenResponse>

    @Operation(
        summary = "로그아웃", description = "현재 세션의 Refresh Token을 폐기하고 Cookie를 삭제합니다.",
        security = [SecurityRequirement(name = "JWT")],
    )
    @Parameters(
        Parameter(
            name = "__Secure-iter-refresh", `in` = ParameterIn.COOKIE,
            description = "폐기할 HttpOnly Refresh Token Cookie. 누락되어도 로그아웃은 멱등 처리됩니다.",
        ),
        Parameter(
            name = "X-XSRF-TOKEN", `in` = ParameterIn.HEADER, required = true,
            description = "XSRF-TOKEN Cookie와 동일한 CSRF Token",
        ),
    )
    fun logout(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(hidden = true) request: HttpServletRequest,
    ): ResponseEntity<Void>

    @Operation(summary = "CSRF Token 발급", description = "Refresh Token Cookie를 사용하는 인증 요청용 CSRF Token을 발급합니다.")
    @ApiResponse(responseCode = "204", description = "CSRF Token Cookie 발급 완료")
    fun csrf(@Parameter(hidden = true) csrfToken: CsrfToken): ResponseEntity<Void>
}
