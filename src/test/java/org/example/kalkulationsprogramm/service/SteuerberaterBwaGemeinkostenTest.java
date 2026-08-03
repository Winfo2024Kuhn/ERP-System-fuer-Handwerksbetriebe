package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests fuer die Gemeinkosten-Extraktion aus der KI-Antwort zu einer BWA.
 *
 * Hintergrund: Die Karten unter "BWA / Auswertungen" zeigten fuer jede BWA
 * "Analysiert" und daneben 0,00 EUR. Ursache war unter anderem, dass
 * {@code root.has("gemeinkosten")} auch bei einem ausdruecklichen JSON-null
 * true liefert und {@code asDouble()} daraus stillschweigend 0 machte.
 */
class SteuerberaterBwaGemeinkostenTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private BigDecimal ausJson(String json) throws Exception {
        return SteuerberaterEmailProcessingService.ermittleGemeinkosten(mapper.readTree(json));
    }

    @Test
    void nimmtDenAusgewiesenenGemeinkostenBetrag() throws Exception {
        assertThat(ausJson("{\"gemeinkosten\": 12500.50, \"gesamtkosten\": 40000, \"personalkosten\": 27499.50}"))
                .isEqualByComparingTo("12500.50");
    }

    @Test
    void rechnetGesamtkostenMinusPersonalkostenWennGemeinkostenFehlen() throws Exception {
        // Der Normalfall: eine DATEV-BWA weist keine Zeile "Gemeinkosten" aus.
        assertThat(ausJson("{\"gesamtkosten\": 40000.00, \"personalkosten\": 25000.00}"))
                .isEqualByComparingTo("15000.00");
    }

    @Test
    void ziehtWareneinsatzUndFremdleistungenAb() throws Exception {
        // Material und Subunternehmer gehen direkt aufs Projekt und sind keine
        // Gemeinkosten. Blieben sie drin, waere der Stundensatz zu hoch.
        assertThat(ausJson("{\"gesamtkosten\": 100000, \"personalkosten\": 40000,"
                + " \"wareneinsatz\": 25000, \"fremdleistungen\": 10000}"))
                .isEqualByComparingTo("25000.00");
    }

    @Test
    void kommtOhneWareneinsatzUndFremdleistungenAus() throws Exception {
        // Aeltere/knappe BWA ohne diese Zeilen: die Abzuege sind schlicht 0.
        assertThat(ausJson("{\"gesamtkosten\": 50000, \"personalkosten\": 30000,"
                + " \"wareneinsatz\": null, \"fremdleistungen\": null}"))
                .isEqualByComparingTo("20000.00");
    }

    @Test
    void liefertNullWennDirekteKostenAllesAufzehren() throws Exception {
        assertThat(ausJson("{\"gesamtkosten\": 50000, \"personalkosten\": 30000,"
                + " \"wareneinsatz\": 25000}")).isNull();
    }

    @Test
    void behandeltAusdrueecklichesJsonNullNichtAlsNull() throws Exception {
        // Der eigentliche Bug: has() ist bei JSON-null true, asDouble() liefert 0.
        assertThat(ausJson("{\"gemeinkosten\": null, \"gesamtkosten\": 30000, \"personalkosten\": 20000}"))
                .isEqualByComparingTo("10000");
    }

    @Test
    void liefertNullWennGarNichtsVerwertbaresDrinsteht() throws Exception {
        assertThat(ausJson("{\"gemeinkosten\": null, \"gesamtkosten\": null, \"personalkosten\": null}")).isNull();
        assertThat(ausJson("{}")).isNull();
        assertThat(ausJson("{\"gemeinkosten\": 0, \"gesamtkosten\": 0}")).isNull();
    }

    @Test
    void liefertNullWennPersonalkostenDieGesamtkostenAufzehren() throws Exception {
        // Sonst stuende eine 0 oder ein Minusbetrag als "Gemeinkosten" auf der Karte.
        assertThat(ausJson("{\"gesamtkosten\": 20000, \"personalkosten\": 20000}")).isNull();
        assertThat(ausJson("{\"gesamtkosten\": 20000, \"personalkosten\": 25000}")).isNull();
    }

    @Test
    void liestBetraegeAuchWennDieKiSieAlsTextLiefert() throws Exception {
        assertThat(ausJson("{\"gemeinkosten\": \"12.500,50\"}")).isEqualByComparingTo("12500.50");
        assertThat(ausJson("{\"gemeinkosten\": \"12500.50\"}")).isEqualByComparingTo("12500.50");
        assertThat(ausJson("{\"gemeinkosten\": \"1.234,56 EUR\"}")).isEqualByComparingTo("1234.56");
        assertThat(ausJson("{\"gemeinkosten\": \"15000\"}")).isEqualByComparingTo("15000");
    }

    @Test
    void liestAuchEnglischeSchreibweiseRichtig() throws Exception {
        // Vorher wurde "1,234.56" als 1,23 EUR gelesen -- Faktor 1000 daneben,
        // still, und direkt im Stundensatz gelandet.
        assertThat(ausJson("{\"gemeinkosten\": \"1,234.56\"}")).isEqualByComparingTo("1234.56");
        assertThat(ausJson("{\"gemeinkosten\": \"12,500.00\"}")).isEqualByComparingTo("12500.00");
        assertThat(ausJson("{\"gemeinkosten\": \"1,234,567.89\"}")).isEqualByComparingTo("1234567.89");
    }

    @Test
    void liestTausenderpunkteOhneNachkommastellenRichtig() throws Exception {
        // Gespiegelter Fall: "12.500" ist die deutsche Tausenderschreibweise.
        // Wird der Punkt als Dezimalzeichen gelesen, werden aus 12.500 EUR
        // still 12,50 EUR -- wieder Faktor 1000 daneben.
        assertThat(ausJson("{\"gemeinkosten\": \"12.500\"}")).isEqualByComparingTo("12500");
        assertThat(ausJson("{\"gemeinkosten\": \"1.234\"}")).isEqualByComparingTo("1234");
        assertThat(ausJson("{\"gemeinkosten\": \"1.234.567\"}")).isEqualByComparingTo("1234567");
        assertThat(ausJson("{\"gemeinkosten\": \"1,234,567\"}")).isEqualByComparingTo("1234567");
    }

    @Test
    void behandeltEineEinzelneDreiergruppeAufBeidenSeitenGleich() throws Exception {
        // "1.234" und "1,234" sind dieselbe Zeichenform und muessen dieselbe
        // Zahl ergeben. Ein Geldbetrag mit drei Nachkommastellen kommt in einer
        // BWA nicht vor, eine Tausenderangabe dauernd.
        assertThat(ausJson("{\"gemeinkosten\": \"12,500\"}")).isEqualByComparingTo("12500");
        assertThat(ausJson("{\"gemeinkosten\": \"1,234\"}")).isEqualByComparingTo("1234");
        assertThat(ausJson("{\"gemeinkosten\": \"12.500\"}")).isEqualByComparingTo("12500");
        assertThat(ausJson("{\"gemeinkosten\": \"1.234\"}")).isEqualByComparingTo("1234");
    }

    @Test
    void haeltEchteDezimalzahlenVonTausendertrennernAuseinander() throws Exception {
        assertThat(ausJson("{\"gemeinkosten\": \"12500.50\"}")).isEqualByComparingTo("12500.50");
        assertThat(ausJson("{\"gemeinkosten\": \"1.5\"}")).isEqualByComparingTo("1.5");
        assertThat(ausJson("{\"gemeinkosten\": \"1234,56\"}")).isEqualByComparingTo("1234.56");
        assertThat(ausJson("{\"gemeinkosten\": \"0,50\"}")).isEqualByComparingTo("0.50");
    }

    @Test
    void ignoriertUnlesbarenTextStattEineNullDarausZuMachen() throws Exception {
        assertThat(ausJson("{\"gemeinkosten\": \"keine Angabe\", \"gesamtkosten\": 30000, \"personalkosten\": 18000}"))
                .isEqualByComparingTo("12000");
        assertThat(ausJson("{\"gemeinkosten\": \"n/a\"}")).isNull();
    }

    @Test
    void rundetKommazahlenSauberStattBinaerAufzublaehen() throws Exception {
        // new BigDecimal(double) haette hier 1234.5599999999999... geliefert.
        assertThat(ausJson("{\"gemeinkosten\": 1234.56}").toPlainString()).isEqualTo("1234.56");
    }

    @Test
    void liefertGenauZweiNachkommastellenFuerDieDatenbankSpalte() throws Exception {
        // bwa_upload.gesamt_gemeinkosten ist DECIMAL(14,2) -- sonst rundet die DB still.
        assertThat(ausJson("{\"gemeinkosten\": 12500.567}").toPlainString()).isEqualTo("12500.57");
        assertThat(ausJson("{\"gesamtkosten\": 30000.999, \"personalkosten\": 20000}").scale())
                .isEqualTo(2);
    }

    @Test
    void nimmtNegativeBetraegeAlsAufwandAnStattSieDurchzureichen() throws Exception {
        // Manche BWA weist Kosten mit Minuszeichen aus.
        assertThat(ausJson("{\"gemeinkosten\": -8000.00}")).isEqualByComparingTo("8000.00");
    }
}
