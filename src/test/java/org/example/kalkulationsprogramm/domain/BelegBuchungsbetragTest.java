package org.example.kalkulationsprogramm.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Regel "Firmenanteil statt Originalbetrag" an einer Stelle festgenagelt.
 *
 * Sie entscheidet, wie viel eines Mischbons (privater Einkauf + Buerokaffee)
 * in die Gemeinkosten und damit in den Stundensatz einfliesst. PC-Pfad,
 * Handy-Pfad und Stundensatz-Rechner haengen alle daran.
 */
class BelegBuchungsbetragTest {

    private static Beleg beleg(BelegAufteilungsModus modus, String netto, String brutto,
                               String firmaNetto, String firmaBrutto) {
        Beleg b = new Beleg();
        b.setAufteilungsModus(modus);
        if (netto != null) b.setBetragNetto(new BigDecimal(netto));
        if (brutto != null) b.setBetragBrutto(new BigDecimal(brutto));
        if (firmaNetto != null) b.setBetragFirmaNetto(new BigDecimal(firmaNetto));
        if (firmaBrutto != null) b.setBetragFirmaBrutto(new BigDecimal(firmaBrutto));
        return b;
    }

    @Test
    @DisplayName("Mischbon: nur der Firmenanteil wird gebucht")
    void teilweiseNimmtFirmenanteil() {
        Beleg b = beleg(BelegAufteilungsModus.TEILWEISE, "100.00", "119.00", "40.00", "47.60");

        assertThat(b.istTeilweiseFirma()).isTrue();
        assertThat(b.getBuchungsbetragNetto()).isEqualByComparingTo("40.00");
        assertThat(b.getBuchungsbetragBrutto()).isEqualByComparingTo("47.60");
    }

    @Test
    @DisplayName("Vollstaendiger Beleg: der Originalbetrag wird gebucht")
    void vollstaendigNimmtOriginalbetrag() {
        Beleg b = beleg(BelegAufteilungsModus.VOLLSTAENDIG, "100.00", "119.00", null, null);

        assertThat(b.istTeilweiseFirma()).isFalse();
        assertThat(b.getBuchungsbetragNetto()).isEqualByComparingTo("100.00");
        assertThat(b.getBuchungsbetragBrutto()).isEqualByComparingTo("119.00");
    }

    @Test
    @DisplayName("VOLLSTAENDIG mit Firmenbetrags-Rest aus Altdaten: Originalbetrag gewinnt")
    void vollstaendigIgnoriertVerwaisteFirmenbetraege() {
        // Der Modus ist massgeblich, nicht das blosse Vorhandensein der Felder.
        Beleg b = beleg(BelegAufteilungsModus.VOLLSTAENDIG, "100.00", "119.00", "40.00", "47.60");

        assertThat(b.istTeilweiseFirma()).isFalse();
        assertThat(b.getBuchungsbetragNetto()).isEqualByComparingTo("100.00");
        assertThat(b.getBuchungsbetragBrutto()).isEqualByComparingTo("119.00");
    }

    @Test
    @DisplayName("TEILWEISE ohne bezifferten Firmenanteil faellt auf den Originalbetrag zurueck")
    void teilweiseOhneBetraegeNimmtOriginal() {
        // Positionen noch nicht angehakt -> lieber den vollen Betrag ansetzen
        // als 0, sonst verschwinden Kosten stillschweigend.
        Beleg b = beleg(BelegAufteilungsModus.TEILWEISE, "100.00", "119.00", null, null);

        assertThat(b.istTeilweiseFirma()).isFalse();
        assertThat(b.getBuchungsbetragNetto()).isEqualByComparingTo("100.00");
        assertThat(b.getBuchungsbetragBrutto()).isEqualByComparingTo("119.00");
    }

    @Test
    @DisplayName("Nur Firma-Brutto gesetzt: dient auch als Netto-Ersatz")
    void firmaBruttoAlsNettoErsatz() {
        Beleg b = beleg(BelegAufteilungsModus.TEILWEISE, "100.00", "119.00", null, "47.60");

        assertThat(b.istTeilweiseFirma()).isTrue();
        assertThat(b.getBuchungsbetragNetto()).isEqualByComparingTo("47.60");
        assertThat(b.getBuchungsbetragBrutto()).isEqualByComparingTo("47.60");
    }

    @Test
    @DisplayName("Kassenbon ohne Netto-Ausweis: Brutto dient als Netto-Ersatz")
    void ohneNettoFaelltAufBruttoZurueck() {
        Beleg b = beleg(BelegAufteilungsModus.VOLLSTAENDIG, null, "119.00", null, null);

        assertThat(b.getBuchungsbetragNetto()).isEqualByComparingTo("119.00");
        assertThat(b.getBuchungsbetragBrutto()).isEqualByComparingTo("119.00");
    }

    @Test
    @DisplayName("Beleg ganz ohne Betraege liefert null statt zu werfen")
    void ohneBetraegeNull() {
        Beleg b = beleg(BelegAufteilungsModus.VOLLSTAENDIG, null, null, null, null);

        assertThat(b.getBuchungsbetragNetto()).isNull();
        assertThat(b.getBuchungsbetragBrutto()).isNull();
    }
}
