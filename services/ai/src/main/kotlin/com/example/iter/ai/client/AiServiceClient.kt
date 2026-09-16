package com.example.iter.ai.client

import com.example.iter.ai.config.AiServiceProperties
import com.example.iter.ai.dto.AiJobAccepted
import com.example.iter.ai.dto.AiJobRequest
import com.example.iter.ai.dto.AiJobStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.util.UUID

@Component
class AiServiceClient(private val restClient: RestClient) {
    // 연결·응답 제한과 내부 API key를 적용한 Python AI 전용 HTTP client를 만듭니다.
    @Autowired
    constructor(properties: AiServiceProperties) : this(createRestClient(properties))

    // Python AI 서비스에 작업을 접수하고 UUID와 초기 상태가 요청과 일치하는지 확인합니다.
    fun createJob(request: AiJobRequest): AiJobAccepted {
        try {
            val response = restClient.post()
                .uri("/internal/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiJobAccepted::class.java)

            // 정상 HTTP 응답이어도 job ID나 필수 상태가 다르면 신뢰할 수 없는 응답으로 봅니다.
            if (
                response == null ||
                request.jobId == null ||
                request.jobId != response.jobId ||
                response.status == null
            ) {
                throw AiServiceException(0)
            }

            return response
        } catch (exception: RestClientException) {
            throw failure(exception)
        }
    }

    // 작업 상태를 한 번만 조회하며 대기 반복은 화면의 polling에 맡깁니다.
    fun getJob(jobId: UUID): AiJobStatus {
        try {
            val response = restClient.get()
                .uri("/internal/v1/jobs/{jobId}", jobId)
                .retrieve()
                .body(AiJobStatus::class.java)

            // 조회한 ID와 응답 ID가 일치해야 다른 작업 결과가 노출되지 않습니다.
            if (response == null || jobId != response.jobId || response.status == null) {
                throw AiServiceException(0)
            }

            return response
        } catch (exception: RestClientException) {
            throw failure(exception)
        }
    }

    // 외부 응답 본문은 노출하지 않고 상태 코드만 공통 예외로 변환합니다.
    private fun failure(exception: RestClientException): AiServiceException {
        val status = if (exception is RestClientResponseException) {
            exception.statusCode.value()
        } else {
            0
        }

        log.warn("AI 서비스 호출 실패: status={}", status)

        return AiServiceException(status)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(AiServiceClient::class.java)

        private fun createRestClient(properties: AiServiceProperties): RestClient {
            val httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()

            val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
                setReadTimeout(properties.readTimeout)
            }

            val builder = RestClient.builder()
                .baseUrl(properties.baseUrl)
                .requestFactory(requestFactory)

            if (properties.internalApiKey.isNotBlank()) {
                builder.defaultHeader("X-Internal-Api-Key", properties.internalApiKey)
            }

            return builder.build()
        }
    }
}
