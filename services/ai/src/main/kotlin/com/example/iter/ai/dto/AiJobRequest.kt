package com.example.iter.ai.dto

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HexFormat
import java.util.UUID
import kotlin.jvm.JvmRecord

@JvmRecord
data class AiJobRequest(
    val schemaVersion: String?,
    val jobId: UUID?,
    val featureType: FeatureType?,
    val source: Source?,
    val inputHash: String?,
    val payload: Map<String, Any?>?
) {
    enum class FeatureType {
        RETURN_CONDITION_V1,
        RETURN_CONDITION_V2,
        REPORT_TRIAGE_V1,
        EQUIPMENT_DRAFT_V1
    }

    @JvmRecord
    data class Source(val type: String?, val id: String?)

    companion object {
        private val mapper: JsonMapper = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build()

        // payload snapshot과 hash를 함께 만들어 같은 작업의 중복 실행을 막습니다.
        @JvmStatic
        fun create(featureType: FeatureType, sourceId: String?, payload: Map<String, Any?>?): AiJobRequest {
            val sourceType = when (featureType) {
                FeatureType.RETURN_CONDITION_V1,
                FeatureType.RETURN_CONDITION_V2,
                -> "RENTAL"

                FeatureType.REPORT_TRIAGE_V1 -> "REPORT"
                FeatureType.EQUIPMENT_DRAFT_V1 -> "UPLOAD_BUNDLE"
            }

            // 정렬된 JSON byte를 hash와 snapshot 양쪽에 사용해 같은 입력의 기준을 통일합니다.
            val json = mapper.writeValueAsBytes(payload)
            // 호출자가 원본 Map을 수정해도 이미 만든 요청 내용은 변하지 않게 복사합니다.
            val snapshot: Map<String, Any?>? = mapper.readValue(
                json,
                object : TypeReference<Map<String, Any?>>() {}
            )

            try {
                val sha256 = MessageDigest.getInstance("SHA-256")
                val hashBytes = sha256.digest(json)
                val hash = HexFormat.of().formatHex(hashBytes)

                return AiJobRequest(
                    schemaVersion = "1.0",
                    jobId = UUID.randomUUID(),
                    featureType = featureType,
                    source = Source(sourceType, sourceId),
                    inputHash = "sha256:$hash",
                    payload = snapshot
                )
            } catch (exception: NoSuchAlgorithmException) {
                throw IllegalStateException("SHA-256을 사용할 수 없습니다.", exception)
            }
        }
    }
}
