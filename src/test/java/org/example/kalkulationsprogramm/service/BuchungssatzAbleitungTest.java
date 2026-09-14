package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegQuelle;
import org.example.kalkulationsprogramm.domain.KasseEinstellung;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.domain.SachkontoTyp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests fuer das Doppik-Variante-A-Mapping (Issue #61): pruft alle
 * Kombinationen von Beleg-Kategorie x Sachkonto-Typ inkl. Robustheit bei
 * fehlendem Sachkonto.
 */
class BuchungssatzAbleitungTest {

    @Test
    @DisplayName("KASSE_EINNAHME + Ertrag-Konto: Soll=Kasse, Haben=Sachkonto")
    void einnahmeMitErtragKonto() {
        Beleg b = beleg(BelegKategorie.KASSE_EINNAHME,
                sachkonto("4400", "Erlöse 19%", SachkontoTyp.ERTRAG));

        BuchungssatzAbleitung.Buchungssatz bs = BuchungssatzAbleitung.ableiten(b);

        assertThat(bs.soll()).isEqualTo(BuchungssatzAbleitung.KASSE);
        assertThat(bs.haben()).contains("Erlöse");
    }

    @Test
    @DisplayName("KASSE_EINNAHME ohne Sachkonto -> Bank-Konto als Haben (Bank->Kasse Abhebung)")
    void einnahmeOhneSachkonto_HabenIstBank() {
        Beleg b = beleg(BelegKategorie.KASSE_EINNAHME, null);

        BuchungssatzAbleitung.Buchungssatz bs = BuchungssatzAbleitung.ableiten(b);

        assertThat(bs.soll()).isEqualTo(BuchungssatzAbleitung.KASSE);
        assertThat(bs.haben()).isEqualTo(BuchungssatzAbleitung.BANK);
    }

    @Test
    @DisplayName("KASSE_AUSGABE + Aufwand-Konto: Soll=Sachkonto, Haben=Kasse")
    void ausgabeMitAufwand() {
        Beleg b = beleg(BelegKategorie.KASSE_AUSGABE,
                sachkonto("4530", "Tankkosten", SachkontoTyp.AUFWAND));

        BuchungssatzAbleitung.Buchungssatz bs = BuchungssatzAbleitung.ableiten(b);

        assertThat(bs.soll()).contains("Tankkosten");
        assertThat(bs.haben()).isEqualTo(BuchungssatzAbleitung.KASSE);
    }

    @Test
    @DisplayName("KASSE_AUSGABE ohne Sachkonto -> Soll=?")
    void ausgabeOhneSachkonto_SollIstFragezeichen() {
        Beleg b = beleg(BelegKategorie.KASSE_AUSGABE, null);

        BuchungssatzAbleitung.Buchungssatz bs = BuchungssatzAbleitung.ableiten(b);

        assertThat(bs.soll()).isEqualTo(BuchungssatzAbleitung.UNKLAR);
        assertThat(bs.haben()).isEqualTo(BuchungssatzAbleitung.KASSE);
    }

    @Test
    @DisplayName("PRIVATEINLAGE: Soll=Kasse, Haben=Privateinlage")
    void privateinlageOhneSachkonto() {
        Beleg b = beleg(BelegKategorie.PRIVATEINLAGE, null);

        BuchungssatzAbleitung.Buchungssatz bs = BuchungssatzAbleitung.ableiten(b);

        assertThat(bs.soll()).isEqualTo(BuchungssatzAbleitung.KASSE);
        assertThat(bs.haben()).isEqualTo(BuchungssatzAbleitung.PRIVATEINLAGE);
    }

    @Test
    @DisplayName("PRIVATENTNAHME: Soll=Privatentnahme, Haben=Kasse")
    void privatentnahmeOhneSachkonto() {
        Beleg b = beleg(BelegKategorie.PRIVATENTNAHME, null);

        BuchungssatzAbleitung.Buchungssatz bs = BuchungssatzAbleitung.ableiten(b);

        assertThat(bs.soll()).isEqualTo(BuchungssatzAbleitung.PRIVATENTNAHME);
        assertThat(bs.haben()).isEqualTo(BuchungssatzAbleitung.KASSE);
    }

    @Test
    @DisplayName("BANK / KREDITKARTE / SONSTIGER_BELEG: Soll=Haben=?")
    void nichtKasse_FragezeichenSollUndHaben() {
        for (BelegKategorie k : new BelegKategorie[]{
                BelegKategorie.BANK, BelegKategorie.KREDITKARTE,
                BelegKategorie.SONSTIGER_BELEG, BelegKategorie.UNZUGEORDNET}) {
            BuchungssatzAbleitung.Buchungssatz bs =
                    BuchungssatzAbleitung.ableiten(beleg(k, null));
            assertThat(bs.soll()).as("Soll fuer " + k).isEqualTo(BuchungssatzAbleitung.UNKLAR);
            assertThat(bs.haben()).as("Haben fuer " + k).isEqualTo(BuchungssatzAbleitung.UNKLAR);
        }
    }

    @Test
    @DisplayName("Null-Beleg: liefert ? / ? statt NPE")
    void nullBeleg_FragezeichenSollUndHaben() {
        BuchungssatzAbleitung.Buchungssatz bs = BuchungssatzAbleitung.ableiten(null);
        assertThat(bs.soll()).isEqualTo(BuchungssatzAbleitung.UNKLAR);
        assertThat(bs.haben()).isEqualTo(BuchungssatzAbleitung.UNKLAR);
    }

    private static Beleg beleg(BelegKategorie kategorie, Sachkonto sachkonto) {
        Beleg b = new Beleg();
        b.setBelegKategorie(kategorie);
        b.setSachkonto(sachkonto);
        return b;
    }

    @Test
    void datevKontenFuerAlleKategorienUndTransfers() {
        KasseEinstellung e = new KasseEinstellung();
        for (BelegKategorie k : BelegKategorie.values()) {
            Beleg b = beleg(k, sachkonto("4930", "Musterkonto", SachkontoTyp.AUFWAND));
            var konten = BuchungssatzAbleitung.ableitenKonten(b, e);
            if (!k.istKassenBewegung()) {
                assertThat(konten.sollKontoNr()).isNull();
                assertThat(konten.habenKontoNr()).isNull();
            } else {
                assertThat(konten.sollKontoNr()).isEqualTo(k.istAusgang() ? "4930" : "1000");
                assertThat(konten.habenKontoNr()).isEqualTo(k.istAusgang() ? "1000" : "4930");
            }
        }
        e.setKassenkontoNummer("1600"); e.setBankkontoNummer("1210");
        Beleg b = beleg(BelegKategorie.KASSE_EINNAHME, null); b.setQuelle(BelegQuelle.TRANSFER);
        assertThat(BuchungssatzAbleitung.ableitenKonten(b, e)).isEqualTo(new BuchungssatzAbleitung.Konten("1600", "1210"));
        b.setBelegKategorie(BelegKategorie.KASSE_AUSGABE);
        assertThat(BuchungssatzAbleitung.ableitenKonten(b, e)).isEqualTo(new BuchungssatzAbleitung.Konten("1210", "1600"));
    }

    @Test
    void datevFehlendeKontenUndPrivateDefaults() {
        assertThat(BuchungssatzAbleitung.ableitenKonten(beleg(BelegKategorie.PRIVATEINLAGE, null), null))
                .isEqualTo(new BuchungssatzAbleitung.Konten("1000", "1810"));
        assertThat(BuchungssatzAbleitung.ableitenKonten(beleg(BelegKategorie.PRIVATENTNAHME, null), null))
                .isEqualTo(new BuchungssatzAbleitung.Konten("1800", "1000"));
        assertThat(BuchungssatzAbleitung.ableitenKonten(beleg(BelegKategorie.KASSE_AUSGABE, null), null))
                .isEqualTo(new BuchungssatzAbleitung.Konten(null, "1000"));
        assertThat(BuchungssatzAbleitung.ableitenKonten(beleg(BelegKategorie.KASSE_EINNAHME, null), null))
                .isEqualTo(new BuchungssatzAbleitung.Konten("1000", null));
    }

    @Test
    void privateGegenbuchungVerwendetDasKontoDesOriginals() {
        Beleg stornoEinlage = beleg(BelegKategorie.PRIVATENTNAHME, null);
        stornoEinlage.setStornoFuerBelegId(10L);
        assertThat(BuchungssatzAbleitung.ableitenKonten(stornoEinlage, null))
                .isEqualTo(new BuchungssatzAbleitung.Konten("1810", "1000"));
        Beleg stornoEntnahme = beleg(BelegKategorie.PRIVATEINLAGE, null);
        stornoEntnahme.setStornoFuerBelegId(11L);
        assertThat(BuchungssatzAbleitung.ableitenKonten(stornoEntnahme, null))
                .isEqualTo(new BuchungssatzAbleitung.Konten("1000", "1800"));
    }

    private static Sachkonto sachkonto(String nummer, String bezeichnung, SachkontoTyp typ) {
        Sachkonto sk = new Sachkonto();
        sk.setNummer(nummer);
        sk.setBezeichnung(bezeichnung);
        sk.setKontoTyp(typ);
        return sk;
    }
}
