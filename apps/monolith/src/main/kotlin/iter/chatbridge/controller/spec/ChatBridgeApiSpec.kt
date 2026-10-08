package iter.chatbridge.controller.spec

import iter.chatbridge.dto.request.ChatInquiryGrantRequest
import iter.chatbridge.dto.response.ChatInquiryGrantResponse
import iter.chatbridge.dto.response.ChatTicketResponse
import iter.common.security.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal

@Tag(name = "Chat Bridge", description = "채팅(apps:chat) 접근을 위한 티켓/문의 승인 발급 API — 실제 채팅은 별도 서버가 처리한다")
@SecurityRequirement(name = "JWT")
interface ChatBridgeApiSpec {

    @Operation(
        summary = "채팅 접근 티켓 발급",
        description = "apps:chat에 대한 REST/WebSocket 인증에 쓸 단기 티켓을 발급합니다. " +
            "chat은 JWT 비밀키를 갖지 않고 이 티켓만 Redis로 검증합니다(TTL 10분).",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "발급 성공"),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ],
    )
    fun issueTicket(
        @AuthenticationPrincipal principal: CustomUserDetails,
    ): ResponseEntity<ChatTicketResponse>

    @Operation(
        summary = "문의 채팅방 생성 승인 발급",
        description = "장비 문의를 위한 채팅방을 만들 권한을 발급합니다. 장비 존재/활성 여부와 " +
            "본인 소유 여부를 여기서 검증하고, 승인 토큰(TTL 60초)을 chat에 전달하면 방이 만들어집니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "발급 성공"),
            ApiResponse(responseCode = "400", description = "본인 소유 장비 문의 시도", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
            ApiResponse(responseCode = "404", description = "존재하지 않는 장비", content = [Content()]),
            ApiResponse(responseCode = "409", description = "문의할 수 없는 상태의 장비", content = [Content()]),
        ],
    )
    fun issueInquiryGrant(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @Valid request: ChatInquiryGrantRequest,
    ): ResponseEntity<ChatInquiryGrantResponse>
}
