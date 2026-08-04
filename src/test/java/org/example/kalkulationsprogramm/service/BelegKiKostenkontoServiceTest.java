package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruef-Tests fuer die Retry-Klassifizierung des KI-Kostenkonto-Agenten.
 * Echte HTTP-Calls werden hier bewusst nicht gemockt — die Verkabelung mit
 * Spring-HttpClient deckt der Smoke-Test in BelegKiAnalyseServiceTest ab.
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

    private BelegKiKostenkontoService service() {
        kostenstelleRepository = Mockito.mock(KostenstelleRepository.class);
        sachkontoRepository = Mockito.mock(SachkontoRepository.class);
        return new BelegKiKostenkontoService(
                kostenstelleRepository,
                sachkontoRepository,
                Mockito.mock(BelegRepository.class),
                Mockito.mock(SystemSettingsService.class),
                new ObjectMapper());
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
