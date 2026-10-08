package iter.ai.service

import iter.ai.config.AiServiceProperties
import iter.ai.domain.entity.EquipmentDraftJob
import iter.ai.domain.repository.EquipmentDraftJobRepository
import iter.ai.dto.AiJobRequest
import iter.ai.port.EquipmentDraftContextPort
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// AI 작업 DB 트랜잭션만 담당하고 사용자·장비 검증은 조합 계층 Port에 위임합니다.
@Service
class EquipmentDraftStore(
    private val jobs: EquipmentDraftJobRepository,
    private val context: EquipmentDraftContextPort,
    private val properties: AiServiceProperties,
    private val mapper: JsonMapper,
    private val clock: Clock
) {
    // 회원 행 잠금 아래에서 업로드 상태와 일일 한도를 다시 확인한 뒤 원본 요청을 저장합니다.
    @Transactional
    fun reserve(ownerId: Long, keys: List<String>, request: AiJobRequest) {
        context.lockOwnerAndValidateUploads(ownerId, keys)

        val dayStart = LocalDate.now(clock).atStartOfDay()

        if (jobs.countByOwnerIdAndCreatedAtGreaterThanEqual(ownerId, dayStart) >= properties.dailyDraftLimit) {
            throw CustomException(ErrorCode.AI_DAILY_LIMIT)
        }

        jobs.save(
            EquipmentDraftJob(
                jobId = requireNotNull(request.jobId).toString(),
                ownerId = ownerId,
                requestJson = mapper.writeValueAsString(request),
                createdAt = LocalDateTime.now(clock)
            )
        )
    }

    // 작업 소유권을 함께 조건으로 조회해 다른 회원의 AI 요청 원문이 노출되지 않게 합니다.
    @Transactional(readOnly = true)
    fun ownedRequest(ownerId: Long, jobId: UUID): AiJobRequest {
        context.requireActiveOwner(ownerId)

        val job = jobs.findByJobIdAndOwnerId(jobId.toString(), ownerId).orElseThrow { CustomException(ErrorCode.ENTITY_NOT_FOUND) }

        return mapper.readValue(job.requestJson, AiJobRequest::class.java)
    }
}
