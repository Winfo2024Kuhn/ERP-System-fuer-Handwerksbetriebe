package org.example.kalkulationsprogramm.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.example.email.EmailService;
import org.springframework.mock.env.MockEnvironment;
import jakarta.mail.MessagingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

class LocalTestMailPolicyTest {

    @Test
    void blocksEveryMailAccountByDefaultInLocalTest() {
        LocalTestMailPolicy policy = policy(true, false);

        assertThrows(IllegalStateException.class, () -> policy.pruefeNetzwerkzugriff("EINKAUF"));
        assertThrows(IllegalStateException.class, () -> policy.pruefeNetzwerkzugriff("HAUPT"));
        assertThrows(IllegalStateException.class, () -> policy.pruefeNetzwerkzugriff(null));
    }

    @Test
    void optsInOnlyForTheExplicitPurchaseAccount() {
        LocalTestMailPolicy policy = policy(true, true);

        assertDoesNotThrow(() -> policy.pruefeNetzwerkzugriff("EINKAUF"));
        assertThrows(IllegalStateException.class, () -> policy.pruefeNetzwerkzugriff("HAUPT"));
        assertThrows(IllegalStateException.class, () -> policy.pruefeNetzwerkzugriff("DOKUMENTE"));
        assertThrows(IllegalStateException.class, () -> policy.pruefeNetzwerkzugriff("unbekannt"));
    }

    @Test
    void leavesMailAccessUnchangedOutsideLocalTest() {
        LocalTestMailPolicy policy = policy(false, false);

        assertDoesNotThrow(() -> policy.pruefeNetzwerkzugriff("HAUPT"));
        assertDoesNotThrow(() -> policy.pruefeNetzwerkzugriff(null));
    }

    @Test
    void blocksLegacySmtpBeforeEmailServiceCanOpenANetworkConnection() {
        LocalTestMailPolicy policy = policy(true, true);
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(150);
            EmailService service = new EmailService("127.0.0.1", server.getLocalPort(), "dummy", "dummy", policy);

            assertThrows(IllegalStateException.class, () -> service.sendEmailAndReturnMessageId(
                    "test@example.com", null, "erp@example.com", "Test", "<p>Test</p>", null, null));
            assertThrows(SocketTimeoutException.class, server::accept);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void allowsOnlyPurchaseAccountToReachTheLocalSmtpTransport() throws Exception {
        LocalTestMailPolicy policy = policy(true, true);
        int unusedLocalPort;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            unusedLocalPort = server.getLocalPort();
        }
        EmailService einkauf = new EmailService("127.0.0.1", unusedLocalPort, "dummy", "dummy", policy)
                .mitKontoId("EINKAUF");
        assertThrows(MessagingException.class, () -> einkauf.sendEmailAndReturnMessageId(
                "test@example.com", null, "erp@example.com", "Test", "<p>Test</p>", null, null));
        EmailService dokumente = new EmailService("127.0.0.1", unusedLocalPort, "dummy", "dummy", policy)
                .mitKontoId("DOKUMENTE");
        assertThrows(IllegalStateException.class, () -> dokumente.sendEmailAndReturnMessageId(
                "test@example.com", null, "erp@example.com", "Test", "<p>Test</p>", null, null));
    }

    private static LocalTestMailPolicy policy(boolean localTest, boolean enabled) {
        MockEnvironment environment = new MockEnvironment();
        if (localTest) environment.setActiveProfiles("local-test");
        environment.setProperty("app.local-test.manual-mail.enabled", Boolean.toString(enabled));
        return new LocalTestMailPolicy(environment);
    }
}
