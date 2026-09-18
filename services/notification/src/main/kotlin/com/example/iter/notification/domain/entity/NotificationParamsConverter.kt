package com.example.iter.notification.domain.entity

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

// Notification.params(알림 문구 조립에 필요한 보간 값들, 예: productName/renterName)를
// DB엔 JSON 문자열 한 컬럼으로 저장한다. 프론트가 type + params로 자체 i18n 문구를 조립하므로,
// 백엔드는 완성된 문장이 아니라 이 구조화된 값만 들고 있으면 된다.
@Converter
class NotificationParamsConverter : AttributeConverter<Map<String, Any>, String> {

    override fun convertToDatabaseColumn(attribute: Map<String, Any>?): String? {
        if (attribute == null) {
            return null
        }
        return try {
            MAPPER.writeValueAsString(attribute)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("알림 params 직렬화 실패: $attribute", e)
        }
    }

    // 빈 문자열도 null 과 같이 취급한다. params 도입 이전에 저장된 행들이 ''로 채워져 있었는데,
    // ''는 JSON 이 아니라서 아래 readValue 가 던지고 그 행이 한 건만 섞여 있어도 알림 목록 조회
    // 전체가 500 이 됐다(V7 마이그레이션에서 데이터는 '{}'로 메웠지만, 읽는 쪽도 한 행 때문에
    // 목록 전체가 죽지 않도록 막아둔다).
    override fun convertToEntityAttribute(dbData: String?): Map<String, Any> {
        if (dbData.isNullOrBlank()) {
            return emptyMap()
        }
        return try {
            MAPPER.readValue(dbData, MAP_TYPE)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("알림 params 역직렬화 실패: $dbData", e)
        }
    }

    companion object {
        private val MAPPER = ObjectMapper()
        private val MAP_TYPE = object : TypeReference<Map<String, Any>>() {}
    }
}
