package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests fuer die Zuordnung einer E-Mail-Adresse zu einem Lieferanten.
 *
 * <p>Fachlich: Bei einer eigenen Firmen-Domain gehoert jede Adresse dahinter zum
 * selben Partner – die Adresse muss also nicht beim Lieferanten gespeichert sein.
 * Bei Freemail-Adressen (gmx.de, t-online.de) sagt die Domain dagegen nichts aus;
 * dort zaehlt nur die exakt hinterlegte Adresse.
 *
 * <p>Alle Daten sind Dummy-Daten (DSGVO).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LieferantEmailResolver")
class LieferantEmailResolverTest {

    @Mock
    private LieferantenRepository lieferantenRepository;

    private LieferantEmailResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LieferantEmailResolver(lieferantenRepository);
    }

    private static Lieferanten lieferant(long id, String name, String... emails) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(id);
        lieferant.setLieferantenname(name);
        lieferant.setKundenEmails(new java.util.ArrayList<>(List.of(emails)));
        return lieferant;
    }

    private void mitLieferanten(Lieferanten... lieferanten) {
        when(lieferantenRepository.findAllWithEmails()).thenReturn(List.of(lieferanten));
        resolver.refresh();
    }

    @Test
    @DisplayName("Exakt hinterlegte Adresse trifft")
    void exakteAdresse_trifft() {
        mitLieferanten(lieferant(1L, "Muster Stahl GmbH", "info@musterstahl.example"));

        assertThat(resolver.resolve(List.of("info@musterstahl.example"))).contains(1L);
    }

    @Test
    @DisplayName("Andere Adresse derselben Firmen-Domain trifft ebenfalls")
    void andereAdresseGleicherFirmenDomain_trifft() {
        mitLieferanten(lieferant(1L, "Muster Stahl GmbH", "info@musterstahl.example"));

        // Adresse ist NICHT beim Lieferanten gespeichert – die Firmen-Domain reicht
        assertThat(resolver.resolve(List.of("einkauf@musterstahl.example"))).contains(1L);
    }

    @Test
    @DisplayName("Freemail-Domain zieht keine fremde Adresse mit")
    void freemailDomain_ziehtNichts() {
        mitLieferanten(lieferant(1L, "Muster Schlosserei", "muster.schlosserei@t-online.de"));

        // Die hinterlegte Adresse selbst trifft weiterhin
        assertThat(resolver.resolve(List.of("muster.schlosserei@t-online.de"))).contains(1L);
        // Eine fremde t-online-Adresse (z.B. ein Kunde) aber nicht
        assertThat(resolver.resolve(List.of("max.mustermann@t-online.de"))).isEmpty();
    }

    @Test
    @DisplayName("Mehrdeutige Firmen-Domain wird nicht zugeordnet")
    void mehrdeutigeDomain_wirdNichtZugeordnet() {
        mitLieferanten(
                lieferant(1L, "Muster Stahl GmbH", "info@gemeinsam.example"),
                lieferant(2L, "Muster Holz GmbH", "kontakt@gemeinsam.example"));

        assertThat(resolver.resolve(List.of("unbekannt@gemeinsam.example"))).isEmpty();
        // Die exakten Adressen bleiben eindeutig
        assertThat(resolver.resolve(List.of("info@gemeinsam.example"))).contains(1L);
    }

    @Test
    @DisplayName("Leere, unbekannte und kaputte Eingaben liefern nichts")
    void ungueltigeEingaben_liefernNichts() {
        mitLieferanten(lieferant(1L, "Muster Stahl GmbH", "info@musterstahl.example"));

        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve(List.of())).isEmpty();
        assertThat(resolver.resolve(List.of("ohne-at-zeichen"))).isEmpty();
        assertThat(resolver.resolve(List.of("   "))).isEmpty();
        assertThat(resolver.resolve(List.of("fremd@andere.example"))).isEmpty();
    }

    @Test
    @DisplayName("Gross-/Kleinschreibung und Leerzeichen sind egal")
    void schreibweise_istEgal() {
        mitLieferanten(lieferant(1L, "Muster Stahl GmbH", "info@musterstahl.example"));

        assertThat(resolver.resolve(List.of("  INFO@MusterStahl.Example  "))).contains(1L);
    }

    @Test
    @DisplayName("istFreemailDomain erkennt die gaengigen Anbieter")
    void istFreemailDomain_erkenntAnbieter() {
        assertThat(LieferantEmailResolver.istFreemailDomain("t-online.de")).isTrue();
        assertThat(LieferantEmailResolver.istFreemailDomain("GMX.DE")).isTrue();
        assertThat(LieferantEmailResolver.istFreemailDomain("gmail.com")).isTrue();
        assertThat(LieferantEmailResolver.istFreemailDomain("musterstahl.example")).isFalse();
        assertThat(LieferantEmailResolver.istFreemailDomain(null)).isFalse();
    }
}
