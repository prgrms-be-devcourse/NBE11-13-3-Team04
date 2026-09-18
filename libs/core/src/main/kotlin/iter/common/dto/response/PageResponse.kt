package iter.common.dto.response

import org.springframework.data.domain.Page

@JvmRecord
data class PageResponse<T : Any>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        @JvmStatic
        fun <T : Any> from(page: Page<T>): PageResponse<T> =
            PageResponse(
                page.content.toList(),
                page.number,
                page.size,
                page.totalElements,
                page.totalPages,
            )
    }
}
