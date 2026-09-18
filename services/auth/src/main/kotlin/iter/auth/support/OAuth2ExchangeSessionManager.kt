package iter.auth.support

import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class OAuth2ExchangeSessionManager {

    fun store(request: HttpServletRequest, exchangeCode: String) {
        request.getSession(true).setAttribute(ATTRIBUTE_NAME, exchangeCode)
    }

    fun consume(request: HttpServletRequest): String {
        val session = request.getSession(false) ?: throw CustomException(ErrorCode.OAUTH_TOKEN_INVALID)

        synchronized(session) {
            val value = try {
                val attribute = session.getAttribute(ATTRIBUTE_NAME)
                session.removeAttribute(ATTRIBUTE_NAME)
                attribute
            } catch (exception: IllegalStateException) {
                throw CustomException(ErrorCode.OAUTH_TOKEN_INVALID)
            }

            if (value !is String || value.isBlank()) {
                throw CustomException(ErrorCode.OAUTH_TOKEN_INVALID)
            }
            return value
        }
    }

    fun invalidate(request: HttpServletRequest) {
        val session = request.getSession(false) ?: return
        try {
            session.invalidate()
        } catch (ignored: IllegalStateException) {
            // 동시 교환 요청이 먼저 세션을 폐기한 경우에도 정리는 완료된 것으로 본다.
        }
    }

    companion object {
        @JvmField
        val ATTRIBUTE_NAME: String = OAuth2ExchangeSessionManager::class.java.name + ".EXCHANGE_CODE"
    }
}
