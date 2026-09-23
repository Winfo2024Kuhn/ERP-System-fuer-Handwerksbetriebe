package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent;
import org.example.kalkulationsprogramm.event.EmailBackfillEventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class LocalTestAfterCommitAsyncTest {
    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(TestConfiguration.class);

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void afterCommitDispatchesAsynchronouslyWhileSchedulingIsDisabled() throws Exception {
        assertThat(context.getBean("taskExecutor", TaskExecutor.class)).isNotNull();
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.execute(status -> {
            context.publishEvent(event());
            return null;
        });
        assertThat(context.getBean(Observation.class).called.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(context.getBean(Observation.class).threadName)
                .startsWith("email-sync-");
    }

    @Test
    void rollbackDoesNotDispatchAfterCommitListener() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.execute(status -> {
            context.publishEvent(event());
            status.setRollbackOnly();
            return null;
        });
        assertThat(context.getBean(Observation.class).called.await(150, TimeUnit.MILLISECONDS))
                .isFalse();
    }

    private static EmailAddressChangedEvent event() {
        return EmailAddressChangedEvent.forNewEntity(
                EmailAddressChangedEvent.EntityType.ANGEBOT, 7L, java.util.List.of("test@example.com"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean(name = "taskExecutor")
        TaskExecutor taskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setThreadNamePrefix("async-");
            return executor;
        }

        @Bean(name = "emailTaskExecutor")
        TaskExecutor emailTaskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setThreadNamePrefix("email-sync-");
            return executor;
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }

        @Bean
        ObservedEmailBackfillListener emailBackfillEventListener(Observation observation) {
            return new ObservedEmailBackfillListener(observation);
        }

        @Bean
        Observation observation() {
            return new Observation();
        }
    }

    static class ObservedEmailBackfillListener extends EmailBackfillEventListener {
        private final Observation observation;

        ObservedEmailBackfillListener(Observation observation) {
            super(mock(org.example.kalkulationsprogramm.repository.KundeRepository.class),
                    mock(org.example.kalkulationsprogramm.repository.LieferantenRepository.class),
                    mock(org.example.kalkulationsprogramm.repository.AnfrageRepository.class),
                    mock(org.example.kalkulationsprogramm.repository.ProjektRepository.class),
                    mock(org.example.kalkulationsprogramm.repository.EmailRepository.class),
                    mock(org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService.class),
                    mock(org.example.kalkulationsprogramm.service.EmailAutoAssignmentService.class));
            this.observation = observation;
        }

        @Override
        @Async("emailTaskExecutor")
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void handleEmailAddressChanged(EmailAddressChangedEvent event) {
            observation.threadName = Thread.currentThread().getName();
            observation.called.countDown();
            super.handleEmailAddressChanged(event);
        }
    }

    static class Observation {
        final CountDownLatch called = new CountDownLatch(1);
        volatile String threadName;
    }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) { }

        @Override
        protected void doCommit(DefaultTransactionStatus status) { }

        @Override
        protected void doRollback(DefaultTransactionStatus status) { }
    }
}
