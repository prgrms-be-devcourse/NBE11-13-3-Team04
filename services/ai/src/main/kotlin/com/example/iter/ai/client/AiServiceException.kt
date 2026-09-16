package com.example.iter.ai.client

// 외부 응답 원문 대신 안전한 공통 메시지와 상태 코드만 보관합니다.
// 0은 HTTP 응답을 받지 못했거나 응답 형식이 잘못된 경우입니다.
open class AiServiceException(val statusCode: Int) : RuntimeException("AI 서비스 요청을 처리하지 못했습니다.")
