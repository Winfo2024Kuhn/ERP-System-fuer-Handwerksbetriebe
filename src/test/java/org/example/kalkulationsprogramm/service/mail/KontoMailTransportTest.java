package org.example.kalkulationsprogramm.service.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Properties;
import java.nio.charset.StandardCharsets;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Nachricht;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Verschluesselung;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class KontoMailTransportTest {
    private static final String FEHLER_ANLAGE = "Die Anlage fehlt oder kann nicht gelesen werden.";
    private static final String FEHLER_LEER = "Die Anlage darf nicht leer sein.";
    private static final String FEHLER_GROESS = "Die Anlage darf höchstens 10 MiB groß sein.";
    // Test-only self-signed keypair for the in-process SMTP/IMAP dummy servers.
    private static final String DUMMY_P12 = """
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""";
    private static javax.net.ssl.SSLContext previousDefaultSslContext;
    private static java.nio.file.Path temporaryTrustStore;

    @org.junit.jupiter.api.BeforeAll
    static void configureDummyTlsTrust() throws Exception {
        previousDefaultSslContext = javax.net.ssl.SSLContext.getDefault();
        java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
        keyStore.load(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(DUMMY_P12)), "testpass".toCharArray());
        java.security.cert.Certificate cert = keyStore.getCertificate("dummy");
        java.security.KeyStore trust = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        trust.load(null, null);
        trust.setCertificateEntry("dummy", cert);
        temporaryTrustStore = java.nio.file.Files.createTempFile("dummy-mail-trust", ".jks");
        try (var output = java.nio.file.Files.newOutputStream(temporaryTrustStore)) {
            trust.store(output, "testpass".toCharArray());
        }
        System.setProperty("javax.net.ssl.trustStore", temporaryTrustStore.toString());
        System.setProperty("javax.net.ssl.trustStorePassword", "testpass");
        var trustManagers = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trust);
        javax.net.ssl.SSLContext.setDefault(javax.net.ssl.SSLContext.getInstance("TLS"));
        javax.net.ssl.SSLContext.getDefault().init(null, trustManagers.getTrustManagers(), new java.security.SecureRandom());
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreDefaultTlsTrust() throws Exception {
        javax.net.ssl.SSLContext.setDefault(previousDefaultSslContext);
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
        if (temporaryTrustStore != null) java.nio.file.Files.deleteIfExists(temporaryTrustStore);
    }


    @Test
    void vorbereitetFriertMessageIdEinUndGesendeteBytesBleibenUnveraendert() throws Exception {
        LocalTestMailPolicy policy = mock(LocalTestMailPolicy.class);
        KontoMailTransport transport = new KontoMailTransport(policy);
        var konto = konto();
        var nachricht = new MailTransportDto.Nachricht("<fest@example.test>", "test@example.com", "Anfrage",
                "<p>Dummy</p>", "<vorher@example.test>", List.of("<alt@example.test>"), List.of());

        byte[] mime = transport.vorbereiten(konto, nachricht);
        MimeMessage parsed = new MimeMessage(Session.getInstance(new Properties()), new java.io.ByteArrayInputStream(mime));

        assertEquals("<fest@example.test>", parsed.getMessageID());
        assertEquals("<vorher@example.test>", parsed.getHeader("In-Reply-To", null));
        assertEquals("<alt@example.test>", parsed.getHeader("References", null));
        assertEquals("<fest@example.test>", parsed.getMessageID());
        verify(policy, never()).pruefeNetzwerkzugriff(anyString());
    }

    @Test
    void localTestSperreWirdVorBereitetemVersandGeprueft() {
        LocalTestMailPolicy policy = mock(LocalTestMailPolicy.class);
        doThrow(new IllegalStateException("gesperrt")).when(policy).pruefeNetzwerkzugriff("EINKAUF");
        KontoMailTransport transport = new KontoMailTransport(policy);

        var result = transport.sendenVorbereitet(konto(), new byte[0]);
        assertEquals(MailTransportDto.Status.SICHER_FEHLGESCHLAGEN, result.status());
        verify(policy).pruefeNetzwerkzugriff("EINKAUF");
    }

    @Test
    void anhaengeWerdenAlsInMemoryMimeTeileVerarbeitet() throws Exception {
        LocalTestMailPolicy policy = mock(LocalTestMailPolicy.class);
        KontoMailTransport transport = new KontoMailTransport(policy);
        var anlage = new EmailService.Attachment("Dummy".getBytes(java.nio.charset.StandardCharsets.UTF_8), "../angebot.pdf", "application/pdf");
        byte[] mime = transport.vorbereiten(konto(), new MailTransportDto.Nachricht("<id@example.test>", "test@example.com", "Anfrage", "<p>Hallo</p>", null, List.of(), List.of(anlage)));
        MimeMessage parsed = new MimeMessage(Session.getInstance(new Properties()), new java.io.ByteArrayInputStream(mime));

        assertTrue(parsed.getContentType().toLowerCase().contains("multipart"));
        assertFalse(new String(mime, java.nio.charset.StandardCharsets.ISO_8859_1).contains("../angebot.pdf"));
    }

    @Test
    void konfiguriertesGesamtMimeLimitBlockiertStilleAnlagenkuerzung() {
        KontoMailTransport transport = new KontoMailTransport(mock(LocalTestMailPolicy.class), 512);
        var nachricht = new Nachricht("<id@example.test>", "test@example.com", "Test", "<p>Dummy</p>", null,
                List.of(), List.of(new EmailService.Attachment(new byte[1024], "beleg.pdf", "application/pdf")));

        assertThrows(IllegalArgumentException.class, () -> transport.vorbereiten(konto(), nachricht));
    }

    @Test
    void mimeAufbauBlockiertAnlageMitFehlendenBytes() {
        assertEquals(FEHLER_ANLAGE, assertThrows(IllegalArgumentException.class,
                () -> mimeMitAnlagen(List.of(new EmailService.Attachment((byte[]) null, "fehlend.pdf", "application/pdf")))).getMessage());
    }

    @Test
    void mimeAufbauBlockiertLeereByteAnlage() {
        assertEquals(FEHLER_LEER, assertThrows(IllegalArgumentException.class,
                () -> mimeMitAnlagen(List.of(new EmailService.Attachment(new byte[0], "leer.pdf", "application/pdf")))).getMessage());
    }

    @Test
    void mimeAufbauBlockiertFehlendeDateiOhnePfadleck(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("private-path-secret.pdf");
        var ex = assertThrows(IllegalArgumentException.class, () -> mimeMitAnlagen(List.of(
                new EmailService.Attachment(null, "fehlt.pdf", "application/pdf", missing.toFile()))));
        assertEquals(FEHLER_ANLAGE, ex.getMessage());
        assertFalse(ex.getMessage().contains(tempDir.toString()));
    }

    @Test
    void mimeAufbauBlockiertNichtLesbareDatei(@TempDir Path tempDir) {
        assertEquals(FEHLER_ANLAGE, assertThrows(IllegalArgumentException.class, () -> mimeMitAnlagen(List.of(
                new EmailService.Attachment(null, "ordner.pdf", "application/pdf", tempDir.toFile())))).getMessage());
    }

    @Test
    void mimeAufbauBlockiertLeereDatei(@TempDir Path tempDir) throws Exception {
        Path empty = Files.createFile(tempDir.resolve("leer.pdf"));
        assertEquals(FEHLER_LEER, assertThrows(IllegalArgumentException.class, () -> mimeMitAnlagen(List.of(
                new EmailService.Attachment(null, "leer.pdf", "application/pdf", empty.toFile())))).getMessage());
    }

    @Test
    void mimeAufbauBlockiertZuGrosseByteAnlage() {
        assertEquals(FEHLER_GROESS, assertThrows(IllegalArgumentException.class, () -> mimeMitAnlagen(List.of(
                new EmailService.Attachment(new byte[10 * 1024 * 1024 + 1], "gross.pdf", "application/pdf")))).getMessage());
    }

    @Test
    void mimeAufbauBlockiertZuGrosseDateiAuchOhneDaten(@TempDir Path tempDir) throws Exception {
        Path oversized = Files.createFile(tempDir.resolve("gross.pdf"));
        try (var file = new java.io.RandomAccessFile(oversized.toFile(), "rw")) {
            file.setLength(10L * 1024 * 1024 + 1);
        }
        assertEquals(FEHLER_GROESS, assertThrows(IllegalArgumentException.class, () -> mimeMitAnlagen(List.of(
                new EmailService.Attachment(null, "gross.pdf", "application/pdf", oversized.toFile())))).getMessage());
    }

    @Test
    void dateiAttachmentKonstruktorLiestZuGrosseDateiNichtVorab(@TempDir Path tempDir) throws Exception {
        Path oversized = Files.createFile(tempDir.resolve("gross-konstruierter-anhang.pdf"));
        try (var file = new java.io.RandomAccessFile(oversized.toFile(), "rw")) {
            file.setLength(10L * 1024 * 1024 + 1);
        }
        assertEquals(FEHLER_GROESS, assertThrows(IllegalArgumentException.class, () -> mimeMitAnlagen(List.of(
                new EmailService.Attachment(oversized.toFile(), "gross.pdf", "application/pdf")))).getMessage());
    }

    @Test
    void alteMehrfachVersandsignaturValidiertVorDemNetzwerk(@TempDir Path tempDir) throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Ausgang.ANGENOMMEN)) {
            EmailService legacy = new EmailService("localhost", smtp.port(), "dummy", "dummy",
                    mock(LocalTestMailPolicy.class));
            Path missingPath = tempDir.resolve("private-path-secret.pdf");
            var missing = new EmailService.Attachment(null, "fehlt.pdf", "application/pdf", missingPath.toFile());
            var ex = assertThrows(IllegalArgumentException.class, () -> legacy.sendEmailWithMultipleAttachments(
                    "test@example.com", null, "erp@example.test", "Test", "<p>Dummy</p>", null, List.of(missing)));
            assertEquals(FEHLER_ANLAGE, ex.getMessage());
            assertFalse(ex.getMessage().contains(tempDir.toString()));
            assertFalse(smtp.awaitConnection());
        }
    }

    @Test
    void unvollstaendigesPaketBlockiertAlleAnlagenVorJederSmtpVerbindung() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Ausgang.ANGENOMMEN)) {
            KontoMailTransport transport = new KontoMailTransport(mock(LocalTestMailPolicy.class));
            var gueltig = new EmailService.Attachment("vollständig".getBytes(StandardCharsets.UTF_8), "ok.pdf", "application/pdf");
            var unvollstaendig = new EmailService.Attachment((byte[]) null, "fehlt.pdf", "application/pdf");
            var result = transport.senden(kontoMitPort(smtp.port()), new Nachricht("<id@example.test>", "test@example.com",
                    "Test", "<p>Dummy</p>", null, List.of(), List.of(gueltig, unvollstaendig)));
            assertEquals(MailTransportDto.Status.SICHER_FEHLGESCHLAGEN, result.status());
            assertNull(result.mime());
            assertFalse(smtp.awaitConnection());
        }
    }

    private static MimeMessage mimeMitAnlagen(List<EmailService.Attachment> attachments) throws Exception {
        return EmailService.baueMimeNachricht(Session.getInstance(new Properties()), "erp@example.test", null,
                "test@example.com", "Test", "<p>Dummy</p>", null, List.of(), attachments);
    }

    @Test
    void smtpErfolgLiefertExaktDieselbenVorbereitetenBytesZurueck() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Ausgang.ANGENOMMEN)) {
            LocalTestMailPolicy policy = mock(LocalTestMailPolicy.class);
            KontoMailTransport transport = new KontoMailTransport(policy);
            var konto = kontoMitPort(smtp.port());
            var nachricht = nachricht();
            byte[] mime = transport.vorbereiten(konto, nachricht);

            var result = transport.sendenVorbereitet(konto, mime);

            assertEquals(MailTransportDto.Status.ANGENOMMEN, result.status());
            assertArrayEquals(mime, result.mime());
            assertEquals("<fest@example.test>", result.messageId());
            assertTrue(smtp.awaitData());
            assertArrayEquals(mime, smtp.receivedData());
            verify(policy).pruefeNetzwerkzugriff("EINKAUF");
        }
    }

    @Test
    void smtpAuthentifizierungsfehlerVorDataIstSicherFehlgeschlagen() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Ausgang.AUTH_FEHLER)) {
            KontoMailTransport transport = new KontoMailTransport(mock(LocalTestMailPolicy.class));
            var result = transport.senden(kontoMitPort(smtp.port()), nachricht());
            assertEquals(MailTransportDto.Status.SICHER_FEHLGESCHLAGEN, result.status());
            assertFalse(smtp.awaitData());
        }
    }

    @Test
    void verbindungsabbruchNachDataIstUnklar() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Ausgang.ABBRUCH_NACH_DATA)) {
            KontoMailTransport transport = new KontoMailTransport(mock(LocalTestMailPolicy.class));
            var result = transport.sendenVorbereitet(kontoMitPort(smtp.port()),
                    transport.vorbereiten(kontoMitPort(smtp.port()), nachricht()));
            assertEquals(MailTransportDto.Status.UNKLAR, result.status());
            assertTrue(smtp.awaitData());
        }
    }

    @Test
    void startTlsIstPflichtUndServerpruefungAktiviert() throws Exception {
        var method = KontoMailTransport.class.getDeclaredMethod("smtpEigenschaften", MailkontoDto.ServerZugang.class);
        method.setAccessible(true);
        Properties properties = (Properties) method.invoke(new KontoMailTransport(mock(LocalTestMailPolicy.class)),
                konto().smtp());
        assertEquals("true", properties.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", properties.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", properties.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertEquals("30000", properties.getProperty("mail.smtp.writetimeout"));
    }

    @Test
    void verbindungstestAuthentifiziertSmtpUndImapOhneData() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Ausgang.ANGENOMMEN); FakeImap imap = new FakeImap()) {
            KontoMailTransport transport = new KontoMailTransport(mock(LocalTestMailPolicy.class));
            var konto = kontoMitPorts(smtp.port(), imap.port());

            var result = transport.pruefeVerbindung(konto);

            assertTrue(result.smtpErfolgreich());
            assertTrue(result.imapErfolgreich());
            assertNull(result.fehlerCode());
            assertFalse(smtp.awaitData());
            assertTrue(imap.awaitLogin());
        }
    }

    @Test
    void imapAppendFehlerNachSmtpErfolgLaesstVersandergebnisUnveraendert() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Ausgang.ANGENOMMEN); FakeImap imap = new FakeImap(true)) {
            var konto = kontoMitPorts(smtp.port(), imap.port());
            KontoMailTransport transport = new KontoMailTransport(mock(LocalTestMailPolicy.class));
            var sent = transport.senden(konto, nachricht());
            SentMailArchiver archiver = new SentMailArchiver(mock(org.example.kalkulationsprogramm.service.SystemSettingsService.class),
                    mock(LocalTestMailPolicy.class));

            var archive = archiver.archiviere(konto, sent.mime());

            assertEquals(MailTransportDto.Status.ANGENOMMEN, sent.status());
            assertFalse(archive.erfolgreich());
            assertEquals("IMAP_ARCHIV", archive.fehlerCode());
            assertTrue(imap.awaitAppend());
        }
    }

    private static Nachricht nachricht() {
        return new Nachricht("<fest@example.test>", "test@example.com", "Test", "<p>Dummy</p>", null, List.of(), List.of());
    }

    private static MailkontoService.KontoZugang kontoMitPort(int port) {
        return kontoMitPorts(port, 2993);
    }

    private static MailkontoService.KontoZugang kontoMitPorts(int smtpPort, int imapPort) {
        var alt = konto();
        var smtp = new MailkontoDto.ServerZugang("localhost", smtpPort, "dummy", "dummy", Verschluesselung.STARTTLS);
        var imap = new MailkontoDto.ServerZugang("localhost", imapPort, "dummy", "dummy", Verschluesselung.STARTTLS);
        return new MailkontoService.KontoZugang(alt.id(), alt.aktiv(), alt.fromAddress(), alt.fromName(), smtp, imap, alt.inbox(), alt.sent());
    }

    private static final class FakeSmtp implements AutoCloseable {
        enum Ausgang { ANGENOMMEN, AUTH_FEHLER, ABBRUCH_NACH_DATA }
        private final ServerSocket server;
        private final Thread worker;
        private final Ausgang ausgang;
        private volatile boolean dataSeen;
        private volatile boolean accepted;
        private volatile boolean dataReached;
        private volatile byte[] receivedData;
        FakeSmtp(Ausgang ausgang) throws Exception {
            this.ausgang = ausgang;
            this.server = new ServerSocket(0);
            this.worker = new Thread(this::serve, "dummy-smtp-test");
            worker.setDaemon(true);
            worker.start();
        }
        int port() { return server.getLocalPort(); }
        boolean awaitData() throws InterruptedException { worker.join(3000); return dataReached; }
        boolean awaitConnection() throws InterruptedException { worker.join(200); return accepted; }
        byte[] receivedData() { return receivedData; }
        private void serve() {
            Socket socket = null;
            try {
                socket = server.accept();
                accepted = true;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true);
                out.print("220 dummy.test ESMTP\r\n"); out.flush();
                String line;
                int authStep = 0;
                boolean upgraded = false;
                java.io.ByteArrayOutputStream mime = new java.io.ByteArrayOutputStream();
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("EHLO") || line.startsWith("HELO")) {
                        out.print(upgraded ? "250-dummy.test\r\n250 AUTH LOGIN\r\n" : "250-dummy.test\r\n250-STARTTLS\r\n250 AUTH LOGIN\r\n"); out.flush();
                    } else if (line.equals("STARTTLS")) {
                        out.print("220 begin TLS\r\n"); out.flush();
                        javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket) serverContext().getSocketFactory()
                                .createSocket(socket, "localhost", port(), true);
                        ssl.setUseClientMode(false);
                        ssl.startHandshake();
                        socket = ssl;
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true);
                        upgraded = true;
                    } else if (line.equals("AUTH LOGIN")) {
                        authStep = 1; out.print("334 VXNlcm5hbWU6\r\n"); out.flush();
                    } else if (authStep == 1) {
                        authStep = 2; out.print("334 UGFzc3dvcmQ6\r\n"); out.flush();
                    } else if (authStep == 2) {
                        if (ausgang == Ausgang.AUTH_FEHLER) { out.print("535 invalid credentials\r\n"); out.flush(); return; }
                        authStep = 0; out.print("235 authenticated\r\n"); out.flush();
                    } else if (line.startsWith("MAIL FROM:")) { out.print("250 sender ok\r\n"); out.flush(); }
                    else if (line.startsWith("RCPT TO:")) { out.print("250 recipient ok\r\n"); out.flush(); }
                    else if (line.equals("DATA")) { dataSeen = true; dataReached = true; out.print("354 send data\r\n"); out.flush(); }
                    else if (dataSeen && line.equals(".")) {
                        receivedData = mime.toByteArray();
                        if (ausgang == Ausgang.ABBRUCH_NACH_DATA) return;
                        out.print("250 queued\r\n"); out.flush(); dataSeen = false;
                    } else if (dataSeen) {
                        String unstuffed = line.startsWith("..") ? line.substring(1) : line;
                        mime.write(unstuffed.getBytes(StandardCharsets.US_ASCII));
                        mime.write('\r'); mime.write('\n');
                    }
                    else if (line.equals("QUIT")) { out.print("221 bye\r\n"); out.flush(); return; }
                    else { out.print("250 ok\r\n"); out.flush(); }
                }
            } catch (Exception ignored) { }
            finally { try { if (socket != null) socket.close(); } catch (Exception ignored) { } }
        }
        @Override public void close() throws Exception { server.close(); worker.join(3000); }
    }

    private static javax.net.ssl.SSLContext serverContext() throws Exception {
        java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
        keyStore.load(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(DUMMY_P12)), "testpass".toCharArray());
        var keyManagers = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, "testpass".toCharArray());
        javax.net.ssl.SSLContext context = javax.net.ssl.SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, new java.security.SecureRandom());
        return context;
    }

    private static final class FakeImap implements AutoCloseable {
        private final ServerSocket server;
        private final Thread worker;
        private final boolean rejectAppend;
        private volatile boolean loggedIn;
        private volatile boolean appendAttempted;
        FakeImap() throws Exception { this(false); }
        FakeImap(boolean rejectAppend) throws Exception {
            this.rejectAppend = rejectAppend;
            server = new ServerSocket(0);
            worker = new Thread(this::serve, "dummy-imap-test");
            worker.setDaemon(true);
            worker.start();
        }
        int port() { return server.getLocalPort(); }
        boolean awaitLogin() throws InterruptedException { worker.join(3000); return loggedIn; }
        boolean awaitAppend() throws InterruptedException { worker.join(3000); return appendAttempted; }
        private void serve() {
            Socket socket = null;
            try {
                socket = server.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true);
                out.print("* OK dummy IMAP ready\r\n"); out.flush();
                String line;
                boolean upgraded = false;
                while ((line = in.readLine()) != null) {
                    String[] parts = line.split(" ", 3);
                    String tag = parts[0];
                    String command = parts.length > 1 ? parts[1].toUpperCase(java.util.Locale.ROOT) : "";
                    if (command.equals("CAPABILITY")) {
                        out.print(upgraded ? "* CAPABILITY IMAP4rev1\r\n" + tag + " OK capability\r\n"
                                : "* CAPABILITY IMAP4rev1 STARTTLS\r\n" + tag + " OK capability\r\n"); out.flush();
                    } else if (command.equals("STARTTLS")) {
                        out.print(tag + " OK begin TLS\r\n"); out.flush();
                        javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket) serverContext().getSocketFactory()
                                .createSocket(socket, "localhost", port(), true);
                        ssl.setUseClientMode(false);
                        ssl.startHandshake();
                        socket = ssl;
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true);
                        upgraded = true;
                    } else if (command.equals("LOGIN")) {
                        loggedIn = true; out.print(tag + " OK login\r\n"); out.flush();
                    } else if (command.equals("LIST")) {
                        out.print("* LIST (\\HasNoChildren) \"/\" \"Sent\"\r\n" + tag + " OK list\r\n"); out.flush();
                    } else if (command.equals("SELECT")) {
                        out.print("* FLAGS (\\Seen \\Answered \\Flagged \\Deleted \\Draft)\r\n* 0 EXISTS\r\n* 0 RECENT\r\n* OK [UIDVALIDITY 1] valid\r\n* OK [UIDNEXT 1] next\r\n" + tag + " OK [READ-WRITE] selected\r\n"); out.flush();
                    } else if (command.equals("SEARCH")) {
                        out.print("* SEARCH\r\n" + tag + " OK search\r\n"); out.flush();
                    } else if (command.equals("APPEND")) {
                        appendAttempted = true;
                        if (rejectAppend) { out.print(tag + " BAD injected append rejection\r\n"); out.flush(); }
                        else { out.print(tag + " OK appended\r\n"); out.flush(); }
                    } else if (command.equals("LOGOUT")) {
                        out.print("* BYE logged out\r\n" + tag + " OK logout\r\n"); out.flush(); return;
                    } else { out.print(tag + " OK\r\n"); out.flush(); }
                }
            } catch (Exception ignored) { }
            finally { try { if (socket != null) socket.close(); } catch (Exception ignored) { } }
        }
        @Override public void close() throws Exception { server.close(); worker.join(3000); }
    }

    private static MailkontoService.KontoZugang konto() {
        var server = new MailkontoDto.ServerZugang("127.0.0.1", 2525, "dummy", "dummy", Verschluesselung.STARTTLS);
        var imap = new MailkontoDto.ServerZugang("127.0.0.1", 2993, "dummy", "dummy", Verschluesselung.STARTTLS);
        return new MailkontoService.KontoZugang("EINKAUF", true, "erp@example.test", "Testbetrieb", server, imap, "INBOX", "Sent");
    }
}
