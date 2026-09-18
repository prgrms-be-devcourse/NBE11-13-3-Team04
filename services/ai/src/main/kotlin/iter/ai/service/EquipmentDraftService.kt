package iter.ai.service

import iter.ai.client.AiServiceClient
import iter.ai.client.AiServiceException
import iter.ai.dto.AiJobRequest
import iter.ai.dto.AiJobStatus
import iter.ai.dto.EquipmentDraftRequest
import iter.ai.dto.EquipmentDraftResponse
import iter.ai.port.EquipmentDraftContextPort
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import org.springframework.stereotype.Service
import java.util.LinkedHashMap
import java.util.UUID

@Service
class EquipmentDraftService(
    private val store: EquipmentDraftStore,
    private val context: EquipmentDraftContextPort,
    private val client: AiServiceClient
) {
    // 본인 임시 업로드를 S3에서 검증한 뒤 고정된 이미지 버전으로 AI 요청을 만듭니다.
    fun create(ownerId: Long, request: EquipmentDraftRequest): EquipmentDraftResponse {
        val imageKeys = requireNotNull(request.imageKeys)
        val references = context.loadValidatedImages(ownerId, imageKeys).map { image -> image.toPayload() }

        val hints = LinkedHashMap<String, Any?>()

        hints["name"] = request.name
        hints["category"] = request.category

        val payload = mapOf<String, Any?>(
            "images" to references,
            "hints" to hints
        )

        val job = AiJobRequest.create(
            AiJobRequest.FeatureType.EQUIPMENT_DRAFT_V1,
            UUID.randomUUID().toString(),
            payload
        )

        store.reserve(ownerId, imageKeys, job)

        return submit(job)
    }

    // 먼저 기존 상태를 조회하고 미접수된 경우에만 같은 UUID와 입력을 다시 보냅니다.
    fun retry(ownerId: Long, jobId: UUID): EquipmentDraftResponse {
        val request = store.ownedRequest(ownerId, jobId)

        try {
            return result(client.getJob(jobId))
        } catch (exception: AiServiceException) {
            if (exception.statusCode != 404) {
                throw CustomException(ErrorCode.AI_UNAVAILABLE)
            }
        }

        // 404일 때만 저장된 원본 요청을 재전송합니다. 다른 오류에서 자동 재접수하면
        // 실제로 처리 중인 작업을 사용자가 중복 실행할 수 있기 때문입니다.
        val references = requireNotNull(request.payload)["images"] as List<*>
        val imageKeys = references.map { reference ->
            (reference as Map<*, *>)["objectKey"] as String
        }

        // 재접수 시점에도 임시 사진이 만료·사용 처리되지 않았는지 다시 확인합니다.
        context.loadValidatedImages(ownerId, imageKeys)

        return submit(request)
    }

    // 요청 소유자를 확인한 뒤 장비 초안 작업의 현재 상태를 반환합니다.
    fun get(ownerId: Long, jobId: UUID): EquipmentDraftResponse {
        store.ownedRequest(ownerId, jobId)

        try {
            return result(client.getJob(jobId))
        } catch (exception: AiServiceException) {
            if (exception.statusCode == 404) {
                return unknown(jobId)
            }

            throw CustomException(ErrorCode.AI_UNAVAILABLE)
        }
    }

    // 접수 응답을 잃어도 중복 생성하지 않도록 작업 ID를 보존한 상태를 반환합니다.
    private fun submit(request: AiJobRequest): EquipmentDraftResponse = try {
        val accepted = client.createJob(request)

        EquipmentDraftResponse(
            accepted.jobId,
            requireNotNull(accepted.status).name,
            null,
            null
        )
    } catch (exception: AiServiceException) {
        unknown(requireNotNull(request.jobId))
    }

    // 접수 결과를 잃어도 같은 작업을 다시 확인할 수 있도록 UUID를 반환합니다.
    private fun unknown(jobId: UUID): EquipmentDraftResponse = EquipmentDraftResponse(
        jobId,
        "SUBMISSION_UNKNOWN",
        null,
        "AI 접수 확인이 필요합니다. 같은 작업 ID로 조회하거나 재접수해주세요. 직접 등록도 가능합니다."
    )

    // 다른 기능의 UUID가 섞이지 않았는지 확인한 뒤 장비 초안 응답으로 변환합니다.
    private fun result(job: AiJobStatus): EquipmentDraftResponse {
        if (job.featureType != AiJobRequest.FeatureType.EQUIPMENT_DRAFT_V1) {
            throw CustomException(ErrorCode.AI_UNAVAILABLE)
        }

        return EquipmentDraftResponse(
            job.jobId,
            requireNotNull(job.status).name,
            job.result,
            job.errorMessage
        )
    }
}
