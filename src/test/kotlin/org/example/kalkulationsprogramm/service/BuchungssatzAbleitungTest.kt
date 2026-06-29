package org.example.kalkulationsprogramm.service

import org.assertj.core.api.Assertions.assertThat
import org.example.kalkulationsprogramm.domain.Beleg
import org.example.kalkulationsprogramm.domain.BelegKategorie
import org.example.kalkulationsprogramm.domain.Sachkonto
import org.example.kalkulationsprogramm.domain.SachkontoTyp
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests fuer das Doppik-Variante-A-Mapping (Issue #61): prueft alle
 * Kombinationen von Beleg-Kategorie x Sachkonto-Typ inkl. Robustheit bei
 * fehlendem Sachkonto.
 */
class BuchungssatzAbleitungTest {

    @Test
    @DisplayName("KASSE_EINNAHME + Ertrag-Konto: Soll=Kasse, Haben=Sachkonto")
    fun einnahmeMitErtragKonto() {
        val b = beleg(
            BelegKategorie.KASSE_EINNAHME,
            sachkonto("4400", "Erloese 19%", SachkontoTyp.ERTRAG),
        )

        val bs = BuchungssatzAbleitung.ableiten(b)

        assertThat(bs.soll).isEqualTo(BuchungssatzAbleitung.KASSE)
        assertThat(bs.haben).contains("Erloese")
    }

    @Test
    @DisplayName("KASSE_EINNAHME ohne Sachkonto -> Bank-Konto als Haben (Bank->Kasse Abhebung)")
    fun einnahmeOhneSachkontoHabenIstBank() {
        val b = beleg(BelegKategorie.KASSE_EINNAHME, null)

        val bs = BuchungssatzAbleitung.ableiten(b)

        assertThat(bs.soll).isEqualTo(BuchungssatzAbleitung.KASSE)
        assertThat(bs.haben).isEqualTo(BuchungssatzAbleitung.BANK)
    }

    @Test
    @DisplayName("KASSE_AUSGABE + Aufwand-Konto: Soll=Sachkonto, Haben=Kasse")
    fun ausgabeMitAufwand() {
        val b = beleg(
            BelegKategorie.KASSE_AUSGABE,
            sachkonto("4530", "Tankkosten", SachkontoTyp.AUFWAND),
        )

        val bs = BuchungssatzAbleitung.ableiten(b)

        assertThat(bs.soll).contains("Tankkosten")
        assertThat(bs.haben).isEqualTo(BuchungssatzAbleitung.KASSE)
    }

    @Test
    @DisplayName("KASSE_AUSGABE ohne Sachkonto -> Soll=?")
    fun ausgabeOhneSachkontoSollIstFragezeichen() {
        val b = beleg(BelegKategorie.KASSE_AUSGABE, null)

        val bs = BuchungssatzAbleitung.ableiten(b)

        assertThat(bs.soll).isEqualTo(BuchungssatzAbleitung.UNKLAR)
        assertThat(bs.haben).isEqualTo(BuchungssatzAbleitung.KASSE)
    }

    @Test
    @DisplayName("PRIVATEINLAGE: Soll=Kasse, Haben=Privateinlage")
    fun privateinlageOhneSachkonto() {
        val b = beleg(BelegKategorie.PRIVATEINLAGE, null)

        val bs = BuchungssatzAbleitung.ableiten(b)

        assertThat(bs.soll).isEqualTo(BuchungssatzAbleitung.KASSE)
        assertThat(bs.haben).isEqualTo(BuchungssatzAbleitung.PRIVATEINLAGE)
    }

    @Test
    @DisplayName("PRIVATENTNAHME: Soll=Privatentnahme, Haben=Kasse")
    fun privatentnahmeOhneSachkonto() {
        val b = beleg(BelegKategorie.PRIVATENTNAHME, null)

        val bs = BuchungssatzAbleitung.ableiten(b)

        assertThat(bs.soll).isEqualTo(BuchungssatzAbleitung.PRIVATENTNAHME)
        assertThat(bs.haben).isEqualTo(BuchungssatzAbleitung.KASSE)
    }

    @Test
    @DisplayName("BANK / KREDITKARTE / SONSTIGER_BELEG: Soll=Haben=?")
    fun nichtKasseFragezeichenSollUndHaben() {
        for (k in arrayOf(
            BelegKategorie.BANK,
            BelegKategorie.KREDITKARTE,
            BelegKategorie.SONSTIGER_BELEG,
            BelegKategorie.UNZUGEORDNET,
        )) {
            val bs = BuchungssatzAbleitung.ableiten(beleg(k, null))
            assertThat(bs.soll).`as`("Soll fuer $k").isEqualTo(BuchungssatzAbleitung.UNKLAR)
            assertThat(bs.haben).`as`("Haben fuer $k").isEqualTo(BuchungssatzAbleitung.UNKLAR)
        }
    }

    @Test
    @DisplayName("Null-Beleg: liefert ? / ? statt NPE")
    fun nullBelegFragezeichenSollUndHaben() {
        val bs = BuchungssatzAbleitung.ableiten(null)
        assertThat(bs.soll).isEqualTo(BuchungssatzAbleitung.UNKLAR)
        assertThat(bs.haben).isEqualTo(BuchungssatzAbleitung.UNKLAR)
    }

    private fun beleg(kategorie: BelegKategorie, sachkonto: Sachkonto?): Beleg =
        Beleg().apply {
            belegKategorie = kategorie
            this.sachkonto = sachkonto
        }

    private fun sachkonto(nummer: String, bezeichnung: String, typ: SachkontoTyp): Sachkonto =
        Sachkonto().apply {
            this.nummer = nummer
            this.bezeichnung = bezeichnung
            kontoTyp = typ
        }
}
