package iter.ai.controller

import iter.ai.dto.EquipmentDraftRequest
import iter.ai.dto.EquipmentDraftResponse
import iter.ai.service.EquipmentDraftService
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
import java.util.UUID

@RestController
@RequestMapping("/api/v1/ai/equipment-drafts")
class EquipmentDraftController(private val service: EquipmentDraftService) {
    // 본인이 업로드한 임시 사진으로 장비 등록 AI 초안 작업을 접수한다.
    @Operation(summary = "본인 임시 사진으로 장비 등록 AI 초안 생성")
    @PostMapping
    fun create(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @Valid
        @RequestBody
        request: EquipmentDraftRequest
    ): ResponseEntity<EquipmentDraftResponse> = ResponseEntity.accepted().body(service.create(principal.user.id, request))

    // 요청 소유자를 확인한 뒤 장비 초안 상태와 결과를 반환한다.
    @Operation(summary = "본인 AI 초안 상태 및 결과 조회")
    @GetMapping("/{jobId}")
    fun get(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        jobId: UUID
    ): EquipmentDraftResponse = service.get(principal.user.id, jobId)

    // 접수 불명확 작업을 새로 생성하지 않고 기존 ID로 다시 접수한다.
    @Operation(summary = "접수 확인이 안 된 본인 작업을 같은 ID로 재접수")
    @PostMapping("/{jobId}/retry")
    fun retry(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        jobId: UUID
    ): ResponseEntity<EquipmentDraftResponse> = ResponseEntity.accepted().body(service.retry(principal.user.id, jobId))
}
