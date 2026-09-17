package com.example.iter.common.logging

import org.slf4j.MDC
import org.springframework.core.task.TaskDecorator
import org.springframework.stereotype.Component

/** 요청 스레드의 MDC 값을 비동기 작업 스레드로 전달하고 작업 종료 후 원래 값으로 복원한다. */
@Component
class MdcTaskDecorator : TaskDecorator {

    override fun decorate(runnable: Runnable): Runnable {
        val submittingContext = MDC.getCopyOfContextMap()

        return Runnable {
            val workerContext = MDC.getCopyOfContextMap()
            try {
                replaceContext(submittingContext)
                runnable.run()
            } finally {
                replaceContext(workerContext)
            }
        }
    }

    private fun replaceContext(context: Map<String, String>?) {
        if (context == null) {
            MDC.clear()
            return
        }
        MDC.setContextMap(context)
    }
}
