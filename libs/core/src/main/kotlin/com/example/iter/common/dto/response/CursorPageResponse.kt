package com.example.iter.common.dto.response

import com.example.iter.common.pagination.CursorCodec
import com.example.iter.common.pagination.CursorKey

/**
 * 전체 건수 조회 없이 다음 페이지 존재 여부와 다음 커서를 반환합니다.
 */
@JvmRecord
data class CursorPageResponse<T>(
    val content: List<T>,
    val nextCursor: String?,
    val hasNext: Boolean,
    val size: Int,
) {
    companion object {
        @JvmStatic
        fun <S, T> from(
            fetched: List<S>,
            requestedSize: Int,
            mapper: (S) -> T,
            cursorKeyExtractor: (S) -> CursorKey,
        ): CursorPageResponse<T> {
            val hasNext = fetched.size > requestedSize

            val pageContent = if (hasNext) fetched.subList(0, requestedSize) else fetched

            val content = pageContent.map(mapper)

            val nextCursor = if (hasNext && pageContent.isNotEmpty()) {
                CursorCodec.encode(cursorKeyExtractor(pageContent.last()))
            } else {
                null
            }

            return CursorPageResponse(content, nextCursor, hasNext, requestedSize)
        }
    }
}
