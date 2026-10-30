package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FreigabeArbeitsbeginnTest
{
    @Test
    void hinweisFuersBuero()
    {
        DokumentFreigabe f = new DokumentFreigabe();
        assertThat(FreigabeArbeitsbeginn.hinweisFuersBuero(null)).isNull();
        assertThat(FreigabeArbeitsbeginn.hinweisFuersBuero(f)).isNull();

        f.setVorzeitigerBeginn(true);
        assertThat(FreigabeArbeitsbeginn.hinweisFuersBuero(f)).isEqualTo("Sofort beginnen erlaubt.");

        f.setVorzeitigerBeginn(false);
        f.setAkzeptiertAm(LocalDateTime.of(2026, 10, 13, 9, 30));
        assertThat(FreigabeArbeitsbeginn.hinweisFuersBuero(f))
                .isEqualTo("Erst nach Ablauf der Widerrufsfrist beginnen (bis 27.10.2026).");

        f.setAkzeptiertAm(null);
        assertThat(FreigabeArbeitsbeginn.hinweisFuersBuero(f))
                .isEqualTo("Erst nach Ablauf der Widerrufsfrist beginnen.");
    }

    @Test
    void entscheidungDesKunden()
    {
        DokumentFreigabe f = new DokumentFreigabe();
        assertThat(FreigabeArbeitsbeginn.entscheidungDesKunden(null)).isNull();
        assertThat(FreigabeArbeitsbeginn.entscheidungDesKunden(f)).isNull();

        f.setVorzeitigerBeginn(true);
        assertThat(FreigabeArbeitsbeginn.entscheidungDesKunden(f))
                .isEqualTo("Beginn vor Ablauf der Widerrufsfrist: verlangt (Häkchen gesetzt)");

        f.setVorzeitigerBeginn(false);
        assertThat(FreigabeArbeitsbeginn.entscheidungDesKunden(f))
                .isEqualTo("Beginn vor Ablauf der Widerrufsfrist: nicht verlangt (Häkchen nicht gesetzt)");
    }
}
