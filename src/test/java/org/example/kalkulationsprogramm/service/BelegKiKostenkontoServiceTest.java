package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiErgebnis;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiNachricht;
import org.example.kalkulationsprogramm.service.ki.KiNichtVerfuegbar;
import org.example.kalkulationsprogramm.service.ki.KiRohZug;
import org.example.kalkulationsprogramm.service.ki.KiTeil;
import org.example.kalkulationsprogramm.service.ki.KiWerkzeug;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.RandomAccessFile;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruef-Tests fuer den KI-Kostenkonto-Agenten. Die KI selbst ist gemockt
 * ({@link KiDienst}); Agent-Schleife und Uebernahme-Regeln laufen mit der
 * echten Service-Implementierung. Die Uebersetzung der Werkzeuge pruefen die
 * Anbieter-Tests.
 */
class BelegKiKostenkontoServiceTest {

    @Test
    void maxAttemptsIstDrei() {
        // Vertraglich festgeschrieben (User-Anforderung: max 3 Versuche).
        // Schuetzt vor versehentlichem Hochdrehen der Retry-Zahl, die jedem
        // async-Worker bis zu 3*45s Wartezeit kosten wuerde.
        assertThat(BelegKiKostenkontoService.MAX_KI_VERSUCHE).isEqualTo(3);
    }

    // =====================================================================
    // Uebernahme-Regeln (wendeErgebnisAn)
    // =====================================================================

    private KostenstelleRepository kostenstelleRepository;
    private SachkontoRepository sachkontoRepository;
    private KiDienst kiDienst;

    private BelegKiKostenkontoService service() {
        kostenstelleRepository = Mockito.mock(KostenstelleRepository.class);
        sachkontoRepository = Mockito.mock(SachkontoRepository.class);
        kiDienst = Mockito.mock(KiDienst.class);
        BelegKiKostenkontoService svc = new BelegKiKostenkontoService(
                kostenstelleRepository,
                sachkontoRepository,
                Mockito.mock(BelegRepository.class),
                kiDienst,
                JsonMapper.builderWithJackson2Defaults().build());
        ReflectionTestUtils.setField(svc, "wiederholungsPausenMs", new long[] {0L, 0L});
        return svc;
    }

    private final ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();

    /** Die KI antwortet der Reihe nach (die letzte Antwort wiederholt sich): Ergebnisse oder Ausnahmen. */
    private void kiAntwortet(Object... antworten) {
        Mockito.when(kiDienst.verfuegbar(KiFunktion.KOSTENKONTO_VORSCHLAG)).thenReturn(true);
        Deque<Object> reihe = new ArrayDeque<>(List.of(antworten));
        Mockito.when(kiDienst.sende(Mockito.eq(KiFunktion.KOSTENKONTO_VORSCHLAG), Mockito.any(KiAnfrage.class)))
                .thenAnswer(aufruf -> {
                    Object naechste = reihe.size() > 1 ? reihe.poll() : reihe.peek();
                    if (naechste instanceof RuntimeException fehler) throw fehler;
                    return naechste;
                });
    }

    private List<KiAnfrage> gesendeteAnfragen(int anzahl) {
        ArgumentCaptor<KiAnfrage> anfragen = ArgumentCaptor.forClass(KiAnfrage.class);
        Mockito.verify(kiDienst, Mockito.times(anzahl))
                .sende(Mockito.eq(KiFunktion.KOSTENKONTO_VORSCHLAG), anfragen.capture());
        return anfragen.getAllValues();
    }

    private KiTeil.WerkzeugAufruf aufruf(String id, String name, String args) throws Exception {
        return new KiTeil.WerkzeugAufruf(id, name, mapper.readTree(args));
    }

    private KiErgebnis aufrufe(KiTeil.WerkzeugAufruf... aufrufe) {
        ObjectNode roh = mapper.createObjectNode().put("role", "model");
        roh.putArray("parts").addObject().put("thoughtSignature", "sig");
        return new KiErgebnis("", false, KiAnbieterTyp.GEMINI, "test-modell", List.of(aufrufe), false,
                List.of(), List.of(), new KiRohZug(KiAnbieterTyp.GEMINI, roh));
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
        Mockito.verifyNoInteractions(kiDienst);
    }

    @Test
    void ohnePassendeKiErklaertAusbleibendenVorschlag() {
        BelegKiKostenkontoService svc = service();
        Mockito.when(kiDienst.verfuegbar(KiFunktion.KOSTENKONTO_VORSCHLAG)).thenReturn(false);
        Beleg beleg = new Beleg();

        svc.klassifiziereBeleg(beleg);

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo(
                "Keine passende KI eingerichtet – das Programm kann nichts vorschlagen.");
        assertKeineZuordnung(beleg);
        Mockito.verify(kiDienst, Mockito.never()).sende(Mockito.any(), Mockito.any(KiAnfrage.class));
    }

    @Test
    void dauerhafterFehlerWirdNichtWiederholtUndErhaeltEigenenHinweis() {
        BelegKiKostenkontoService svc = service();
        kiAntwortet(new KiAnbieterFehler("Mistral nimmt den Schlüssel nicht an (HTTP 401).", 401, false));
        Beleg beleg = new Beleg();
        beleg.setKiKostenkontoHinweis("Veralteter Hinweis");

        svc.klassifiziereBeleg(beleg);

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo(
                "Die KI hat nicht geantwortet. Bitte von Hand wählen.");
        assertKeineZuordnung(beleg);
        gesendeteAnfragen(1);
    }

    @Test
    void voruebergehendeFehlerWerdenBisZuDreimalVersucht() throws Exception {
        BelegKiKostenkontoService svc = service();
        KiAnbieterFehler ueberlastet = new KiAnbieterFehler("Ausgelastet (HTTP 429).", 429, true);
        kiAntwortet(ueberlastet, new KiAnbieterFehler("Nicht erreichbar.", true, new java.io.IOException("weg")),
                aufrufe(aufruf("a1", BelegKiKostenkontoService.FINALE_ZUORDNUNG,
                        "{\"confidence\":0.3,\"begruendung\":\"Unklar\"}")));
        Beleg beleg = new Beleg();

        svc.klassifiziereBeleg(beleg);

        gesendeteAnfragen(3);
        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo("KI unsicher (0,30) – bitte prüfen.");
    }

    @Test
    void dreiVoruebergehendeFehlerGebenAuf() {
        BelegKiKostenkontoService svc = service();
        kiAntwortet(new KiAnbieterFehler("Störung (HTTP 503).", 503, true));
        Beleg beleg = new Beleg();

        svc.klassifiziereBeleg(beleg);

        gesendeteAnfragen(BelegKiKostenkontoService.MAX_KI_VERSUCHE);
        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo("Die KI hat nicht geantwortet. Bitte von Hand wählen.");
    }

    @Test
    void wirdDieFunktionMittendrinAusgeschaltetKeinVorschlag() {
        BelegKiKostenkontoService svc = service();
        kiAntwortet(new KiNichtVerfuegbar(KiFunktion.KOSTENKONTO_VORSCHLAG, "aus"));
        Beleg beleg = new Beleg();

        svc.klassifiziereBeleg(beleg);

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo("Die KI hat nicht geantwortet. Bitte von Hand wählen.");
        gesendeteAnfragen(1);
    }

    @Test
    void sechsRundenOhneEntscheidungErhaltenHinweis() throws Exception {
        BelegKiKostenkontoService svc = service();
        kiAntwortet(aufrufe(aufruf("", "liste_kostenstellen", "{}")));
        Beleg beleg = new Beleg();
        beleg.setKiKostenkontoHinweis("Veralteter Hinweis");

        svc.klassifiziereBeleg(beleg);

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo(
                "Die KI konnte sich nicht entscheiden. Bitte von Hand wählen.");
        assertKeineZuordnung(beleg);
        List<KiAnfrage> anfragen = gesendeteAnfragen(6);
        assertThat(anfragen).allSatisfy(anfrage -> {
            assertThat(anfrage.werkzeuge()).extracting(KiWerkzeug::name).containsExactly("liste_kostenstellen",
                    "liste_sachkonten", "aehnliche_belege", BelegKiKostenkontoService.FINALE_ZUORDNUNG);
            assertThat(anfrage.werkzeugModus()).isEqualTo(KiAnfrage.WerkzeugModus.AUTO);
            assertThat(anfrage.websuche()).isFalse();
            assertThat(anfrage.zeitlimit()).isEqualTo(Duration.ofSeconds(45));
            assertThat(anfrage.systemAnweisung()).contains("autonomer Buchhaltungs-Agent");
        });
        // Jede Runde haengt Zug der KI und Werkzeug-Ergebnis an
        assertThat(anfragen.get(5).nachrichten()).hasSize(11);
        KiWerkzeug finale = anfragen.get(0).werkzeuge().get(3);
        assertThat(finale.parameter().path("required").toString()).contains("confidence", "begruendung");
    }

    @Test
    void freierTextOhneWerkzeugWirdAlsErgebnisGelesen() {
        BelegKiKostenkontoService svc = service();
        kiAntwortet(new KiErgebnis("```json\n{\"kostenstelleId\":null,\"confidence\":0.2,\"begruendung\":\"Material\"}\n```",
                false, KiAnbieterTyp.MISTRAL, "m"));
        Beleg beleg = new Beleg();

        svc.klassifiziereBeleg(beleg);

        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo("KI unsicher (0,20) – bitte prüfen.");
        assertThat(beleg.getKiKostenkontoBegruendung()).isEqualTo("Material");
    }

    @Test
    void unbekanntesWerkzeugBrichtDenLaufAb() throws Exception {
        BelegKiKostenkontoService svc = service();
        kiAntwortet(aufrufe(aufruf("x", "rm_rf", "{}")));
        Beleg beleg = new Beleg();

        svc.klassifiziereBeleg(beleg);

        gesendeteAnfragen(1);
        assertThat(beleg.getKiKostenkontoHinweis()).isEqualTo("Die KI konnte sich nicht entscheiden. Bitte von Hand wählen.");
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

        KiNachricht nachricht = svc.ersteNachricht(beleg);

        assertThat(nachricht.rolle()).isEqualTo(KiNachricht.Rolle.NUTZER);
        assertThat(nachricht.teile()).hasSize(2);
        assertThat(nachricht.text()).contains("BELEG-DATEN:");
        assertThat(nachricht.teile().get(1)).isInstanceOfSatisfying(KiTeil.Datei.class, datei -> {
            assertThat(datei.mimeType()).isEqualTo(mimeType);
            assertThat(datei.daten()).isEqualTo(dummyDatei);
        });
    }

    @Test
    void fehlendeDateiFaelltAufTextZurueck() {
        BelegKiKostenkontoService svc = service();
        ReflectionTestUtils.setField(svc, "uploadPath", uploadDir.toString());
        Beleg beleg = new Beleg();
        beleg.setGespeicherterDateiname("fehlt.pdf");

        assertNurText(svc.ersteNachricht(beleg));
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

        assertNurText(svc.ersteNachricht(beleg));
    }

    @Test
    void dateiAusserhalbBelegordnerWirdNichtAnKiGesendet() throws Exception {
        BelegKiKostenkontoService svc = service();
        ReflectionTestUtils.setField(svc, "uploadPath", uploadDir.toString());
        Files.createDirectories(uploadDir.resolve("belege"));
        Files.writeString(uploadDir.resolve("fremd.pdf"), "Nicht fuer die KI bestimmt");
        Beleg beleg = new Beleg();
        beleg.setGespeicherterDateiname("../fremd.pdf");

        assertNurText(svc.ersteNachricht(beleg));
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

        assertNurText(svc.ersteNachricht(beleg));
    }

    @Test
    void positionslisteErgaenztPromptMitHoechstensFuenfzehnZeilen() throws Exception {
        BelegKiKostenkontoService svc = service();
        ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();
        ObjectNode extraktion = mapper.createObjectNode();
        var positionen = extraktion.putArray("positionen");
        for (int i = 1; i <= 16; i++) {
            positionen.addObject().put("menge", "1.5")
                    .put("beschreibung", "Musterartikel " + i)
                    .put("betragBrutto", "12.50");
        }
        Beleg beleg = new Beleg();
        beleg.setKiExtraktionJson(mapper.writeValueAsString(extraktion));

        String text = svc.ersteNachricht(beleg).text();

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

        assertNurText(svc.ersteNachricht(beleg));
    }

    private static void assertNurText(KiNachricht nachricht) {
        assertThat(nachricht.teile()).hasSize(1);
        assertThat(nachricht.text()).contains("BELEG-DATEN:");
        assertThat(nachricht.teile().get(0)).isInstanceOf(KiTeil.Text.class);
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

    // ── Parallele Werkzeug-Aufrufe ──

    @Test
    void mehrereGleichzeitigeAufrufeWerdenAlleInEinemZugBeantwortet() throws Exception {
        BelegKiKostenkontoService svc = service();
        kiAntwortet(aufrufe(aufruf("a1", "liste_kostenstellen", "{}"), aufruf("a2", "liste_sachkonten", "{}")),
                aufrufe(aufruf("a3", BelegKiKostenkontoService.FINALE_ZUORDNUNG,
                        "{\"confidence\":0.3,\"begruendung\":\"Unklar\"}")));

        svc.klassifiziereBeleg(new Beleg());

        List<KiNachricht> verlauf = gesendeteAnfragen(2).get(1).nachrichten();
        assertThat(verlauf).hasSize(3);
        // Der Zug der KI geht samt Rohform (Gedanken-Signatur) zurück
        assertThat(verlauf.get(1).rolle()).isEqualTo(KiNachricht.Rolle.KI);
        assertThat(verlauf.get(1).roh().inhalt().toString()).contains("sig");
        assertThat(verlauf.get(2).rolle()).isEqualTo(KiNachricht.Rolle.WERKZEUG);
        List<KiTeil> antworten = verlauf.get(2).teile();
        assertThat(antworten).hasSize(2);
        KiTeil.WerkzeugErgebnis erste = (KiTeil.WerkzeugErgebnis) antworten.get(0);
        assertThat(erste.id()).isEqualTo("a1");
        assertThat(erste.ergebnis().path("name").asString("")).isEqualTo("liste_kostenstellen");
        assertThat(erste.ergebnis().path("content").isArray()).isTrue();
        KiTeil.WerkzeugErgebnis zweite = (KiTeil.WerkzeugErgebnis) antworten.get(1);
        assertThat(zweite.id()).isEqualTo("a2");
        assertThat(zweite.ergebnis().path("name").asString("")).isEqualTo("liste_sachkonten");
        Mockito.verify(kostenstelleRepository).findByAktivTrueOrderBySortierungAsc();
        Mockito.verify(sachkontoRepository).findByAktivTrueOrderBySortierungAscBezeichnungAsc();
    }

    @Test
    void finaleZuordnungNebenAnderenAufrufenBeendetDenLauf() throws Exception {
        BelegKiKostenkontoService svc = service();
        kiAntwortet(aufrufe(aufruf("a1", "liste_kostenstellen", "{}"),
                aufruf("a2", BelegKiKostenkontoService.FINALE_ZUORDNUNG,
                        "{\"confidence\":0.3,\"begruendung\":\"Unklar\"}")));
        Beleg beleg = new Beleg();

        svc.klassifiziereBeleg(beleg);

        gesendeteAnfragen(1);
        Mockito.verify(kostenstelleRepository, Mockito.never()).findByAktivTrueOrderBySortierungAsc();
        assertThat(beleg.getKiKostenkontoHinweis()).isNotEqualTo("Die KI konnte sich nicht entscheiden. Bitte von Hand wählen.");
        assertKeineZuordnung(beleg);
    }
}
