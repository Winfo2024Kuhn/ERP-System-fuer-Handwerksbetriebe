package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueft {@link ZahlungsartMapper} gegen die verbindliche Tabelle aus
 * Entscheidung 1 des Orchestrators (docs/superpowers/specs/2026-09-09-kasse-belege.md).
 */
class ZahlungsartMapperTest {

    private static Stream<Arguments> tabellenZeilen() {
        return Stream.of(
                Arguments.of("BAR", "Bar", BelegKategorie.KASSE_AUSGABE, true),
                Arguments.of("EC", "EC-Karte", BelegKategorie.BANK, true),
                Arguments.of("EC_KARTE", "EC-Karte", BelegKategorie.BANK, true),
                Arguments.of("GIROCARD", "EC-Karte", BelegKategorie.BANK, true),
                Arguments.of("UEBERWEISUNG", "Überweisung", BelegKategorie.BANK, false),
                Arguments.of("SEPA_LASTSCHRIFT", "Lastschrift", BelegKategorie.BANK, false),
                Arguments.of("LASTSCHRIFT", "Lastschrift", BelegKategorie.BANK, false),
                Arguments.of("KREDITKARTE", "Kreditkarte", BelegKategorie.KREDITKARTE, true),
                Arguments.of("PAYPAL", "PayPal", BelegKategorie.BANK, true),
                Arguments.of("AMAZON_PAY", "Online-Zahlung", BelegKategorie.BANK, true),
                Arguments.of("VORAUSKASSE", "Überweisung", BelegKategorie.BANK, true),
                Arguments.of("RECHNUNG", "Rechnung", BelegKategorie.SONSTIGER_BELEG, false),
                Arguments.of("SCHECK", "Scheck", BelegKategorie.BANK, false)
        );
    }

    @ParameterizedTest(name = "{0} -> Stammdaten {1}, Kategorie {2}, bezahlt={3}")
    @MethodSource("tabellenZeilen")
    void mapptKiCodeGemaessVerbindlicherTabelle(
            String kiCode, String erwarteteStammdaten, BelegKategorie erwarteteKategorie, boolean erwartetBezahlt) {
        assertThat(ZahlungsartMapper.zuStammdaten(kiCode)).isEqualTo(erwarteteStammdaten);
        // richtungAusgabe=true fuer alle Zeilen -- BAR-Richtung wird separat
        // unten geprueft (barRichtungBestimmtKasseEinnahmeOderAusgabe).
        assertThat(ZahlungsartMapper.zuKategorie(erwarteteStammdaten, true)).isEqualTo(erwarteteKategorie);
        assertThat(ZahlungsartMapper.giltAlsBezahlt(kiCode)).isEqualTo(erwartetBezahlt);
    }

    @Test
    void nullLeerUndUnbekanntErgebenKeineZuordnungUndGeltenNichtAlsBezahlt() {
        for (String eingabe : new String[]{null, "", "   ", "SONSTIGE", "FANTASIEWAEHRUNG"}) {
            assertThat(ZahlungsartMapper.zuStammdaten(eingabe)).as("zuStammdaten(%s)", eingabe).isNull();
            assertThat(ZahlungsartMapper.giltAlsBezahlt(eingabe)).as("giltAlsBezahlt(%s)", eingabe).isFalse();
        }
        assertThat(ZahlungsartMapper.zuKategorie(null, true)).isNull();
    }

    @Test
    void zuStammdatenIstIdempotentFuerBereitsMigrierteKlartextwerte() {
        assertThat(ZahlungsartMapper.zuStammdaten("Überweisung")).isEqualTo("Überweisung");
        assertThat(ZahlungsartMapper.zuStammdaten("Bar")).isEqualTo("Bar");
        assertThat(ZahlungsartMapper.zuStammdaten("Online-Zahlung")).isEqualTo("Online-Zahlung");
    }

    @Test
    void barRichtungBestimmtKasseEinnahmeOderAusgabe() {
        assertThat(ZahlungsartMapper.zuKategorie("Bar", true)).isEqualTo(BelegKategorie.KASSE_AUSGABE);
        assertThat(ZahlungsartMapper.zuKategorie("Bar", false)).isEqualTo(BelegKategorie.KASSE_EINNAHME);
    }
}
