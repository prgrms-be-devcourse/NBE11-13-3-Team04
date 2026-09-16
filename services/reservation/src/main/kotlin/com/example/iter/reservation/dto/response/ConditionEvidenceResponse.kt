package com.example.iter.reservation.dto.response

import com.example.iter.reservation.domain.entity.ProductConditionType

import java.time.LocalDateTime
import java.util.Objects

// 원본 자바 record의 compact constructor가 null->빈 목록 치환 + 방어적 복사를 했다
// (ConditionEvidenceResponseTest 3개가 이 계약을 검증한다). 코틀린 data class의 주 생성자
// val 파라미터는 재할당이 안 되므로 이 변환을 data class로는 표현할 수 없어, 평범한 class로
// 두고 equals/hashCode/toString을 record와 동일하게 직접 구현한다.
//
// 프로퍼티 자체는 코틀린 관용 그대로(getProductCondition() 등) 둬서 Jackson 직렬화가
// 정상 동작하게 하고, ReturnMapper.java·여러 테스트가 쓰는 record 접근자 이름
// (productCondition() 등)은 별도 브리지 함수로 제공한다 — @get:JvmName으로 접근자 이름
// 자체를 바꾸면 Jackson이 getter를 인식하지 못해 응답 직렬화가 깨진다(실측됨).
class ConditionEvidenceResponse(
    val productCondition: ProductConditionType?,
    val conditionDetail: String?,
    imageUrls: List<String>?,
    val recordedAt: LocalDateTime?,
) {
    val imageUrls: List<String> = if (imageUrls == null) emptyList() else java.util.List.copyOf(imageUrls)

    fun productCondition(): ProductConditionType? = productCondition
    fun conditionDetail(): String? = conditionDetail
    fun imageUrls(): List<String> = imageUrls
    fun recordedAt(): LocalDateTime? = recordedAt

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConditionEvidenceResponse) return false
        return productCondition == other.productCondition &&
            conditionDetail == other.conditionDetail &&
            imageUrls == other.imageUrls &&
            recordedAt == other.recordedAt
    }

    override fun hashCode(): Int = Objects.hash(productCondition, conditionDetail, imageUrls, recordedAt)

    override fun toString(): String =
        "ConditionEvidenceResponse[productCondition=$productCondition, conditionDetail=$conditionDetail, " +
            "imageUrls=$imageUrls, recordedAt=$recordedAt]"
}
