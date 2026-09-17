package com.example.iter.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

private const val MAX_REQUEST_ID_LENGTH = 64
private val SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,$MAX_REQUEST_ID_LENGTH}")

/** 모든 HTTP 요청에 추적 ID를 부여하고 처리 결과와 소요 시간을 한 줄로 기록한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = resolveRequestId(request)
        val previousRequestId = MDC.get(REQUEST_ID_MDC_KEY)
        val startedAt = System.nanoTime()

        MDC.put(REQUEST_ID_MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            logCompletedRequest(request, response, durationMs)
            restorePreviousRequestId(previousRequestId)
        }
    }

    private fun resolveRequestId(request: HttpServletRequest): String {
        val suppliedRequestId = request.getHeader(REQUEST_ID_HEADER)
        if (suppliedRequestId != null && SAFE_REQUEST_ID.matcher(suppliedRequestId).matches()) {
            return suppliedRequestId
        }
        return UUID.randomUUID().toString()
    }

    private fun logCompletedRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
        durationMs: Long,
    ) {
        val status = response.status
        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            log.warn(
                "HTTP 요청 완료: method={}, path={}, status={}, durationMs={}",
                request.method,
                request.requestURI,
                status,
                durationMs,
            )
            return
        }

        log.info(
            "HTTP 요청 완료: method={}, path={}, status={}, durationMs={}",
            request.method,
            request.requestURI,
            status,
            durationMs,
        )
    }

    private fun restorePreviousRequestId(previousRequestId: String?) {
        if (previousRequestId == null) {
            MDC.remove(REQUEST_ID_MDC_KEY)
            return
        }
        MDC.put(REQUEST_ID_MDC_KEY, previousRequestId)
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-ID"
        const val REQUEST_ID_MDC_KEY = "requestId"
    }
}
