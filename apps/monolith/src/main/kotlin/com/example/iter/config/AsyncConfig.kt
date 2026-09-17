package com.example.iter.config

import com.example.iter.common.logging.MdcTaskDecorator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig(
    private val mdcTaskDecorator: MdcTaskDecorator,
) {

    // 메일 발송 전용 스레드풀 — 요청 처리 스레드나 다른 비동기 작업과 자원을 다투지 않도록 분리
    @Bean(name = ["mailExecutor"])
    fun mailExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.setQueueCapacity(100)
        executor.setThreadNamePrefix("mail-")
        executor.setTaskDecorator(mdcTaskDecorator)
        executor.initialize()
        return executor
    }
}
