package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EanPruefungTest {

    @Test
    void gueltigeNummernWerdenAkzeptiert() {
        assertThat(EanPruefung.fehler("4006381333931")).isNull();   // EAN-13
        assertThat(EanPruefung.fehler("96385074")).isNull();        // EAN-8
        assertThat(EanPruefung.fehler("036000291452")).isNull();    // UPC-A
        assertThat(EanPruefung.fehler("10012345678902")).isNull();  // GTIN-14
    }

    @Test
    void falschePruefzifferWirdAbgelehnt() {
        assertThat(EanPruefung.fehler("4006381333932")).contains("Prüfziffer");
    }

    @Test
    void falscheLaengeUndBuchstabenWerdenAbgelehnt() {
        assertThat(EanPruefung.fehler("12345")).contains("5 Stellen");
        assertThat(EanPruefung.fehler("40063813339AB")).contains("nur Ziffern");
        assertThat(EanPruefung.fehler("'; DROP TABLE artikel; --")).contains("nur Ziffern");
    }

    @Test
    void leereEingabeIstKeineEan() {
        assertThat(EanPruefung.normalisiert("  ")).isNull();
        assertThat(EanPruefung.normalisiert(null)).isNull();
        assertThat(EanPruefung.fehler(null)).isNull();
        assertThat(EanPruefung.normalisiert(" 4006 3813 33931 ")).isEqualTo("4006381333931");
    }
}
