package com.example.iter.reservation.dto.request

import com.example.iter.reservation.api.RentalStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

// 원본 자바 record는 compact constructor로 page/size의 null을 기본값으로 치환했다.
// @ModelAttribute 바인딩(RentalHistoryApiController)에서 쿼리 파라미터가 없으면 Spring의
// 코틀린 인식 BeanUtils(KotlinDelegate.instantiateClass)가 그 값을 비워 전달하고
// KCallable.callBy가 기본값을 채운다 — device EquipmentSearchRequest의 page/size 기본값과
// 동일한 근거로 검증된 패턴이다.
data class RentalHistorySearchRequest(
    val status: RentalStatus?,

    @field:Size(max = 100, message = "장비명은 100자 이하여야 합니다.")
    val equipmentName: String?,

    @field:Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
    val page: Int = 0,

    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    val size: Int = 20,
) {
    // RentalHistoryService(자바)가 record 접근자 스타일로 그대로 부른다.
    fun status(): RentalStatus? = status
    fun equipmentName(): String? = equipmentName
    fun page(): Int = page
    fun size(): Int = size
}
