package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.service.einkauf.EinkaufAntwortZuordnungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAuditService;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.einkauf.AnfrageLieferant;
import org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailZuordnung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.example.kalkulationsprogramm.repository.AnfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRevisionRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMailZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EinkaufsanfrageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class EinkaufAntwortZuordnungServiceTest {
    @Test
    void erkenntAutomatischeAntwortAnhandAutoSubmittedHeader() {
        assertEquals("AUTOMATISCHE_ANTWORT",
                EinkaufAntwortZuordnungService.klassifiziere("auto-replied", "Angebot", "Wir melden uns"));
    }

    @Test
    void klassifiziertAngebotNichtOhneExplizitenAngebotshinweis() {
        assertEquals("PRUEFEN",
                EinkaufAntwortZuordnungService.klassifiziere(null, "Ihre Anfrage", "Hier ist eine Datei."));
    }

    @Test
    void klassifiziertEindeutigenAngebotsbetreff() {
        assertEquals("ANGEBOT",
                EinkaufAntwortZuordnungService.klassifiziere(null, "Angebot A-17", "Vielen Dank für Ihre Anfrage."));
    }

    @Test
    void task11ImporteventOrdnetMitCodeUndBekanntemAbsenderAuchBeiGeaendertemBetreffZu() {
        var emails = mock(EmailRepository.class);
        var participations = mock(AnfrageLieferantRepository.class);
        var links = mock(EinkaufMailZuordnungRepository.class);
        var requests = mock(EinkaufsanfrageRepository.class);
        var revisions = mock(AnfrageRevisionRepository.class);
        String code = "0123456789abcdef0123456789abcdef";
        Email email = new Email();
        ReflectionTestUtils.setField(email, "id", 88L);
        email.setKontoId("EINKAUF"); email.setDirection(EmailDirection.IN);
        email.setFromAddress("test@example.com"); email.setSubject("Neue Betreffzeile");
        email.setBody("Zuordnungscode: " + code + "\nUnser Preis beträgt 12 Euro je Stück.");
        var head = mock(org.example.kalkulationsprogramm.domain.einkauf.Einkaufsanfrage.class);
        when(head.getId()).thenReturn(3L);
        var revision = mock(AnfrageRevision.class);
        when(revision.getId()).thenReturn(4L); when(revision.getAnfrage()).thenReturn(head);
        var supplier = mock(AnfrageLieferant.class);
        when(supplier.getId()).thenReturn(5L); when(supplier.getRueckmeldecode()).thenReturn(code);
        when(supplier.getKontakt()).thenReturn(new Snapshot(6L, 7L, "Lieferant", "test@example.com", null, null, null));
        when(supplier.getRevision()).thenReturn(revision);
        when(emails.findById(88L)).thenReturn(Optional.of(email));
        when(participations.findFirstByRueckmeldecode(code)).thenReturn(Optional.of(supplier));
        when(links.findByEmailId(88L)).thenReturn(Optional.empty());
        when(links.save(any(EinkaufMailZuordnung.class))).thenAnswer(call -> call.getArgument(0));
        var service = new EinkaufAntwortZuordnungService(emails, participations, links, requests, revisions,
                mock(EinkaufAuditService.class), new ObjectMapper(), mock(org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository.class));

        service.nachImport(new org.example.kalkulationsprogramm.service.EmailImportService.EinkaufEmailImportiert(88L));

        verify(links).save(argThat(link -> "ANFRAGE".equals(link.getTyp()) && link.getVorgangId() == 3L
                && link.getBeteiligungId() == 5L && link.getRevisionId() == 4L && "PRUEFEN".equals(link.getStatus())
                && "CODE_ABSENDER".equals(link.getQuelle())));
    }

    @Test
    void bounceUndAbsageWerdenNichtAlsAngebotGezählt() {
        assertEquals("UNZUSTELLBAR", EinkaufAntwortZuordnungService.klassifiziere(null,
                "Delivery status notification", "Undelivered mail returned to sender"));
        assertEquals("ABSAGE", EinkaufAntwortZuordnungService.klassifiziere(null,
                "Antwort", "Wir können leider kein Angebot erstellen."));
    }
}
