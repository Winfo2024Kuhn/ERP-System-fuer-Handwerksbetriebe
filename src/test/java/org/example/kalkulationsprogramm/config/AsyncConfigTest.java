package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Regressionstests für AsyncConfig.
 *
 * Hintergrund: Vor diesem Fix gab es nur den `emailTaskExecutor`, kein
 * `taskExecutor` und kein expliziter `taskScheduler`. Spring fiel deshalb
 * für @Async auf SimpleAsyncTaskExecutor zurück (unbegrenzte Threads),
 * und @Scheduled lief auf einem Single-Thread-Pool. Beides führte dazu,
 * dass der Email-Import nach einem hängenden Job nie wieder angestoßen
 * wurde – nur ein Server-Neustart half.
 */
class AsyncConfigTest {

    private final AsyncConfig config = new AsyncConfig();

    @Test
    void taskSchedulerHatMehrAlsEinenThread() {
        TaskScheduler scheduler = config.taskScheduler();

        assertThat(scheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
        ThreadPoolTaskScheduler pool = (ThreadPoolTaskScheduler) scheduler;
        // getPoolSize() liefert 0 bevor Tasks laufen – stattdessen
        // konfigurierten Wert über den ScheduledExecutor abfragen.
        ScheduledThreadPoolExecutor exec =
                (ScheduledThreadPoolExecutor) pool.getScheduledExecutor();
        assertThat(exec.getCorePoolSize())
                .as("taskScheduler muss > 1 Thread haben, sonst blockiert ein hängender @Scheduled-Job alle anderen")
                .isGreaterThan(1);
    }

    @Test
    void taskExecutorIstThreadPoolUndNichtSimpleAsyncTaskExecutor() {
        TaskExecutor executor = config.taskExecutor();

        assertThat(executor)
                .as("Default @Async-Executor muss ein ThreadPool sein, nicht SimpleAsyncTaskExecutor")
                .isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        assertThat(pool.getCorePoolSize()).isGreaterThan(0);
        assertThat(pool.getMaxPoolSize()).isGreaterThanOrEqualTo(pool.getCorePoolSize());
    }

    @Test
    void emailTaskExecutorBleibtVerfuegbarFuerExpliziteNutzung() {
        TaskExecutor executor = config.emailTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        assertThat(pool.getCorePoolSize()).isPositive();
    }
}
