package iter.device.controller.api.spec

import iter.common.dto.response.PageResponse
import iter.common.security.CustomUserDetails
import iter.device.dto.request.MyEquipmentSearchRequest
import iter.device.dto.response.MyEquipmentSummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.ResponseEntity

@Tag(name = "My Equipment", description = "내 장비 관리 API")
@SecurityRequirement(name = "JWT")
interface MyEquipmentApiSpec {

    @Operation(
        summary = "등록 장비 목록 및 상태별 조회",
        description = "인증 회원이 등록한 장비를 상태별로 필터링하고 정렬하여 조회합니다.",
    )
    fun getMyEquipment(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @ParameterObject request: MyEquipmentSearchRequest,
    ): ResponseEntity<PageResponse<MyEquipmentSummaryResponse>>
}
