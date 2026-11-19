package org.example.kalkulationsprogramm.service.rechnungpdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Reine Rechnung hinter dem Abschnitt „Abrechnungsstand“. */
class AbrechnungsstandRechnungTest {

    private static BigDecimal b(String wert) {
        return new BigDecimal(wert);
    }

    @Test
    void bruttoRundetDieSteuerKaufmaennischAufCent() {
        assertThat(AbrechnungsstandRechnung.brutto(b("6430.76"))).isEqualByComparingTo("7652.60");
        assertThat(AbrechnungsstandRechnung.brutto(null)).isEqualByComparingTo("0");
    }

    @Test
    void abschlagOhneAbschlussLaesstRestOffen() {
        AbrechnungsstandRechnung r = AbrechnungsstandRechnung.fuer(b("10000"), b("2000"), b("3000"), false, false);

        assertThat(r.dieseNetto()).isEqualByComparingTo("3000");
        assertThat(r.dieseSteuer()).isEqualByComparingTo("570.00");
        assertThat(r.dieseBrutto()).isEqualByComparingTo("3570.00");
        assertThat(r.hatDifferenz()).isFalse();
        assertThat(r.restNetto()).isEqualByComparingTo("5000");
        assertThat(r.prozentAbgerechnet()).isEqualTo(50.0);
        assertThat(r.anteilDieser()).isEqualByComparingTo("30.0");
    }

    @Test
    void schlussrechnungZiehtVorrechnungenVomPositionsbetragAb() {
        AbrechnungsstandRechnung r = AbrechnungsstandRechnung.fuer(b("10000"), b("4000"), b("8000"), true, true);

        assertThat(r.dieseNetto()).isEqualByComparingTo("4000");
        assertThat(r.differenzNetto()).isEqualByComparingTo("2000");
        assertThat(r.hatDifferenz()).isTrue();
        assertThat(r.differenzEntfallen()).isTrue();
        assertThat(r.restNetto()).isEqualByComparingTo("0");
        assertThat(r.prozentAbgerechnet()).isEqualTo(100.0);
    }

    @Test
    void zusatzleistungIstNegativeDifferenzUndRestBleibtNull() {
        // Beispiel aus dem Nutzerfall: Angebot 6.430,76 €, Rechnung 7.710,90 € ueber den ganzen Auftrag.
        AbrechnungsstandRechnung r = AbrechnungsstandRechnung.fuer(b("6430.76"), BigDecimal.ZERO, b("7710.90"), false, true);

        assertThat(r.differenzNetto()).isEqualByComparingTo("-1280.14");
        assertThat(r.differenzEntfallen()).isFalse();
        assertThat(AbrechnungsstandRechnung.brutto(r.differenzNetto().abs())).isEqualByComparingTo("1523.37");
        assertThat(r.restNetto()).isEqualByComparingTo("0");
    }

    @Test
    void ohneAbschlussGibtEsKeineDifferenz() {
        AbrechnungsstandRechnung r = AbrechnungsstandRechnung.fuer(b("10000"), BigDecimal.ZERO, b("9600"), false, false);

        assertThat(r.differenzNetto()).isEqualByComparingTo("0");
        assertThat(r.hatDifferenz()).isFalse();
        assertThat(r.restNetto()).isEqualByComparingTo("400");
        assertThat(r.prozentAbgerechnet()).isEqualTo(96.0);
    }

    @Test
    void differenzUnterEinemCentZaehltNicht() {
        AbrechnungsstandRechnung r = AbrechnungsstandRechnung.fuer(b("10000"), BigDecimal.ZERO, b("9999.995"), false, true);

        assertThat(r.hatDifferenz()).isFalse();
    }
}
