package iter.device.config

import iter.common.logging.MdcTaskDecorator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
class DeviceAsyncConfig(
    private val mdcTaskDecorator: MdcTaskDecorator,
) {

    @Bean(name = [S3_TASK_EXECUTOR])
    fun s3TaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.setCorePoolSize(2)
        executor.setMaxPoolSize(4)
        executor.setQueueCapacity(100)
        executor.setThreadNamePrefix("s3-cleanup-")
        executor.setTaskDecorator(mdcTaskDecorator)
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(10)
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.initialize()
        return executor
    }

    companion object {
        // EquipmentImageCleanupService 가 @Async(DeviceAsyncConfig.S3_TASK_EXECUTOR) 의
        // 애너테이션 인자로 참조하므로 const 여야 한다.
        const val S3_TASK_EXECUTOR = "s3TaskExecutor"
    }
}
