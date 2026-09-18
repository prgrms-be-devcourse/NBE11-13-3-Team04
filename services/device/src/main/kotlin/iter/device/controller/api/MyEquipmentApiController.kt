package iter.device.controller.api

import iter.common.dto.response.PageResponse
import iter.common.security.CustomUserDetails
import iter.device.controller.api.spec.MyEquipmentApiSpec
import iter.device.dto.request.MyEquipmentSearchRequest
import iter.device.dto.response.MyEquipmentSummaryResponse
import iter.device.service.EquipmentQueryService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me/devices")
class MyEquipmentApiController(
    private val equipmentQueryService: EquipmentQueryService,
) : MyEquipmentApiSpec {

    @GetMapping
    override fun getMyEquipment(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @Valid @ModelAttribute request: MyEquipmentSearchRequest,
    ): ResponseEntity<PageResponse<MyEquipmentSummaryResponse>> =
        ResponseEntity.ok(equipmentQueryService.getMyEquipment(principal.user.id, request))
}
