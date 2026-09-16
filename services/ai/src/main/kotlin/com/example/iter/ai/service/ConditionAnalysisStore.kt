package com.example.iter.ai.service

import com.example.iter.ai.config.AiServiceProperties
import com.example.iter.ai.domain.entity.ConditionAnalysisJob
import com.example.iter.ai.domain.repository.ConditionAnalysisJobRepository
import com.example.iter.ai.dto.AiJobRequest
import com.example.iter.ai.port.ConditionAnalysisContextPort
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.LocalDateTime
import java.util.Optional

@Service
class ConditionAnalysisStore(
    private val jobs: ConditionAnalysisJobRepository,
    private val context: ConditionAnalysisContextPort,
    private val properties: AiServiceProperties,
    private val mapper: JsonMapper,
    private val clock: Clock
) {
    // 거래 소유권을 먼저 확인한 뒤 거래 ID에 대응하는 단일 비교 작업을 조회합니다.
    @Transactional(readOnly = true)
    fun find(ownerId: Long, rentalId: Long): Optional<ConditionAnalysisJob> {
        context.requireOwner(ownerId, rentalId)
        return jobs.findById(rentalId)
    }

    // 이미지와 AI 서버를 호출하기 전에 권한·상태 오류를 빠르게 반환합니다.
    @Transactional(readOnly = true)
    fun validateReady(ownerId: Long, rentalId: Long) {
        context.requireReady(ownerId, rentalId)
    }

    // 회원·대여 행을 잠근 상태에서 기존 작업, 일일 한도, 신규 저장을 하나의 원자적 절차로 처리합니다.
    @Transactional
    fun reserve(ownerId: Long, rentalId: Long, request: AiJobRequest): ConditionAnalysisJob {
        context.lockAndRequireReady(ownerId, rentalId)

        val existing = jobs.findById(rentalId)

        // 거래당 최초 비교 요청을 고정해 이후 사진 변경이나 동시 요청으로 분석 기준이 달라지지 않게 합니다.
        if (existing.isPresent) return existing.get()

        val now = LocalDateTime.now(clock)

        if (
            jobs.countByOwnerIdAndCreatedAtGreaterThanEqual(
                ownerId,
                now.toLocalDate().atStartOfDay()
            ) >= properties.dailyConditionLimit
        ) {
            throw CustomException(ErrorCode.AI_CONDITION_DAILY_LIMIT)
        }

        return jobs.save(
            ConditionAnalysisJob(
                rentalId = rentalId,
                ownerId = ownerId,
                jobId = requireNotNull(request.jobId).toString(),
                requestJson = mapper.writeValueAsString(request),
                createdAt = now
            )
        )
    }
}
