package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.Freigabe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto;
import org.example.kalkulationsprogramm.repository.AnfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRevisionRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMailZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EinkaufsanfrageRepository;
import org.example.kalkulationsprogramm.service.einkauf.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EinkaufKommunikationServiceTest {
    @Test
    void wiederholungEinerBereitsAngenommenenFreigabeStartetKeineErneuteVorschauUndKeinenVersand() {
        var anfragen = mock(EinkaufsanfrageRepository.class);
        var revisionen = mock(AnfrageRevisionRepository.class);
        var beteiligungen = mock(AnfrageLieferantRepository.class);
        var emails = mock(EmailRepository.class);
        var zuordnungen = mock(EinkaufMailZuordnungRepository.class);
        var previews = mock(org.example.kalkulationsprogramm.repository.EinkaufKommunikationVorschauRepository.class);
        var vorlagen = mock(EinkaufVorlagenService.class);
        var pdf = mock(EinkaufPdfService.class);
        var dateien = mock(EinkaufDateiService.class);
        var outbox = mock(EinkaufOutboxService.class);
        var worker = mock(EinkaufVersandWorker.class);
        var key = UUID.randomUUID();
        String token = "{\"templateId\":3,\"pdfDateiId\":19}";
        var sent = new VersandDto(77L, 2, "ANFRAGE", 11L, 12L, "ANGENOMMEN", null,
                Instant.EPOCH, Instant.EPOCH, true, "<mail@erp.local>");
        when(outbox.findeWiederholungsauftrag(key, token, 11L, 13L)).thenReturn(Optional.of(sent));
        var service = new EinkaufKommunikationService(anfragen, revisionen, beteiligungen, emails, zuordnungen, previews,
                vorlagen, pdf, dateien, outbox, worker, new ObjectMapper());

        var result = service.senden(11L, 13L, new Freigabe(12, token, key), 5L);

        assertEquals("ANGENOMMEN", result.status());
        verify(outbox).findeWiederholungsauftrag(key, token, 11L, 13L);
        verifyNoInteractions(anfragen, revisionen, beteiligungen, vorlagen, pdf, dateien, worker);
    }

    @Test
    void clientErfundeneJsonFreigabeKannKeineAnderePDFVorschauFreigeben() {
        var outbox = mock(EinkaufOutboxService.class);
        var previews = mock(org.example.kalkulationsprogramm.repository.EinkaufKommunikationVorschauRepository.class);
        var vorlagen = mock(EinkaufVorlagenService.class);
        var pdf = mock(EinkaufPdfService.class);
        var dateien = mock(EinkaufDateiService.class);
        var worker = mock(EinkaufVersandWorker.class);
        var key = UUID.randomUUID();
        String forged = "{\"templateId\":3,\"pdfDateiId\":999,\"snapshotHash\":\"self-made\"}";
        var service = new EinkaufKommunikationService(mock(EinkaufsanfrageRepository.class),
                mock(AnfrageRevisionRepository.class), mock(AnfrageLieferantRepository.class),
                mock(EmailRepository.class), mock(EinkaufMailZuordnungRepository.class), previews,
                vorlagen, pdf, dateien, outbox, worker, new ObjectMapper());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.senden(11L, 13L, new Freigabe(12, forged, key), 5L));

        verify(previews).findByFreigabeTokenAndAnfrageIdAndBeteiligungId(forged, 11L, 13L);
        verifyNoInteractions(vorlagen, pdf, dateien, worker);
    }
}
