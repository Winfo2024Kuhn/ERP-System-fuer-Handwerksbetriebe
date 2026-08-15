package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelPreisHinweis;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ArtikelPositionsPreisServiceTest {

    private final ArtikelPositionsPreisService service = new ArtikelPositionsPreisService();

    private static void mitPreis(Artikel artikel, String preis) {
        LieferantenArtikelPreise p = new LieferantenArtikelPreise();
        p.setArtikel(artikel);
        p.setPreis(new BigDecimal(preis));
        p.setAktuell(true);
        artikel.getArtikelpreis().add(p);
    }

    @Test
    void meterwareUebernimmtDenMeterpreisUnveraendert() {
        Artikel rohr = new Artikel();
        rohr.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        rohr.setVerkaufsaufschlagProzent(BigDecimal.ZERO);
        mitPreis(rohr, "6.00");

        var vorschlag = service.berechne(rohr);

        assertThat(vorschlag.einheit()).isEqualTo("lfm");
        assertThat(vorschlag.einzelpreis()).isEqualByComparingTo("6.00");
        assertThat(vorschlag.hinweis()).isEqualTo(ArtikelPreisHinweis.OK);
    }

    @Test
    void kilopreisWirdUeberDasGewichtJeMeterAufMeterUmgerechnet() {
        ArtikelWerkstoffe traeger = new ArtikelWerkstoffe();
        traeger.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        traeger.setMasse(new BigDecimal("3.5000"));
        traeger.setVerkaufsaufschlagProzent(BigDecimal.ZERO);
        mitPreis(traeger, "2.00");

        var vorschlag = service.berechne(traeger);

        assertThat(vorschlag.einheit()).isEqualTo("lfm");
        assertThat(vorschlag.einzelpreis()).isEqualByComparingTo("7.00");
        assertThat(vorschlag.hinweis()).isEqualTo(ArtikelPreisHinweis.OK);
    }

    @Test
    void blechRechnetUeberDasGewichtJeQuadratmeter() {
        ArtikelWerkstoffe blech = new ArtikelWerkstoffe();
        blech.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        blech.setMassePerQm(new BigDecimal("15.7000"));
        blech.setVerkaufsaufschlagProzent(BigDecimal.ZERO);
        mitPreis(blech, "2.00");

        var vorschlag = service.berechne(blech);

        assertThat(vorschlag.einheit()).isEqualTo("m²");
        assertThat(vorschlag.einzelpreis()).isEqualByComparingTo("31.40");
    }

    @Test
    void stueckwareBleibtBeimStueckpreis() {
        Artikel beschlag = new Artikel();
        beschlag.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        beschlag.setVerkaufsaufschlagProzent(BigDecimal.ZERO);
        mitPreis(beschlag, "4.50");

        var vorschlag = service.berechne(beschlag);

        assertThat(vorschlag.einheit()).isEqualTo("Stk");
        assertThat(vorschlag.einzelpreis()).isEqualByComparingTo("4.50");
    }

    @Test
    void aufschlagWirdAufDenUmgerechnetenPreisAufgeschlagen() {
        ArtikelWerkstoffe traeger = new ArtikelWerkstoffe();
        traeger.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        traeger.setMasse(new BigDecimal("2.0000"));
        traeger.setVerkaufsaufschlagProzent(new BigDecimal("40.00"));
        mitPreis(traeger, "3.00");

        var vorschlag = service.berechne(traeger);

        // 3,00 x 2,0 = 6,00 -> +40% = 8,40
        assertThat(vorschlag.einzelpreis()).isEqualByComparingTo("8.40");
        assertThat(vorschlag.hinweis()).isEqualTo(ArtikelPreisHinweis.OK);
    }

    @Test
    void ohneAufschlagKommtDerEinkaufspreisMitWarnung() {
        Artikel rohr = new Artikel();
        rohr.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        mitPreis(rohr, "6.00");

        var vorschlag = service.berechne(rohr);

        assertThat(vorschlag.einzelpreis()).isEqualByComparingTo("6.00");
        assertThat(vorschlag.hinweis()).isEqualTo(ArtikelPreisHinweis.KEIN_AUFSCHLAG);
    }

    @Test
    void ohneLieferantenpreisBleibtDerPreisLeer() {
        Artikel werkstoff = new Artikel();
        werkstoff.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        werkstoff.setVerkaufsaufschlagProzent(new BigDecimal("40.00"));

        var vorschlag = service.berechne(werkstoff);

        assertThat(vorschlag.einzelpreis()).isNull();
        assertThat(vorschlag.einheit()).isEqualTo("lfm");
        assertThat(vorschlag.hinweis()).isEqualTo(ArtikelPreisHinweis.KEIN_PREIS);
    }

    @Test
    void kilogrammOhneGewichtBleibtInMeternMitLeeremPreis() {
        ArtikelWerkstoffe unvollstaendig = new ArtikelWerkstoffe();
        unvollstaendig.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        unvollstaendig.setVerkaufsaufschlagProzent(new BigDecimal("40.00"));
        mitPreis(unvollstaendig, "2.00");

        var vorschlag = service.berechne(unvollstaendig);

        assertThat(vorschlag.einheit()).isEqualTo("lfm");
        assertThat(vorschlag.einzelpreis()).isNull();
        assertThat(vorschlag.hinweis()).isEqualTo(ArtikelPreisHinweis.KEIN_GEWICHT);
    }

    @Test
    void fehlenderPreisSchlaegtFehlendesGewicht() {
        ArtikelWerkstoffe beides = new ArtikelWerkstoffe();
        beides.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);

        assertThat(service.berechne(beides).hinweis()).isEqualTo(ArtikelPreisHinweis.KEIN_PREIS);
    }

    @Test
    void ohneVerrechnungseinheitGiltStueck() {
        Artikel unbekannt = new Artikel();
        unbekannt.setVerkaufsaufschlagProzent(BigDecimal.ZERO);
        mitPreis(unbekannt, "1.20");

        assertThat(service.berechne(unbekannt).einheit()).isEqualTo("Stk");
    }

    // ------------------------------------------------------------------
    // Ueberladung mit bereits ermitteltem Preis
    // ------------------------------------------------------------------

    /** Loser Preisstand, so wie ihn der Controller nach dem Filtern in der Hand haelt. */
    private static LieferantenArtikelPreise preisstand(String preis) {
        LieferantenArtikelPreise p = new LieferantenArtikelPreise();
        p.setPreis(new BigDecimal(preis));
        p.setAktuell(true);
        return p;
    }

    @Test
    void nimmtDenUebergebenenPreisUndNichtDenGuenstigstenAmArtikel() {
        // Der Artikel traegt einen viel billigeren Preisstand. Uebergibt der
        // Aufrufer einen anderen, gilt seiner - er kennt den Lieferantenfilter,
        // der Service nicht.
        Artikel rohr = new Artikel();
        rohr.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        rohr.setVerkaufsaufschlagProzent(new BigDecimal("50.00"));
        mitPreis(rohr, "4.00");

        var vorschlag = service.berechne(rohr, preisstand("10.00"));

        assertThat(vorschlag.einzelpreis()).isEqualByComparingTo("15.00");
        assertThat(vorschlag.hinweis()).isEqualTo(ArtikelPreisHinweis.OK);
    }

    @Test
    void ohneUebergebenenPreisBleibtDerVorschlagLeerTrotzPreisAmArtikel() {
        // Der Aufrufer sagt damit: "Fuer diese Zeile gibt es keinen gueltigen
        // Preis" - etwa weil der gefilterte Lieferant keinen hinterlegt hat.
        // Ein stiller Rueckgriff auf einen anderen Lieferanten waere genau der
        // Fehler, den die Ueberladung verhindert.
        Artikel rohr = new Artikel();
        rohr.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        rohr.setVerkaufsaufschlagProzent(new BigDecimal("50.00"));
        mitPreis(rohr, "4.00");

        var vorschlag = service.berechne(rohr, null);

        assertThat(vorschlag.einzelpreis()).isNull();
        assertThat(vorschlag.einheit()).isEqualTo("lfm");
        assertThat(vorschlag.hinweis()).isEqualTo(ArtikelPreisHinweis.KEIN_PREIS);
    }

    @Test
    void rechnetDenUebergebenenKilopreisGenausoUeber() {
        ArtikelWerkstoffe traeger = new ArtikelWerkstoffe();
        traeger.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        traeger.setMasse(new BigDecimal("2.0000"));
        traeger.setVerkaufsaufschlagProzent(BigDecimal.ZERO);

        var vorschlag = service.berechne(traeger, preisstand("3.00"));

        assertThat(vorschlag.einheit()).isEqualTo("lfm");
        assertThat(vorschlag.einzelpreis()).isEqualByComparingTo("6.00");
    }

    @Test
    void derEinstiegOhnePreiskontextNimmtWeiterhinDenGuenstigstenAmArtikel() {
        Artikel rohr = new Artikel();
        rohr.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        rohr.setVerkaufsaufschlagProzent(BigDecimal.ZERO);
        mitPreis(rohr, "9.00");
        mitPreis(rohr, "4.00");

        assertThat(service.berechne(rohr).einzelpreis()).isEqualByComparingTo("4.00");
    }

    @Test
    void vertraegtEinenNullArtikelAuchMitPreis() {
        var vorschlag = service.berechne(null, preisstand("4.00"));

        assertThat(vorschlag.einheit()).isEqualTo("Stk");
        assertThat(vorschlag.einzelpreis()).isNull();
        assertThat(vorschlag.hinweis()).isEqualTo(ArtikelPreisHinweis.KEIN_PREIS);
    }
}
