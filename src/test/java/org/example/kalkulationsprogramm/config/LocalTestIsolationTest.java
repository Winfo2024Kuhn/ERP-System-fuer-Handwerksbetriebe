package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.core.task.TaskExecutor;
import java.util.concurrent.CompletableFuture;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.mail.SmtpHtmlMailSender;
import org.example.email.ImapAppendService;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalTestIsolationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncAndSchedulingConfiguration.class)
            .withPropertyValues("app.background-jobs.enabled=false");

    @Test
    void disablesPeriodicSchedulingButKeepsTheAsyncExecutor() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context.getBean("taskExecutor")).isInstanceOf(TaskExecutor.class);
            assertThat(context.getBean(AsyncProbe.class).run().join())
                    .startsWith("async-");
        });
    }

    @Test
    void blocksMainMailboxConnectionTestsAndDirectSmtpSender() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-test");
        LocalTestMailPolicy policy = new LocalTestMailPolicy(environment);
        SystemSettingsService settings = new SystemSettingsService(
                mock(org.example.kalkulationsprogramm.repository.SystemSettingRepository.class),
                environment, policy);

        assertThat(settings.testSmtp("127.0.0.1", 25, "dummy", "dummy", null).message())
                .contains("lokalen Testprofil");
        assertThat(settings.testImap("127.0.0.1", 143, "dummy", "dummy").message())
                .contains("lokalen Testprofil");
        assertThrows(IllegalStateException.class, () -> new SmtpHtmlMailSender(settings, policy)
                .send("erp@example.com", "test@example.com", "Test", "<p>Test</p>", java.util.Map.of()));
    }

    @Test
    void blocksImapAppendBeforeOpeningTheSocket() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-test");
        LocalTestMailPolicy policy = new LocalTestMailPolicy(environment);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.isImapConfigured()).thenReturn(true);
        when(settings.getImapHost()).thenReturn("127.0.0.1");
        when(settings.getImapUsername()).thenReturn("dummy");
        when(settings.getImapPassword()).thenReturn("dummy");

        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(150);
            when(settings.getImapPort()).thenReturn(server.getLocalPort());
            new ImapAppendService(settings, policy).appendToSent("erp@example.com",
                    java.util.List.of("test@example.com"), "Test", "<p>Test</p>", java.util.List.of(), null);
            assertThrows(java.net.SocketTimeoutException.class, server::accept);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @Import({AsyncConfig.class, BackgroundSchedulingConfig.class})
    static class AsyncAndSchedulingConfiguration {
        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    static class AsyncProbe {
        @Async
        public CompletableFuture<String> run() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
