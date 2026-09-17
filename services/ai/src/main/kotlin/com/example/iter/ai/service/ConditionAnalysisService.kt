package com.example.iter.ai.service

import com.example.iter.ai.client.AiServiceClient
import com.example.iter.ai.client.AiServiceException
import com.example.iter.ai.domain.entity.ConditionAnalysisJob
import com.example.iter.ai.dto.AiJobRequest
import com.example.iter.ai.dto.ConditionAnalysisRequest
import com.example.iter.ai.dto.ConditionAnalysisResponse
import com.example.iter.ai.port.ConditionAnalysisContextPort
import com.example.iter.ai.port.ConditionImage
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.image.CaptureView
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.util.EnumMap
import java.util.EnumSet
import java.util.LinkedHashMap
import java.util.UUID

@Service
class ConditionAnalysisService(
    private val store: ConditionAnalysisStore,
    private val images: ConditionEvidenceImages,
    private val context: ConditionAnalysisContextPort,
    private val client: AiServiceClient,
    private val mapper: JsonMapper
) {
    // 대여 상태를 확인하고 수령·반납의 정면·측면·후면 사진을 같은 방향끼리 묶습니다.
    fun create(ownerId: Long, rentalId: Long, input: ConditionAnalysisRequest): ConditionAnalysisResponse {
        val existing = store.find(ownerId, rentalId)

        // 대여 건당 최초 선택 사진을 고정하므로 이후 요청 값으로 덮어쓰지 않습니다.
        if (existing.isPresent) {
            return submit(existing.get())
        }

        store.validateReady(ownerId, rentalId)

        val comparison = context.loadComparison(ownerId, rentalId)
        val before = requiredViews(comparison.beforeImages)
        val after = requiredViews(comparison.afterImages)

        val beforeImages = references("before", before)
        val afterImages = references("after", after)
        val listingImages = optionalReferences("listing", comparison.listingImages)

        val payload = linkedMapOf<String, Any?>(
            "listingImages" to listingImages,
            "beforeImages" to beforeImages,
            "afterImages" to afterImages
        )

        val request = AiJobRequest.create(
            AiJobRequest.FeatureType.RETURN_CONDITION_V2,
            rentalId.toString(),
            payload
        )

        val savedJob = store.reserve(ownerId, rentalId, request)

        return submit(savedJob)
    }

    // 저장된 비교 요청을 새 작업으로 만들지 않고 동일한 UUID로 다시 접수합니다.
    fun retry(ownerId: Long, rentalId: Long): ConditionAnalysisResponse {
        val savedJob = store.find(ownerId, rentalId).orElseThrow { CustomException(ErrorCode.ENTITY_NOT_FOUND) }

        return submit(savedJob)
    }

    // 소유권을 확인하고 Python AI 서비스의 현재 상태를 화면 응답으로 변환합니다.
    fun get(ownerId: Long, rentalId: Long): ConditionAnalysisResponse {
        val saved = store.find(ownerId, rentalId)

        if (saved.isEmpty) {
            return ConditionAnalysisResponse(
                null,
                "NOT_REQUESTED",
                null,
                null,
                null,
                null
            )
        }

        val job = saved.get()
        return try {
            val result = client.getJob(UUID.fromString(job.jobId))

            // UUID가 우연히 다른 AI 기능과 섞여도 잘못된 결과를 화면에 표시하지 않습니다.
            val conditionFeature = result.featureType == AiJobRequest.FeatureType.RETURN_CONDITION_V1 ||
                result.featureType == AiJobRequest.FeatureType.RETURN_CONDITION_V2

            if (!conditionFeature) {
                throw CustomException(ErrorCode.AI_CONDITION_UNAVAILABLE)
            }

            response(job, requireNotNull(result.status).name, result.result, result.errorMessage)
        } catch (exception: AiServiceException) {
            if (exception.statusCode == 404) {
                unknown(job)
            } else {
                throw CustomException(ErrorCode.AI_CONDITION_UNAVAILABLE)
            }
        }
    }

    // DB transaction 밖에서 AI 작업을 접수하며 응답이 불명확하면 UUID를 보존합니다.
    // timeout은 서버가 요청을 받지 못했다는 뜻이 아니므로 실패로 단정하지 않습니다.
    private fun submit(job: ConditionAnalysisJob): ConditionAnalysisResponse = try {
        val request = mapper.readValue(job.requestJson, AiJobRequest::class.java)
        val accepted = client.createJob(request)

        response(job, requireNotNull(accepted.status).name, null, null)
    } catch (exception: AiServiceException) {
        unknown(job)
    }

    // 접수 성공 여부를 단정할 수 없을 때 재조회 가능한 작업 ID와 안내 문구를 유지합니다.
    private fun unknown(job: ConditionAnalysisJob): ConditionAnalysisResponse = response(
        job,
        "SUBMISSION_UNKNOWN",
        null,
        "AI 접수 확인이 필요합니다. 같은 작업으로 재접수하거나 사진을 직접 비교하십시오."
    )

    // 저장된 요청에서 실제 비교 사진 번호를 복원해 결과와 함께 화면에 전달합니다.
    private fun response(job: ConditionAnalysisJob, status: String, result: Map<String, Any?>?, message: String?): ConditionAnalysisResponse {
        val request = mapper.readValue(job.requestJson, AiJobRequest::class.java)
        val payload = requireNotNull(request.payload)
        val before = payload["beforeImages"] as List<*>
        val after = payload["afterImages"] as List<*>

        val comparedImageIds = LinkedHashMap<String, String>()

        if (request.featureType == AiJobRequest.FeatureType.RETURN_CONDITION_V1) {
            comparedImageIds["before"] = (before.first() as Map<*, *>)["imageId"] as String
            comparedImageIds["after"] = (after.first() as Map<*, *>)["imageId"] as String
        } else {
            addComparedImageIds(comparedImageIds, "before", before)
            addComparedImageIds(comparedImageIds, "after", after)
        }

        return ConditionAnalysisResponse(
            UUID.fromString(job.jobId),
            status,
            result,
            message,
            job.createdAt,
            comparedImageIds
        )
    }

    private fun requiredViews(source: List<ConditionImage>): Map<CaptureView, ConditionImage> {
        val result = toViewMap(source)

        if (result.keys != EnumSet.allOf(CaptureView::class.java)) {
            throw CustomException(ErrorCode.INVALID_IMAGE)
        }

        return result
    }

    private fun optionalReferences(phase: String, source: List<ConditionImage>): List<Map<String, Any>> {
        val byView = toViewMap(source)

        if (byView.keys != EnumSet.allOf(CaptureView::class.java)) {
            return emptyList()
        }

        return references(phase, byView)
    }

    private fun toViewMap(source: List<ConditionImage>): Map<CaptureView, ConditionImage> {
        val result = EnumMap<CaptureView, ConditionImage>(CaptureView::class.java)

        for (image in source) {
            val captureView = image.captureView?.let(CaptureView::valueOf) ?: continue

            if (result.put(captureView, image) != null) {
                throw CustomException(ErrorCode.INVALID_IMAGE)
            }
        }
        return result
    }

    private fun references(phase: String, source: Map<CaptureView, ConditionImage>): List<Map<String, Any>> = CaptureView.entries.map { view ->
        val image = requireNotNull(source[view])
        val imageId = "$phase-${view.name.lowercase()}"

        images.reference(image.imageUrl, imageId, view.name)
    }.toList()

    private fun addComparedImageIds(target: MutableMap<String, String>, phase: String, source: List<*>) {
        for (item in source) {
            val image = item as Map<*, *>
            val slot = (image["captureSlot"] as String).lowercase()

            target["$phase-$slot"] = image["imageId"] as String
        }
    }
}
