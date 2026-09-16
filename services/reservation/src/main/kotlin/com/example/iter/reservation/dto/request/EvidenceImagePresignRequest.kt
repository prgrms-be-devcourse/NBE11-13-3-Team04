package com.example.iter.reservation.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// 수령/반납 증빙 사진 업로드용 presigned URL 요청 — 장비 이미지(device 도메인)와 달리
// temp->public 승격 단계가 없어 훨씬 단순하다: 발급받은 objectKey가 곧 최종 위치다.
//
// 원래 자바 record였고 아직 자바인 서비스(RentalEvidenceUploadService 등)가 record 접근자
// (request.files(), item.contentType())를 그대로 부른다. 처음엔 @get:JvmName으로 프로퍼티
// 접근자 이름 자체를 record와 맞췄으나, 그러면 Jackson이 이 getter를 표준 빈 접근자로 인식하지
// 못해 응답 DTO 직렬화가 조용히 깨졌다(ReturnApiControllerTest 등에서 실측). 그래서 프로퍼티는
// 코틀린 관용 그대로 두고(getFiles() 등, Jackson용), 자바 호출부를 위한 record 이름 그대로의
// 별도 함수를 덧붙이는 방식으로 바꿨다 — R2 DTO 전체에 공통 적용되는 규칙이다.
data class EvidenceImagePresignRequest(
    @field:NotEmpty(message = "이미지는 최소 1장 필요합니다.")
    @field:Size(max = 4, message = "이미지는 최대 4장까지 업로드할 수 있습니다.")
    val files: List<@Valid Item>?,
) {
    fun files(): List<Item>? = files

    data class Item(
        @field:NotBlank(message = "Content-Type은 필수입니다.")
        @field:Pattern(
            regexp = "image/jpeg|image/png|image/webp",
            message = "JPEG, PNG, WebP 형식만 지원합니다.",
        )
        val contentType: String?,
    ) {
        fun contentType(): String? = contentType
    }
}
