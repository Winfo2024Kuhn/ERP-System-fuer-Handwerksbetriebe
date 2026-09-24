package org.example.kalkulationsprogramm.domain.einkauf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EinkaufMailantwortVorschauTest {
    @Test
    void speichertUnveraenderlicheAnlagenSnapshots() {
        var anlagen = new java.util.ArrayList<>(List.of(new EinkaufMailantwortVorschau.AnlageSnapshot(7L, "a".repeat(64))));
        var vorschau = new EinkaufMailantwortVorschau("b".repeat(64), 4L, "EINKAUF", "ANFRAGE", 9L, 3L, 2L,
                "<reply@example.invalid>", "<parent@example.invalid>", List.of("<first@example.invalid>"),
                "supplier@example.invalid", "Re: Anfrage", "<p>Antwort</p>", anlagen,
                "c".repeat(64), Instant.parse("2026-09-24T10:00:00Z"), Instant.parse("2026-09-25T10:00:00Z"));
        anlagen.clear();

        assertEquals(1, vorschau.getAnlagen().size());
        assertThrows(UnsupportedOperationException.class, () -> vorschau.getAnlagen().clear());
    }

    @Test
    void bindetVersandauftragNurEinmalUndNimmtAnnahmeIdempotentAuf() {
        var vorschau = new EinkaufMailantwortVorschau("b".repeat(64), 4L, "EINKAUF", "ANFRAGE", 9L, 3L, 2L,
                "<reply@example.invalid>", "<parent@example.invalid>", List.of("<parent@example.invalid>"),
                "supplier@example.invalid", "Re: Anfrage", "<p>Antwort</p>", List.of(), "c".repeat(64),
                Instant.parse("2026-09-24T10:00:00Z"), Instant.parse("2026-09-25T10:00:00Z"));
        vorschau.setVersandId(31L);
        vorschau.markiereAngenommen(Instant.parse("2026-09-24T11:00:00Z"));
        vorschau.markiereAngenommen(Instant.parse("2026-09-24T12:00:00Z"));

        assertEquals(31L, vorschau.getVersandId());
        assertEquals(Instant.parse("2026-09-24T11:00:00Z"), vorschau.getAngenommenAm());
        assertThrows(IllegalStateException.class, () -> vorschau.setVersandId(32L));
    }
}
