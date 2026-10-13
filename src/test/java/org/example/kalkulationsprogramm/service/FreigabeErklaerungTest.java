package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FreigabeErklaerungTest {

    @Test
    void bereinigt_entferntSteuerzeichenUndKuerztAufSpaltenlaenge() {
        FreigabeErklaerung roh = new FreigabeErklaerung(true, "2026-10-13-sehr-lange-kennung",
                "\t2026-10-13\n", "Ich\u0000 beauftrage   <script>alert(1)</script>", "k".repeat(200));

        FreigabeErklaerung b = roh.bereinigt();

        assertThat(b.vorzeitigerBeginn()).isTrue();
        assertThat(b.widerrufStand()).hasSize(20);
        assertThat(b.datenschutzStand()).isEqualTo("2026-10-13");
        // HTML bleibt als Text erhalten – escapt wird bei der Ausgabe, nicht beim Speichern.
        assertThat(b.bestaetigungText()).isEqualTo("Ich beauftrage <script>alert(1)</script>");
        assertThat(b.knopfText()).hasSize(100);
    }

    @Test
    void bereinigt_leereTexteWerdenNull() {
        FreigabeErklaerung b = new FreigabeErklaerung(null, "  ", "", "\n\t", null).bereinigt();

        assertThat(b).isEqualTo(FreigabeErklaerung.KEINE);
    }
}
