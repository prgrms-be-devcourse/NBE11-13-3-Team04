package iter.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class ClockConfig {
    @Bean
    fun applicationClock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
