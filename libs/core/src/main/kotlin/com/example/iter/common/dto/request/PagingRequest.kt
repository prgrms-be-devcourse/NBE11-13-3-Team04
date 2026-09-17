package com.example.iter.common.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

@JvmRecord
data class PagingRequest(
    @field:Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
    val page: Int = 0,

    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    val size: Int = 20,
) {
    // 자바 시절 record 컴팩트 생성자가 null(쿼리 파라미터 미전달)을 0/20으로 바꿔주던 것과
    // 동일한 통로 — page()/size()가 boxed Integer였던 자바 호출부(테스트 등)가 null을
    // 그대로 넘겨도 여기서 기본값으로 정규화된다.
    constructor(page: Int?, size: Int?) : this(page ?: 0, size ?: 20)
}
