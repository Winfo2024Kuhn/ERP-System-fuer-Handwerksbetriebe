package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.RandomAccessFile;
import java.util.Base64;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruef-Tests fuer die Retry-Klassifizierung des KI-Kostenkonto-Agenten.
 * Netzwerkzugriffe werden am HttpClient ersetzt; Agent-Schleife und
 * Uebernahme-Regeln laufen mit der echten Service-Implementierung.
 */
class BelegKiKostenkontoServiceTest {

    @ParameterizedTest(name = "HTTP {0} ist transient und sollte retried werden")
    @ValueSource(ints = { 429, 500, 502, 503, 504, 599 })
    void retryableStatusCodes(int status) {
        assertThat(BelegKiKostenkontoService.isRetryableStatus(status)).isTrue();
    }

    @ParameterizedTest(name = "HTTP {0} ist deterministisch und sollte NICHT retried werden")
    @ValueSource(ints = { 200, 201, 301, 400, 401, 403, 404, 422, 600, 0 })
    void nonRetryableStatusCodes(int status) {
        assertThat(BelegKiKostenkontoService.isRetryableStatus(status)).isFalse();
    }

    @Test
    void maxAttemptsIstDrei() {
        // Vertraglich festgeschrieben (User-Anforderung: max 3 Versuche).
        // Schuetzt vor versehentlichem Hochdrehen der Retry-Zahl, die jedem
        // async-Worker bis zu 3*45s Wartezeit kosten wuerde.
        assertThat(BelegKiKostenkontoService.MAX_GEMINI_ATTEMPTS).isEqualTo(3);
    }

    // =====================================================================
    // Uebernahme-Regeln (wendeErgebnisAn)
    // =====================================================================

    private KostenstelleRepository kostenstelleRepository;
    private SachkontoRepository sachkontoRepository;
    private SystemSettingsService systemSettingsService;

    private BelegKiKostenkontoService service() {
        kostenstelleRepository = Mockito.mock(KostenstelleRepository.class);
        sachkontoRepository = Mockito.mock(SachkontoRepository.class);
        systemSettingsService = Mockito.mock(SystemSettingsService.class);
        return new BelegKiKostenkontoService(
                kostenstelleRepository,
                sachkontoRepository,
                Mockito.mock(BelegRepository.class),
                systemSettingsService,
                new ObjectMapper());
    }

    @Test
    void vorhandeneBaustelleBleibtErhaltenMitHinweis() {
        BelegKiKostenkontoService svc = service();
        Beleg beleg = new Beleg();
        Kostenstelle gewaehlt = new Kostenstelle();
        gewaehlt.setId(3L);
        beleg.setKostenstelle(gewaehlt);

        svc.klassifiziereBeleg(beleg);

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo(
                "Es war schon eine Baustelle zugeordnet – die KI hat nicht nachgeschaut.");
        assertThat(beleg.getKostenstelle()).isSameAs(gewaehlt);
        assertThat(beleg.getSachkonto()).isNull();
        Mockito.verifyNoInteractions(systemSettingsService);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @ValueSource(strings = "   ")
    void fehlenderSchluesselErklaertAusbleibendenVorschlag(String key) {
        BelegKiKostenkontoService svc = service();
        Mockito.when(systemSettingsService.getGeminiApiKey()).thenReturn(key);
        Beleg beleg = new Beleg();

        svc.klassifiziereBeleg(beleg);

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo(
                "Kein KI-Schlüssel hinterlegt – das Programm kann nichts vorschlagen.");
        assertKeineZuordnung(beleg);
    }

    @Test
    void leereAntwortErhaeltEigenenHinweis() throws Exception {
        BelegKiKostenkontoService svc = service();
        mockAntwort(svc, 204, "");
        Beleg beleg = new Beleg();
        beleg.setKiKostenkontoHinweis("Veralteter Hinweis");

        svc.klassifiziereBeleg(beleg);

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo(
                "Die KI hat nicht geantwortet. Bitte von Hand wählen.");
        assertKeineZuordnung(beleg);
    }

    @Test
    void sechsRundenOhneEntscheidungErhaltenHinweis() throws Exception {
        BelegKiKostenkontoService svc = service();
        HttpClient client = mockAntwort(svc, 200, """
                {"candidates":[{"content":{"parts":[
                  {"functionCall":{"name":"liste_kostenstellen","args":{}}}
                ]}}]}
                """);
        Beleg beleg = new Beleg();
        beleg.setKiKostenkontoHinweis("Veralteter Hinweis");

        svc.klassifiziereBeleg(beleg);

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo(
                "Die KI konnte sich nicht entscheiden. Bitte von Hand wählen.");
        assertKeineZuordnung(beleg);
        Mockito.verify(client, Mockito.times(6)).send(Mockito.any(HttpRequest.class), Mockito.any());
    }

    @Test
    void unsichereZuordnungFordertPruefungMitDeutscherZahl() {
        BelegKiKostenkontoService svc = service();
        Beleg beleg = new Beleg();

        svc.wendeErgebnisAn(beleg, new BelegKiKostenkontoService.AgentErgebnis(
                3L, 7L, new BigDecimal("0.80"), "Unklar"));

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo("KI unsicher (0,80) – bitte prüfen.");
        assertKeineZuordnung(beleg);
    }

    @Test
    void fehlendeConfidenceFordertPruefungOhneTechnischenNullwert() {
        BelegKiKostenkontoService svc = service();
        Beleg beleg = new Beleg();

        svc.wendeErgebnisAn(beleg, new BelegKiKostenkontoService.AgentErgebnis(3L, 7L, null, "Unklar"));

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo("KI unsicher – bitte prüfen.");
        assertKeineZuordnung(beleg);
    }

    @Test
    void erfolgreicheAutomatischeZuordnungLoeschtAltenHinweis() {
        BelegKiKostenkontoService svc = service();
        Sachkonto konto = sachkonto(7L, "Fahrzeugkosten", true);
        Mockito.when(sachkontoRepository.findById(7L)).thenReturn(Optional.of(konto));
        Beleg beleg = new Beleg();
        beleg.setKiKostenkontoHinweis("Veralteter Hinweis");

        svc.wendeErgebnisAn(beleg, new BelegKiKostenkontoService.AgentErgebnis(
                null, 7L, new BigDecimal("0.95"), "Sicher"));

        assertThat(beleg.getKiKostenkontoHinweis()).isNull();
        assertThat(beleg.getSachkonto()).isSameAs(konto);
    }

    @Test
    void nichtUebernehmbarerVorschlagErklaertManuelleAuswahl() {
        BelegKiKostenkontoService svc = service();
        Beleg beleg = new Beleg();

        svc.wendeErgebnisAn(beleg, new BelegKiKostenkontoService.AgentErgebnis(
                3L, 7L, new BigDecimal("0.99"), "Unbekannte Konten"));

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo(
                "Die KI hat nichts automatisch zugeordnet. Bitte von Hand wählen.");
        assertKeineZuordnung(beleg);
    }

    @SuppressWarnings("unchecked")
    private HttpClient mockAntwort(BelegKiKostenkontoService svc, int status, String body) throws Exception {
        Mockito.when(systemSettingsService.getGeminiApiKey()).thenReturn("dummy-test-key");
        HttpClient client = Mockito.mock(HttpClient.class);
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        Mockito.when(response.statusCode()).thenReturn(status);
        Mockito.when(response.body()).thenReturn(body);
        Mockito.when(client.send(Mockito.any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        ReflectionTestUtils.setField(svc, "httpClient", client);
        ReflectionTestUtils.setField(svc, "geminiModel", "dummy-test-model");
        return client;
    }

    private static void assertKeineZuordnung(Beleg beleg) {
        assertThat(beleg.getKostenstelle()).isNull();
        assertThat(beleg.getSachkonto()).isNull();
    }

    @TempDir
    Path uploadDir;

    @ParameterizedTest
    @CsvSource({"muster.png,image/png", "muster.PDF,application/pdf"})
    void ersterTurnEnthaeltOriginaldateiAlsInlineData(String dateiname, String mimeType) throws Exception {
        BelegKiKostenkontoService svc = service();
        ReflectionTestUtils.setField(svc, "uploadPath", uploadDir.toString());
        Path belege = Files.createDirectories(uploadDir.resolve("belege"));
        byte[] dummyDatei = "Musterbeleg ohne Personendaten".getBytes(StandardCharsets.UTF_8);
        Files.write(belege.resolve(dateiname), dummyDatei);
        Beleg beleg = new Beleg();
        beleg.setGespeicherterDateiname(dateiname);

        ObjectNode turn = svc.buildInitialTurn(beleg);

        assertThat(turn.path("role").asText()).isEqualTo("user");
        assertThat(turn.path("parts").size()).isEqualTo(2);
        assertThat(turn.path("parts").path(0).path("text").asText()).contains("BELEG-DATEN:");
        assertThat(turn.path("parts").path(1).path("inline_data").path("mime_type").asText())
                .isEqualTo(mimeType);
        assertThat(Base64.getDecoder().decode(
                turn.path("parts").path(1).path("inline_data").path("data").asText()))
                .isEqualTo(dummyDatei);
    }

    @Test
    void fehlendeDateiFaelltAufTextZurueck() {
        BelegKiKostenkontoService svc = service();
        ReflectionTestUtils.setField(svc, "uploadPath", uploadDir.toString());
        Beleg beleg = new Beleg();
        beleg.setGespeicherterDateiname("fehlt.pdf");

        ObjectNode turn = svc.buildInitialTurn(beleg);

        assertNurText(turn);
    }

    @ParameterizedTest
    @ValueSource(longs = {8 * 1024 * 1024, 8 * 1024 * 1024 + 1})
    void dateiAbAchtMbFaelltAufTextZurueck(long bytes) throws Exception {
        BelegKiKostenkontoService svc = service();
        ReflectionTestUtils.setField(svc, "uploadPath", uploadDir.toString());
        Path belege = Files.createDirectories(uploadDir.resolve("belege"));
        try (RandomAccessFile datei = new RandomAccessFile(belege.resolve("gross.pdf").toFile(), "rw")) {
            datei.setLength(bytes);
        }
        Beleg beleg = new Beleg();
        beleg.setGespeicherterDateiname("gross.pdf");

        assertNurText(svc.buildInitialTurn(beleg));
    }

    @Test
    void dateiAusserhalbBelegordnerWirdNichtAnKiGesendet() throws Exception {
        BelegKiKostenkontoService svc = service();
        ReflectionTestUtils.setField(svc, "uploadPath", uploadDir.toString());
        Files.createDirectories(uploadDir.resolve("belege"));
        Files.writeString(uploadDir.resolve("fremd.pdf"), "Nicht fuer die KI bestimmt");
        Beleg beleg = new Beleg();
        beleg.setGespeicherterDateiname("../fremd.pdf");

        assertNurText(svc.buildInitialTurn(beleg));
    }

    @Test
    void symlinkAusserhalbBelegordnerWirdNichtAnKiGesendet() throws Exception {
        BelegKiKostenkontoService svc = service();
        ReflectionTestUtils.setField(svc, "uploadPath", uploadDir.toString());
        Path belege = Files.createDirectories(uploadDir.resolve("belege"));
        Path fremd = Files.writeString(uploadDir.resolve("fremd.pdf"), "Nicht fuer die KI bestimmt");
        Files.createSymbolicLink(belege.resolve("link.pdf"), fremd);
        Beleg beleg = new Beleg();
        beleg.setGespeicherterDateiname("link.pdf");

        assertNurText(svc.buildInitialTurn(beleg));
    }

    @Test
    void positionslisteErgaenztPromptMitHoechstensFuenfzehnZeilen() throws Exception {
        BelegKiKostenkontoService svc = service();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode extraktion = mapper.createObjectNode();
        var positionen = extraktion.putArray("positionen");
        for (int i = 1; i <= 16; i++) {
            positionen.addObject().put("menge", "1.5")
                    .put("beschreibung", "Musterartikel " + i)
                    .put("betragBrutto", "12.50");
        }
        Beleg beleg = new Beleg();
        beleg.setKiExtraktionJson(mapper.writeValueAsString(extraktion));

        String text = svc.buildInitialTurn(beleg).path("parts").path(0).path("text").asText();

        assertThat(text).contains("1,5 × Musterartikel 1 — 12,50", "1,5 × Musterartikel 15 — 12,50");
        assertThat(text).doesNotContain("Musterartikel 16");
        assertThat(text.lines().filter(line -> line.contains(" × ")).count()).isEqualTo(15);
        Mockito.verifyNoInteractions(kostenstelleRepository, sachkontoRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"kaputtes json", "{}", "null", "{\"positionen\":null}",
            "{\"positionen\":[null,42,{}]}"})
    void fehlendeOderKaputtePositionsdatenVerhindernTextPromptNicht(String json) {
        BelegKiKostenkontoService svc = service();
        Beleg beleg = new Beleg();
        beleg.setKiExtraktionJson(json);

        assertNurText(svc.buildInitialTurn(beleg));
    }

    private static void assertNurText(ObjectNode turn) {
        assertThat(turn.path("parts").size()).isEqualTo(1);
        assertThat(turn.path("parts").path(0).path("text").asText()).contains("BELEG-DATEN:");
        assertThat(turn.path("parts").path(0).has("inline_data")).isFalse();
    }

    private Sachkonto sachkonto(long id, String bezeichnung, boolean aktiv) {
        Sachkonto s = new Sachkonto();
        s.setId(id);
        s.setBezeichnung(bezeichnung);
        s.setAktiv(aktiv);
        return s;
    }

    @Test
    void sachkontoWirdAuchOhneKostenstelleUebernommen() {
        // Der Regressionsfall: eine Tankquittung, bei der die KI sehr sicher ist,
        // aber KEINE Kostenstelle liefert. Frueher fiel der Kontovorschlag dann
        // stillschweigend weg und der Buchhalter sah "kein Konto".
        BelegKiKostenkontoService svc = service();
        Sachkonto konto = sachkonto(7L, "Fahrzeugkosten", true);
        Mockito.when(sachkontoRepository.findById(7L)).thenReturn(Optional.of(konto));

        Beleg beleg = new Beleg();
        svc.wendeErgebnisAn(beleg, new BelegKiKostenkontoService.AgentErgebnis(
                null, 7L, new BigDecimal("0.97"), "Tankquittung an Esso"));

        assertThat(beleg.getSachkonto()).isSameAs(konto);
        assertThat(beleg.getKostenstelle()).isNull();
    }

    @Test
    void sachkontoWirdBeiNiedrigerConfidenceNichtUebernommen() {
        // Unter 0.95 bleibt es beim reinen Vorschlag — der Buchhalter entscheidet.
        BelegKiKostenkontoService svc = service();

        Beleg beleg = new Beleg();
        svc.wendeErgebnisAn(beleg, new BelegKiKostenkontoService.AgentErgebnis(
                null, 7L, new BigDecimal("0.80"), "wahrscheinlich Bueromaterial"));

        assertThat(beleg.getSachkonto()).isNull();
        assertThat(beleg.getKiVorgeschlagenerSachkontoId()).isEqualTo(7L);
        Mockito.verify(sachkontoRepository, Mockito.never()).findById(Mockito.anyLong());
    }

    @Test
    void deaktiviertesSachkontoWirdNichtUebernommen() {
        // Die KI darf kein Konto setzen, das in liste_sachkonten nie auftauchte.
        BelegKiKostenkontoService svc = service();
        Mockito.when(sachkontoRepository.findById(9L))
                .thenReturn(Optional.of(sachkonto(9L, "Altes Konto", false)));

        Beleg beleg = new Beleg();
        svc.wendeErgebnisAn(beleg, new BelegKiKostenkontoService.AgentErgebnis(
                null, 9L, new BigDecimal("0.99"), "sehr sicher"));

        assertThat(beleg.getSachkonto()).isNull();
    }

    @Test
    void vorschlaegeWerdenImmerProtokolliert() {
        // Auch wenn nichts automatisch uebernommen wird, muss der Vorschlag samt
        // Begruendung am Beleg haengen — genau das zeigt das UI dem Buchhalter an.
        BelegKiKostenkontoService svc = service();

        Beleg beleg = new Beleg();
        svc.wendeErgebnisAn(beleg, new BelegKiKostenkontoService.AgentErgebnis(
                3L, 7L, new BigDecimal("0.42"), "geraten"));

        assertThat(beleg.getKiVorgeschlagenerKostenstelleId()).isEqualTo(3L);
        assertThat(beleg.getKiVorgeschlagenerSachkontoId()).isEqualTo(7L);
        assertThat(beleg.getKiKostenkontoBegruendung()).isEqualTo("geraten");
        assertThat(beleg.getKiKostenkontoConfidence()).isEqualByComparingTo("0.42");
    }

    @Test
    void projektKostenstelleWirdNichtAutomatischGesetzt() {
        // Schutz des Bestellungs-Workflows: Projekt-Material darf NIE automatisch
        // in den Gemeinkosten-Topf wandern, egal wie sicher die KI ist.
        BelegKiKostenkontoService svc = service();
        Kostenstelle projekt = new Kostenstelle();
        projekt.setId(3L);
        projekt.setBezeichnung("Projekt Halle Nord");
        projekt.setAktiv(true);
        projekt.setIstFixkosten(false);
        projekt.setIstInvestition(false);
        Mockito.when(kostenstelleRepository.findById(3L)).thenReturn(Optional.of(projekt));

        Beleg beleg = new Beleg();
        svc.wendeErgebnisAn(beleg, new BelegKiKostenkontoService.AgentErgebnis(
                3L, null, new BigDecimal("0.99"), "Stahlprofile"));

        assertThat(beleg.getKostenstelle()).isNull();
    }

    @Test
    void confidenceWirdAufEinsGeclamped() {
        // Halluzinierte Werte wie 1.5 wuerden die DECIMAL(3,2)-Spalte sprengen.
        BelegKiKostenkontoService svc = service();
        Mockito.when(sachkontoRepository.findById(7L))
                .thenReturn(Optional.of(sachkonto(7L, "Fahrzeugkosten", true)));

        Beleg beleg = new Beleg();
        svc.wendeErgebnisAn(beleg, new BelegKiKostenkontoService.AgentErgebnis(
                null, 7L, new BigDecimal("1.5"), "uebersicher"));

        assertThat(beleg.getKiKostenkontoConfidence()).isEqualByComparingTo("1.00");
    }
}
