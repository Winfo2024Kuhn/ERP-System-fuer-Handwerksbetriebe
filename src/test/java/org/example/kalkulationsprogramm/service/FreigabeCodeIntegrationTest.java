package org.example.kalkulationsprogramm.service;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.example.kalkulationsprogramm.controller.FreigabeInternalController;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bestätigungscode von Ende zu Ende im ERP: HTTP-Endpunkte → echte Transaktionen auf der
 * Datenbank (H2) → echter SMTP-Versand an einen Testserver (GreenMail, mit TLS). Der Code
 * wird aus der empfangenen Mail gelesen – so wie der Kunde es tut.
 *
 * <p>Geprüft wird vor allem das Transaktionsverhalten, das Unit-Tests mit Mocks nicht sehen:
 * ein falscher Code bleibt gezählt (noRollbackFor), ein gescheiterter Versand hinterlässt
 * nichts, eine an anderer Stelle gescheiterte Annahme verbraucht den Code nicht, und zwei
 * gleichzeitige Klicks arbeiten dank Zeilensperre nacheinander. Nur Dummy-Daten.</p>
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ FreigabeCodeService.class, FreigabeCodeMailService.class, DokumentFreigabeService.class,
        FreigabeEmpfaengerService.class, FreigabeCodeIntegrationTest.Uhr.class })
@org.springframework.test.context.TestPropertySource(properties = "freigabe.public-base-url=https://www.example.de/")
class FreigabeCodeIntegrationTest
{
    private static final String KUNDE = "max.mustermann@example.de";
    /** Hauptpostfach – von dort kommt der Code, wie die Antwort auf eine Website-Anfrage. */
    private static final String ABSENDER = "info@example.com";
    private static final Pattern CODE_IN_MAIL = Pattern.compile("(\\d{3}) (\\d{3})");

    @RegisterExtension
    static final GreenMailExtension MAILSERVER = new GreenMailExtension(ServerSetupTest.SMTPS);

    private static SSLContext vorherigerKontext;

    @MockitoBean private SystemSettingsService systemSettingsService;
    @MockitoBean private FirmeninformationService firmeninformationService;
    @MockitoBean private EmailSignatureService emailSignatureService;
    @MockitoBean private AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;
    @MockitoBean private AusgangsGeschaeftsDokumentAuditService ausgangsGeschaeftsDokumentAuditService;
    @MockitoBean private WebPushService webPushService;
    @MockitoBean private DateiSpeicherService dateiSpeicherService;
    @MockitoBean private AutoAuftragsbestaetigungVersandService autoAuftragsbestaetigungVersandService;
    @MockitoBean private ProjektManagementService projektManagementService;
    @MockitoBean private TaskExecutor taskExecutor;

    @Autowired private DokumentFreigabeRepository repository;
    @Autowired private DokumentFreigabeService freigabeService;
    @Autowired private FreigabeCodeService codeService;
    @Autowired private FreigabeEmpfaengerService empfaengerService;
    @Autowired private VerstellbareUhr uhr;
    @Autowired private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private String uuid;

    /** Uhr, die der Test vorstellen kann – für Wartezeit und Ablauf. */
    static class VerstellbareUhr extends Clock
    {
        private volatile Instant jetzt = Instant.parse("2026-10-11T12:00:00Z");

        void vor(Duration dauer) { jetzt = jetzt.plus(dauer); }

        @Override public ZoneId getZone() { return ZoneId.of("Europe/Berlin"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return jetzt; }
    }

    @TestConfiguration
    static class Uhr
    {
        @Bean
        VerstellbareUhr clock() { return new VerstellbareUhr(); }
    }

    /**
     * GreenMail nutzt ein selbstsigniertes Zertifikat aus seinem eigenen Keystore. Diesem einen
     * Zertifikat wird für die Dauer des Tests vertraut – kein "alles akzeptieren".
     */
    @BeforeAll
    static void vertraueTestserver() throws Exception
    {
        vorherigerKontext = SSLContext.getDefault();
        KeyStore greenmail = KeyStore.getInstance("PKCS12");
        try (InputStream in = GreenMailExtension.class.getClassLoader().getResourceAsStream("greenmail.p12"))
        {
            greenmail.load(in, "changeit".toCharArray());
        }
        TrustManagerFactory vertrauen = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        vertrauen.init(greenmail);
        SSLContext nurGreenMail = SSLContext.getInstance("TLS");
        nurGreenMail.init(null, vertrauen.getTrustManagers(), null);
        SSLContext.setDefault(nurGreenMail);
    }

    @AfterAll
    static void vertrauenZuruecksetzen()
    {
        SSLContext.setDefault(vorherigerKontext);
    }

    @BeforeEach
    void vorbereiten()
    {
        MAILSERVER.setUser(ABSENDER, ABSENDER, "pw-hauptpostfach");
        when(systemSettingsService.getStandardMailKonto()).thenReturn(mailKonto(ServerSetupTest.SMTPS.getPort()));
        // Das Postfach für Rechnungen und Geschäftsdokumente ist für die Code-Mail tabu: Auf
        // Port 1 lauscht nichts – nähme der Versand dieses Konto, scheiterte er.
        when(systemSettingsService.getDokumentMailKonto()).thenReturn(new SystemSettingsService.MailKonto(
                "127.0.0.1", 1, "rechnungen@example.com", "pw-rechnungen", "rechnungen@example.com", "Musterbetrieb"));
        FirmeninformationDto firma = new FirmeninformationDto();
        firma.setTelefon("09369 / 23 23");
        when(firmeninformationService.getFirmeninformation()).thenReturn(firma);
        when(emailSignatureService.appendSystemSignatureIfConfigured(anyString()))
                .thenAnswer(inv -> inv.getArgument(0) + "<p>-- Musterbetrieb, Musterstraße 1</p>");

        // Der Push bei der Online-Sperre läuft sonst im Hintergrund; hier gleich ausführen.
        org.mockito.Mockito.doAnswer(inv -> { inv.<Runnable>getArgument(0).run(); return null; })
                .when(taskExecutor).execute(any(Runnable.class));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FreigabeInternalController(freigabeService, codeService, dateiSpeicherService))
                .build();

        uuid = UUID.randomUUID().toString();
        DokumentFreigabe f = new DokumentFreigabe();
        f.setUuid(uuid);
        // Quelldokument existiert nicht → kein Auto-AB-Pfad, es geht nur um Code und Annahme.
        f.setQuellTyp(FreigabeQuellTyp.AUSGANGS_DOKUMENT);
        f.setQuellDokumentId(999_999L);
        f.setDokumentNummer("AN-2026-0001");
        f.setDokumentArt("Angebot");
        f.setKundeName("Max Mustermann");
        f.setKundeEmail(KUNDE);
        f.setAblaufDatum(LocalDateTime.now().plusDays(14));
        f.setHashOriginal("a".repeat(64));
        repository.save(f);
    }

    @AfterEach
    void aufraeumen()
    {
        repository.deleteAll();
    }

    private static SystemSettingsService.MailKonto mailKonto(int port)
    {
        return new SystemSettingsService.MailKonto("127.0.0.1", port, ABSENDER, "pw-hauptpostfach", ABSENDER, "Musterbetrieb");
    }

    private DokumentFreigabe ausDb()
    {
        return new TransactionTemplate(transactionManager)
                .execute(s -> repository.findByUuid(uuid).orElseThrow());
    }

    private MvcResult fordereCodeAn() throws Exception
    {
        return mockMvc.perform(post("/api/internal/freigabe/{uuid}/code", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientIp\":\"192.0.2.1\",\"userAgent\":\"Test\"}"))
                .andReturn();
    }

    /** Dekodierter HTML-Teil der Mail (der Rohtext ist quoted-printable und umbrochen). */
    private static String htmlAus(jakarta.mail.Part teil) throws Exception
    {
        Object inhalt = teil.getContent();
        if (inhalt instanceof jakarta.mail.Multipart multipart)
        {
            StringBuilder html = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++)
            {
                html.append(htmlAus(multipart.getBodyPart(i)));
            }
            return html.toString();
        }
        return teil.isMimeType("text/html") ? inhalt.toString() : "";
    }

    private String codeAusLetzterMail() throws Exception
    {
        MimeMessage[] mails = MAILSERVER.getReceivedMessages();
        assertThat(mails).isNotEmpty();
        MimeMessage letzte = mails[mails.length - 1];
        Matcher m = CODE_IN_MAIL.matcher(GreenMailUtil.getBody(letzte));
        assertThat(m.find()).as("Code steht in der Mail").isTrue();
        return m.group(1) + m.group(2);
    }

    private org.springframework.test.web.servlet.ResultActions akzeptiere(String code, String vorname) throws Exception
    {
        String body = """
                {"vorname":"%s","nachname":"Mustermann","unterzeichnerName":"%s Mustermann",
                 "bestaetigung":true,"clientIp":"192.0.2.1","userAgent":"Test","code":"%s"}
                """.formatted(vorname, vorname, code);
        return mockMvc.perform(post("/api/internal/freigabe/{uuid}/akzeptieren", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void codeKommtPerMailUndNurDieserCodeNimmtAn() throws Exception
    {
        MvcResult antwort = fordereCodeAn();
        assertThat(antwort.getResponse().getStatus()).isEqualTo(200);
        assertThat(antwort.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"emailMaskiert\":\"m•••@example.de\"")
                .contains("\"erneutAnfordernInSekunden\":30");

        MimeMessage[] mails = MAILSERVER.getReceivedMessages();
        assertThat(mails).hasSize(1);
        assertThat(mails[0].getAllRecipients()[0].toString()).isEqualTo(KUNDE);
        assertThat(mails[0].getFrom()[0].toString()).contains(ABSENDER);
        assertThat(mails[0].getSubject()).isEqualTo("Ihr Bestätigungscode für Angebot AN-2026-0001");
        String code = codeAusLetzterMail();
        assertThat(htmlAus(mails[0])).contains("09369 / 23 23").contains("Musterbetrieb, Musterstraße 1");

        DokumentFreigabe gespeichert = ausDb();
        assertThat(gespeichert.getCodeHash()).hasSize(64).doesNotContain(code);
        assertThat(gespeichert.getCodeFensterAnzahl()).isEqualTo(1);

        // Falscher Code: 422, und der Fehlversuch ist trotz Ausnahme gespeichert.
        String falsch = code.equals("000000") ? "111111" : "000000";
        akzeptiere(falsch, "Max")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeFehler").value("FALSCH"))
                .andExpect(jsonPath("$.verbleibendeVersuche").value(4));
        assertThat(ausDb().getCodeFehlversuche()).isEqualTo(1);
        assertThat(ausDb().getStatus()).isEqualTo(FreigabeStatus.PENDING);

        // Richtiger Code: angenommen, Code verbraucht, Beleg gespeichert.
        akzeptiere(code, "Max")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unterzeichnerName").value("Max Mustermann"));
        DokumentFreigabe angenommen = ausDb();
        assertThat(angenommen.getStatus()).isEqualTo(FreigabeStatus.ACCEPTED);
        assertThat(angenommen.getCodeHash()).isNull();
        assertThat(angenommen.getCodeBestaetigtAm()).isNotNull();
        assertThat(angenommen.getCodeBestaetigtEmail()).isEqualTo(KUNDE);
        assertThat(angenommen.getHashAcceptance()).isNotBlank();

        // Doppelklick: dieselbe Annahme kommt zurück, nichts wird überschrieben.
        akzeptiere(code, "Erika")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unterzeichnerName").value("Max Mustermann"));

        // Nach der Annahme gibt es keinen neuen Code mehr.
        uhr.vor(Duration.ofMinutes(1));
        assertThat(fordereCodeAn().getResponse().getStatus()).isEqualTo(410);
    }

    @Test
    void ohneAngefordertenCode_nimmtNichtsAn() throws Exception
    {
        akzeptiere("123456", "Max")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeFehler").value("NICHT_ANGEFORDERT"));
        assertThat(ausDb().getStatus()).isEqualTo(FreigabeStatus.PENDING);
    }

    @Test
    void wartezeitUndNeuerCodeMachtAltenUngueltig() throws Exception
    {
        fordereCodeAn();
        String alterCode = codeAusLetzterMail();

        MvcResult zuFrueh = fordereCodeAn();
        assertThat(zuFrueh.getResponse().getStatus()).isEqualTo(429);
        assertThat(zuFrueh.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("\"erneutAnfordernInSekunden\":30");
        assertThat(MAILSERVER.getReceivedMessages()).hasSize(1);

        uhr.vor(Duration.ofSeconds(31));
        assertThat(fordereCodeAn().getResponse().getStatus()).isEqualTo(200);
        assertThat(MAILSERVER.getReceivedMessages()).hasSize(2);
        String neuerCode = codeAusLetzterMail();

        if (!alterCode.equals(neuerCode))
        {
            akzeptiere(alterCode, "Max").andExpect(status().isUnprocessableEntity());
        }
        akzeptiere(neuerCode, "Max").andExpect(status().isOk());
    }

    @Test
    void hoechstensFuenfCodesProStunde() throws Exception
    {
        for (int i = 0; i < FreigabeCodeService.MAX_CODES_PRO_FENSTER; i++)
        {
            assertThat(fordereCodeAn().getResponse().getStatus()).isEqualTo(200);
            uhr.vor(Duration.ofSeconds(31));
        }
        MvcResult gesperrt = fordereCodeAn();
        assertThat(gesperrt.getResponse().getStatus()).isEqualTo(429);
        assertThat(MAILSERVER.getReceivedMessages()).hasSize(FreigabeCodeService.MAX_CODES_PRO_FENSTER);

        uhr.vor(Duration.ofHours(1));
        assertThat(fordereCodeAn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void abgelaufenerCode_wirdVerworfenUndDasBleibtGespeichert() throws Exception
    {
        fordereCodeAn();
        String code = codeAusLetzterMail();

        uhr.vor(Duration.ofMinutes(16));
        akzeptiere(code, "Max")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeFehler").value("ABGELAUFEN"));
        assertThat(ausDb().getCodeHash()).isNull();
        assertThat(ausDb().getStatus()).isEqualTo(FreigabeStatus.PENDING);
    }

    private static String falscherCodeZu(String code)
    {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void fuenfFehlversuche_schickenAutomatischNeuenCodeUndNurDerGilt() throws Exception
    {
        fordereCodeAn();
        String ersterCode = codeAusLetzterMail();
        uhr.vor(Duration.ofSeconds(31));

        for (int i = 1; i < FreigabeCodeService.MAX_FEHLVERSUCHE; i++)
        {
            akzeptiere(falscherCodeZu(ersterCode), "Max").andExpect(jsonPath("$.codeFehler").value("FALSCH"));
        }
        akzeptiere(falscherCodeZu(ersterCode), "Max")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeFehler").value("NEUER_CODE_VERSCHICKT"))
                .andExpect(jsonPath("$.emailMaskiert").value("m•••@example.de"))
                .andExpect(jsonPath("$.erneutAnfordernInSekunden").value(30));
        assertThat(MAILSERVER.getReceivedMessages()).hasSize(2);
        String zweiterCode = codeAusLetzterMail();
        assertThat(ausDb().getCodeFehlversucheGesamt()).isEqualTo(5);

        if (!zweiterCode.equals(ersterCode))
        {
            akzeptiere(ersterCode, "Max").andExpect(jsonPath("$.codeFehler").value("FALSCH"));
        }
        akzeptiere(zweiterCode, "Max").andExpect(status().isOk());
        assertThat(ausDb().getStatus()).isEqualTo(FreigabeStatus.ACCEPTED);
    }

    @Test
    void fuenfFehlversucheInnerhalbDerWartezeit_sperrenBisZumNeuenCode() throws Exception
    {
        fordereCodeAn();
        String code = codeAusLetzterMail();

        for (int i = 1; i < FreigabeCodeService.MAX_FEHLVERSUCHE; i++)
        {
            akzeptiere(falscherCodeZu(code), "Max").andExpect(jsonPath("$.codeFehler").value("FALSCH"));
        }
        akzeptiere(falscherCodeZu(code), "Max")
                .andExpect(jsonPath("$.codeFehler").value("GESPERRT"))
                .andExpect(jsonPath("$.erneutAnfordernInSekunden").value(30));
        assertThat(ausDb().getCodeHash()).isNull();
        assertThat(MAILSERVER.getReceivedMessages()).hasSize(1);

        akzeptiere(code, "Max").andExpect(jsonPath("$.codeFehler").value("NICHT_ANGEFORDERT"));
        uhr.vor(Duration.ofSeconds(31));
        assertThat(fordereCodeAn().getResponse().getStatus()).isEqualTo(200);
        akzeptiere(codeAusLetzterMail(), "Max").andExpect(status().isOk());
    }

    @Test
    void fuenfzehnFehlversuche_sperrenDieOnlineFreigabe() throws Exception
    {
        fordereCodeAn();
        for (int runde = 1; runde <= 3; runde++)
        {
            String code = codeAusLetzterMail();
            uhr.vor(Duration.ofSeconds(31));
            for (int i = 1; i <= FreigabeCodeService.MAX_FEHLVERSUCHE; i++)
            {
                akzeptiere(falscherCodeZu(code), "Max");
            }
        }

        DokumentFreigabe f = ausDb();
        assertThat(f.getCodeFehlversucheGesamt()).isEqualTo(FreigabeCodeService.MAX_FEHLVERSUCHE_GESAMT);
        assertThat(f.getCodeHash()).isNull();
        assertThat(f.getStatus()).isEqualTo(FreigabeStatus.PENDING);
        // Erst Code, dann zwei automatische Nachsendungen – nach dem 15. Fehler keine weitere.
        assertThat(MAILSERVER.getReceivedMessages()).hasSize(3);
        verify(webPushService, times(1)).notifyFreigabeAnnahme(org.mockito.ArgumentMatchers.eq("Online-Freigabe gesperrt"),
                anyString(), anyString());

        akzeptiere("123456", "Max")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeFehler").value("ONLINE_GESPERRT"));
        uhr.vor(Duration.ofHours(2));
        assertThat(fordereCodeAn().getResponse().getStatus()).isEqualTo(423);
        assertThat(MAILSERVER.getReceivedMessages()).hasSize(3);
    }

    @Test
    void gescheiterterVersand_hinterlaesstNichts() throws Exception
    {
        // Port, auf dem kein Mailserver lauscht → Versand scheitert → alles zurückgerollt.
        when(systemSettingsService.getStandardMailKonto()).thenReturn(mailKonto(1));

        MvcResult antwort = fordereCodeAn();

        assertThat(antwort.getResponse().getStatus()).isEqualTo(502);
        DokumentFreigabe f = ausDb();
        assertThat(f.getCodeHash()).isNull();
        assertThat(f.getCodeAngefordertAm()).isNull();
        assertThat(f.getCodeFensterAnzahl()).isZero();

        // Keine Wartezeit verbraucht: sofort neu anfordern geht, sobald der Server da ist.
        when(systemSettingsService.getStandardMailKonto()).thenReturn(mailKonto(ServerSetupTest.SMTPS.getPort()));
        assertThat(fordereCodeAn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anAndererStelleGescheiterteAnnahme_verbrauchtDenCodeNicht() throws Exception
    {
        fordereCodeAn();
        String code = codeAusLetzterMail();
        String hashVorher = ausDb().getCodeHash();

        // Ein zweiter Link desselben Vorgangs wurde inzwischen angenommen: Der Code stimmt,
        // die Annahme scheitert aber NACH der Code-Prüfung (410) – die ganze Transaktion
        // rollt zurück, der Code bleibt unverbraucht.
        DokumentFreigabe andere = new DokumentFreigabe();
        andere.setUuid(UUID.randomUUID().toString());
        andere.setQuellTyp(FreigabeQuellTyp.AUSGANGS_DOKUMENT);
        andere.setQuellDokumentId(999_999L);
        andere.setDokumentNummer("AN-2026-0001");
        andere.setDokumentArt("Angebot");
        andere.setAblaufDatum(LocalDateTime.now().plusDays(14));
        andere.setHashOriginal("b".repeat(64));
        andere.setStatus(FreigabeStatus.ACCEPTED);
        repository.save(andere);

        akzeptiere(code, "Max").andExpect(status().isGone());

        DokumentFreigabe danach = ausDb();
        assertThat(danach.getCodeHash()).isEqualTo(hashVorher);
        assertThat(danach.getCodeBestaetigtAm()).isNull();
        assertThat(danach.getStatus()).isEqualTo(FreigabeStatus.PENDING);
    }

    @Test
    void zweiGleichzeitigeCodeAnforderungen_verschickenNurEinenCode() throws Exception
    {
        List<Integer> stati = gleichzeitig(2, () -> fordereCodeAn().getResponse().getStatus());

        assertThat(stati).containsExactlyInAnyOrder(200, 429);
        assertThat(MAILSERVER.getReceivedMessages()).hasSize(1);
        assertThat(ausDb().getCodeFensterAnzahl()).isEqualTo(1);
    }

    @Test
    void zweiGleichzeitigeAnnahmen_nehmenGenauEinmalAn() throws Exception
    {
        fordereCodeAn();
        String code = codeAusLetzterMail();

        List<Integer> stati = gleichzeitig(2, () -> akzeptiere(code, "Max").andReturn().getResponse().getStatus());

        // Der zweite wartet auf die Zeilensperre und bekommt dann die erste Annahme zurück.
        assertThat(stati).containsExactly(200, 200);
        DokumentFreigabe f = ausDb();
        assertThat(f.getStatus()).isEqualTo(FreigabeStatus.ACCEPTED);
        verify(webPushService, times(1)).notifyFreigabeAnnahme(anyString(), anyString(), any());
    }

    private static <T> List<T> gleichzeitig(int anzahl, Callable<T> aufgabe) throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool(anzahl);
        try
        {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> laeufe = new ArrayList<>();
            for (int i = 0; i < anzahl; i++)
            {
                laeufe.add(pool.submit(() -> {
                    start.await();
                    return aufgabe.call();
                }));
            }
            start.countDown();
            List<T> ergebnisse = new ArrayList<>();
            for (Future<T> lauf : laeufe)
            {
                ergebnisse.add(lauf.get());
            }
            return ergebnisse;
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    @Test
    void imDialogGeaenderterEmpfaenger_bekommtDenCode_spaetereWeiterleitungNicht() throws Exception
    {
        // Büro hat im Versand-Dialog den Architekten statt der vorgeschlagenen Adresse eingetragen.
        empfaengerService.uebernehmeVersandEmpfaenger(
                "<a href=\"https://www.example.de/freigabe/" + uuid + "\">Angebot ansehen</a>",
                List.of("architekt@example.de"));

        MvcResult antwort = fordereCodeAn();

        assertThat(antwort.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("a•••@example.de");
        assertThat(MAILSERVER.getReceivedMessages()).hasSize(1);
        assertThat(MAILSERVER.getReceivedMessages()[0].getAllRecipients()[0].toString()).isEqualTo("architekt@example.de");
        // Später leitet das Büro die Angebots-Mail an einen Subunternehmer weiter.
        empfaengerService.uebernehmeVersandEmpfaenger(
                "<a href=\"https://www.example.de/freigabe/" + uuid + "\">Angebot ansehen</a>",
                List.of("subunternehmer@example.de"));
        assertThat(ausDb().getKundeEmail()).isEqualTo("architekt@example.de");

        akzeptiere(codeAusLetzterMail(), "Max").andExpect(status().isOk());
        assertThat(ausDb().getCodeBestaetigtEmail()).isEqualTo("architekt@example.de");
    }
}
