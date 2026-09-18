package iter.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.ai")
open class AiServiceProperties {
    var baseUrl: String = "http://localhost:8000"
    var internalApiKey: String = ""
    var connectTimeout: Duration = Duration.ofSeconds(2)
    var readTimeout: Duration = Duration.ofSeconds(5)
    var dailyDraftLimit: Int = 5
    var dailyReportLimit: Int = 20
    var dailyConditionLimit: Int = 5
}
