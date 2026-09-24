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
        var replyPreviews = mock(org.example.kalkulationsprogramm.repository.EinkaufMailantwortVorschauRepository.class);
        var dispatches = mock(org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository.class);
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
        var service = new EinkaufKommunikationService(anfragen, revisionen, beteiligungen, emails, zuordnungen, previews, replyPreviews, dispatches,
                vorlagen, pdf, dateien, outbox, worker, mock(EinkaufMailantwortVersandListener.class), new ObjectMapper());

        var result = service.senden(11L, 13L, new Freigabe(12, token, key), 5L);

        assertEquals("ANGENOMMEN", result.status());
        verify(outbox).findeWiederholungsauftrag(key, token, 11L, 13L);
        verifyNoInteractions(anfragen, revisionen, beteiligungen, vorlagen, pdf, dateien, worker);
    }

    @Test
    void clientErfundeneJsonFreigabeKannKeineAnderePDFVorschauFreigeben() {
        var outbox = mock(EinkaufOutboxService.class);
        var previews = mock(org.example.kalkulationsprogramm.repository.EinkaufKommunikationVorschauRepository.class);
        var replyPreviews = mock(org.example.kalkulationsprogramm.repository.EinkaufMailantwortVorschauRepository.class);
        var dispatches = mock(org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository.class);
        var vorlagen = mock(EinkaufVorlagenService.class);
        var pdf = mock(EinkaufPdfService.class);
        var dateien = mock(EinkaufDateiService.class);
        var worker = mock(EinkaufVersandWorker.class);
        var key = UUID.randomUUID();
        String forged = "{\"templateId\":3,\"pdfDateiId\":999,\"snapshotHash\":\"self-made\"}";
        var service = new EinkaufKommunikationService(mock(EinkaufsanfrageRepository.class),
                mock(AnfrageRevisionRepository.class), mock(AnfrageLieferantRepository.class),
                mock(EmailRepository.class), mock(EinkaufMailZuordnungRepository.class), previews, replyPreviews, dispatches,
                vorlagen, pdf, dateien, outbox, worker, mock(EinkaufMailantwortVersandListener.class), new ObjectMapper());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.senden(11L, 13L, new Freigabe(12, forged, key), 5L));

        verify(previews).findByFreigabeTokenAndAnfrageIdAndBeteiligungId(forged, 11L, 13L);
        verifyNoInteractions(vorlagen, pdf, dateien, worker);
    }
    @Test
    void verlaufLaedtNurNachrichtenDerAktuellenSeiteInEinemBatch() {
        var emails = mock(EmailRepository.class);
        var links = mock(EinkaufMailZuordnungRepository.class);
        var page = org.springframework.data.domain.PageRequest.of(1, 2);
        var first = new org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailZuordnung(2L);
        var second = new org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailZuordnung(3L);
        first.automatisch("ANFRAGE", 11L, 13L, 12L, "ANGEBOT", "CODE_ABSENDER");
        second.automatisch("ANFRAGE", 11L, 13L, 12L, "ANGEBOT", "CODE_ABSENDER");
        when(links.findAllByTypAndVorgangId("ANFRAGE", 11L, page)).thenReturn(
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(first, second), page, 5));
        var mail2 = new org.example.kalkulationsprogramm.domain.Email(); mail2.setId(2L); mail2.setSubject("Zwei");
        var mail3 = new org.example.kalkulationsprogramm.domain.Email(); mail3.setId(3L); mail3.setSubject("Drei");
        when(emails.findAllById(java.util.List.of(2L, 3L))).thenReturn(java.util.List.of(mail3, mail2));
        var service = new EinkaufKommunikationService(mock(EinkaufsanfrageRepository.class), mock(AnfrageRevisionRepository.class),
                mock(AnfrageLieferantRepository.class), emails, links,
                mock(org.example.kalkulationsprogramm.repository.EinkaufKommunikationVorschauRepository.class),
                mock(org.example.kalkulationsprogramm.repository.EinkaufMailantwortVorschauRepository.class),
                mock(org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository.class),
                mock(EinkaufVorlagenService.class), mock(EinkaufPdfService.class), mock(EinkaufDateiService.class),
                mock(EinkaufOutboxService.class), mock(EinkaufVersandWorker.class), mock(EinkaufMailantwortVersandListener.class), new ObjectMapper());
        var result = service.verlauf("ANFRAGE", 11L, page);
        assertEquals(5, result.getTotalElements());
        assertEquals(java.util.List.of("Zwei", "Drei"), result.stream().map(n -> n.subject()).toList());
        verify(emails).findAllById(java.util.List.of(2L, 3L));
        verifyNoMoreInteractions(emails);
    }

}
