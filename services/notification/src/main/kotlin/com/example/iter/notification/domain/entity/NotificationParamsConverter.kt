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

    override fun convertToEntityAttribute(dbData: String?): Map<String, Any> {
        if (dbData == null) {
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
