package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueft die generierte Verfahrensdokumentation: die beiden neuen
 * Kassen-Abschnitte muessen enthalten sein (TSE-Abgrenzung,
 * Steuerberater-Monatspaket), und ohne gepflegte Firmendaten darf keine
 * Exception fliegen, sondern ein Fallback-Text erscheinen.
 */
class VerfahrensdokumentationServiceTest {

    private FirmeninformationRepository repository;
    private VerfahrensdokumentationService service;

    @BeforeEach
    void setUp() {
        repository = mock(FirmeninformationRepository.class);
        service = new VerfahrensdokumentationService(repository);
    }

    @Test
    void enthaeltUeberschriftenDerBeidenNeuenAbschnitte() {
        when(repository.findFirmeninformation()).thenReturn(Optional.of(musterbetrieb()));

        String text = service.erzeugeText();

        assertTrue(text.contains("WARUM DIESE KASSE KEINE REGISTRIERKASSE IST"),
                "Abschnitt 'Warum diese Kasse keine Registrierkasse ist' fehlt");
        assertTrue(text.contains("WAS DER STEUERBERATER MONATLICH BEKOMMT"),
                "Abschnitt 'Was der Steuerberater monatlich bekommt' fehlt");
    }

    @Test
    void enthaeltSatzZurFehlendenTsePflicht() {
        when(repository.findFirmeninformation()).thenReturn(Optional.of(musterbetrieb()));

        String text = service.erzeugeText();

        assertTrue(text.contains("keine TSE, keine Kassenmeldung, keine Belegausgabepflicht"),
                "Der Satz zur fehlenden TSE-Pflicht fehlt");
        assertTrue(text.contains("§ 146a AO"), "Verweis auf § 146a AO fehlt");
    }

    @Test
    void enthaeltQuittungUndErsatzbeleg() {
        when(repository.findFirmeninformation()).thenReturn(Optional.of(musterbetrieb()));

        String text = service.erzeugeText();

        assertTrue(text.contains("Quittung"), "Begriff 'Quittung' fehlt");
        assertTrue(text.contains("Ersatzbeleg"), "Begriff 'Ersatzbeleg' fehlt");
    }

    @Test
    void ohneFirmendatenStehtFallbackStattException() {
        when(repository.findFirmeninformation()).thenReturn(Optional.empty());

        String text = assertDoesNotThrow(() -> service.erzeugeText());

        assertTrue(text.contains("(Firmenname nicht gepflegt)"),
                "Fallback-Text fuer fehlenden Firmennamen fehlt");
    }

    @Test
    void textIstLaengerAls3000Zeichen() {
        when(repository.findFirmeninformation()).thenReturn(Optional.of(musterbetrieb()));

        String text = service.erzeugeText();

        assertTrue(text.length() > 3000,
                "Text hat nur " + text.length() + " Zeichen - fehlt ein Abschnitt?");
    }

    private Firmeninformation musterbetrieb() {
        Firmeninformation f = new Firmeninformation();
        f.setFirmenname("Musterbetrieb GmbH");
        f.setSteuernummer("12/345/67890");
        f.setStrasse("Musterstraße 1");
        f.setPlz("12345");
        f.setOrt("Musterstadt");
        return f;
    }
}
