package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailantwortVorschau;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.EinkaufVersandAngenommen;
import org.example.kalkulationsprogramm.repository.EinkaufMailantwortVorschauRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufMailantwortVersandListener;
import org.junit.jupiter.api.Test;

class EinkaufMailantwortVersandListenerTest {
    @Test
    void nimmtNurDieGebundeneAntwortVorschauIdempotentAn() {
        var repository = mock(EinkaufMailantwortVorschauRepository.class);
        var vorschau = new EinkaufMailantwortVorschau("b".repeat(64), 4L, "EINKAUF", "ANFRAGE", 9L, 3L, 2L,
                "<reply@example.invalid>", "<parent@example.invalid>", List.of("<parent@example.invalid>"),
                "supplier@example.invalid", "Re: Anfrage", "<p>Antwort</p>", List.of(), "c".repeat(64),
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
        vorschau.setVersandId(31L);
        when(repository.findByVersandIdAndEmailId(31L, 4L)).thenReturn(Optional.of(vorschau));
        var listener = new EinkaufMailantwortVersandListener(repository);
        var event = new EinkaufVersandAngenommen(java.util.UUID.randomUUID(), 31L, "ANTWORT", 4L, 2L, 3L, Instant.EPOCH.plusSeconds(5));

        listener.verarbeite(event);
        listener.verarbeite(event);

        assertEquals(Instant.EPOCH.plusSeconds(5), vorschau.getAngenommenAm());
        verify(repository, org.mockito.Mockito.times(2)).save(vorschau);
        assertEquals(java.util.Set.of("ANTWORT"), listener.unterstuetzteVorgangstypen());
    }
}
