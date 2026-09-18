package iter.payment.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "toss")
data class TossProperties @JvmOverloads constructor(
    val clientKey: String,
    val secretKey: String,
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.tosspayments.com"
    }
}
