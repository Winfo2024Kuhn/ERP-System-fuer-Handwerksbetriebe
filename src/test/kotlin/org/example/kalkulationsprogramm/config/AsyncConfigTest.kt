package org.example.kalkulationsprogramm.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ScheduledThreadPoolExecutor

class AsyncConfigTest {

    private val config = AsyncConfig()

    @Test
    fun taskSchedulerHatMehrAlsEinenThread() {
        val scheduler = config.taskScheduler()

        assertThat(scheduler).isInstanceOf(ThreadPoolTaskScheduler::class.java)
        val pool = scheduler as ThreadPoolTaskScheduler
        val exec = pool.scheduledExecutor as ScheduledThreadPoolExecutor
        assertThat(exec.corePoolSize)
            .`as`("taskScheduler muss > 1 Thread haben, sonst blockiert ein haengender @Scheduled-Job alle anderen")
            .isGreaterThan(1)
    }

    @Test
    fun taskExecutorIstThreadPoolUndNichtSimpleAsyncTaskExecutor() {
        val executor = config.taskExecutor()

        assertThat(executor)
            .`as`("Default @Async-Executor muss ein ThreadPool sein, nicht SimpleAsyncTaskExecutor")
            .isInstanceOf(ThreadPoolTaskExecutor::class.java)

        val pool = executor as ThreadPoolTaskExecutor
        assertThat(pool.corePoolSize).isGreaterThan(0)
        assertThat(pool.maxPoolSize).isGreaterThanOrEqualTo(pool.corePoolSize)
    }

    @Test
    fun emailTaskExecutorBleibtVerfuegbarFuerExpliziteNutzung() {
        val executor = config.emailTaskExecutor()

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor::class.java)
        val pool = executor as ThreadPoolTaskExecutor
        assertThat(pool.corePoolSize).isPositive()
    }
}
