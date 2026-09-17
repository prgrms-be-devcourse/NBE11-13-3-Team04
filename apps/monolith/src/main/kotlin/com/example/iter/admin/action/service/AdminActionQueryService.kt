package com.example.iter.admin.action.service

import com.example.iter.admin.action.dto.request.AdminActionSearchRequest
import com.example.iter.admin.action.dto.response.AdminActionResponse
import com.example.iter.admin.action.util.AdminActionMapper
import com.example.iter.common.audit.domain.repository.AdminActionRepository
import com.example.iter.common.dto.response.CursorPageResponse
import com.example.iter.common.pagination.CursorCodec
import com.example.iter.common.pagination.CursorKey
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminActionQueryService(private val adminActionRepository: AdminActionRepository, private val adminActionMapper: AdminActionMapper) {
    // 생성 시각과 ID를 묶은 커서로 다음 구간을 조회해 데이터가 늘어나도 이전 페이지를 다시 훑지 않습니다.
    @Transactional(readOnly = true)
    fun getAdminActions(request: AdminActionSearchRequest): CursorPageResponse<AdminActionResponse> {
        val cursorKey = CursorCodec.decode(request.cursor)

        // 요청 크기보다 한 건 더 조회해 별도의 count 쿼리 없이 다음 페이지 존재 여부를 판단합니다.
        val actions = adminActionRepository.searchForAdminByCursor(
            request.targetType,
            request.targetId,
            request.action,
            cursorKey?.createdAt,
            cursorKey?.id,
            PageRequest.of(0, request.size + 1)
        )

        return CursorPageResponse.from(
            actions,
            request.size,
            adminActionMapper::toResponse
        ) { action -> CursorKey(action.createdAt!!, action.id!!) }
    }
}
