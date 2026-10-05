package org.example.kalkulationsprogramm.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.example.kalkulationsprogramm.service.EmailAutoAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailBackfillEventListenerLueckenTest {

    @Mock KundeRepository kundeRepository;
    @Mock LieferantenRepository lieferantenRepository;
    @Mock AnfrageRepository anfrageRepository;
    @Mock ProjektRepository projektRepository;
    @Mock EmailRepository emailRepository;
    @Mock EmailAttachmentProcessingService attachmentService;
    @Mock EmailAutoAssignmentService autoAssignment;
    EmailBackfillEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new EmailBackfillEventListener(kundeRepository, lieferantenRepository, anfrageRepository,
                projektRepository, emailRepository, attachmentService, autoAssignment);
    }

    private static Email mail(long id) {
        Email e = new Email();
        e.setId(id);
        return e;
    }

    private static EmailAddressChangedEvent event(EntityType t, List<String> neu) {
        return EmailAddressChangedEvent.forAddressChange(t, 1L, neu, neu);
    }

    @Test
    @DisplayName("Event-Factories setzen newEntity und Adresslisten korrekt")
    void eventFactories() {
        EmailAddressChangedEvent neu = EmailAddressChangedEvent.forNewEntity(EntityType.KUNDE, 5L, List.of("a@example.com"));
        assertTrue(neu.isNewEntity());
        assertEquals(neu.getNewAddresses(), neu.getAllAddresses());
        EmailAddressChangedEvent alt = EmailAddressChangedEvent.forAddressChange(EntityType.PROJEKT, 6L,
                List.of("b@example.com"), List.of("a@example.com", "b@example.com"));
        assertFalse(alt.isNewEntity());
        assertEquals(2, alt.getAllAddresses().size());
    }

    @Test
    @DisplayName("Keine neuen Adressen (null oder leer): es passiert nichts")
    void keineAdressen() {
        listener.handleEmailAddressChanged(event(EntityType.KUNDE, null));
        listener.handleEmailAddressChanged(event(EntityType.KUNDE, List.of()));
        verifyNoInteractions(kundeRepository, emailRepository, lieferantenRepository);
    }

    @Test
    @DisplayName("ANGEBOT-Events werden ignoriert")
    void angebot() {
        listener.handleEmailAddressChanged(event(EntityType.ANGEBOT, List.of("a@example.com")));
        verifyNoInteractions(kundeRepository, emailRepository, lieferantenRepository, projektRepository, anfrageRepository);
    }

    @Test
    @DisplayName("Fehler im Backfill werden abgefangen")
    void fehlerAbgefangen() {
        when(kundeRepository.findById(1L)).thenThrow(new IllegalStateException("DB"));
        assertDoesNotThrow(() -> listener.handleEmailAddressChanged(event(EntityType.KUNDE, List.of("a@example.com"))));
    }

    // ---- KUNDE ----

    @Test
    @DisplayName("Kunde: unbekannter Kunde -> keine Mail-Abfrage")
    void kundeUnbekannt() {
        when(kundeRepository.findById(1L)).thenReturn(Optional.empty());
        listener.handleEmailAddressChanged(event(EntityType.KUNDE, List.of("a@example.com")));
        verifyNoInteractions(emailRepository);
    }

    @Test
    @DisplayName("Kunde mit genau einem Projekt: Mail wird direkt diesem Projekt zugeordnet")
    void kundeEinProjekt() {
        Kunde k = new Kunde();
        k.setId(1L);
        Projekt p = new Projekt();
        p.setId(10L);
        Email e = mail(100L);
        when(kundeRepository.findById(1L)).thenReturn(Optional.of(k));
        when(emailRepository.findUnassignedByAddress("max@example.com")).thenReturn(List.of(e));
        when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of(p));
        when(anfrageRepository.findByKundeId(1L)).thenReturn(List.of());

        listener.handleEmailAddressChanged(event(EntityType.KUNDE, List.of("max@example.com")));

        assertEquals(EmailZuordnungTyp.PROJEKT, e.getZuordnungTyp());
        assertSame(p, e.getProjekt());
        verify(emailRepository).save(e);
        verifyNoInteractions(autoAssignment);
    }

    @Test
    @DisplayName("Kunde mit genau einer Anfrage: Mail wird dieser Anfrage zugeordnet")
    void kundeEineAnfrage() {
        Kunde k = new Kunde();
        k.setId(1L);
        Anfrage a = new Anfrage();
        a.setId(20L);
        Email e = mail(100L);
        when(kundeRepository.findById(1L)).thenReturn(Optional.of(k));
        when(emailRepository.findUnassignedByAddress(anyString())).thenReturn(List.of(e));
        when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of());
        when(anfrageRepository.findByKundeId(1L)).thenReturn(List.of(a));

        listener.handleEmailAddressChanged(event(EntityType.KUNDE, List.of("max@example.com")));

        assertEquals(EmailZuordnungTyp.ANFRAGE, e.getZuordnungTyp());
        assertSame(a, e.getAnfrage());
        verify(emailRepository).save(e);
    }

    @Test
    @DisplayName("Kunde mit mehreren Vorgaengen oder keinem: Zuordnung per Stichwort-Heuristik")
    void kundeStichworte() {
        Kunde k = new Kunde();
        k.setId(1L);
        Projekt p1 = new Projekt();
        Projekt p2 = new Projekt();
        Email e = mail(100L);
        when(kundeRepository.findById(1L)).thenReturn(Optional.of(k));
        when(emailRepository.findUnassignedByAddress(anyString())).thenReturn(List.of(e));
        when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of(p1, p2));
        when(anfrageRepository.findByKundeId(1L)).thenReturn(List.of());
        when(autoAssignment.tryAssignByKeywords(e, List.of(p1, p2), List.of())).thenReturn(true);

        listener.handleEmailAddressChanged(event(EntityType.KUNDE, List.of("max@example.com")));

        verify(autoAssignment).tryAssignByKeywords(e, List.of(p1, p2), List.of());
        verify(emailRepository, never()).save(any());
        assertEquals(EmailZuordnungTyp.KEINE, e.getZuordnungTyp());
    }

    // ---- ANFRAGE / PROJEKT ----

    @Test
    @DisplayName("Anfrage: alle unzugeordneten Mails der Adressen werden zugeordnet; unbekannte Anfrage wird ignoriert")
    void anfrage() {
        Anfrage a = new Anfrage();
        a.setId(1L);
        Email e1 = mail(1L);
        Email e2 = mail(2L);
        when(anfrageRepository.findById(1L)).thenReturn(Optional.of(a));
        when(emailRepository.findUnassignedByAddress("a@example.com")).thenReturn(List.of(e1));
        when(emailRepository.findUnassignedByAddress("b@example.com")).thenReturn(List.of(e2));

        listener.handleEmailAddressChanged(event(EntityType.ANFRAGE, List.of("a@example.com", "b@example.com")));

        assertSame(a, e1.getAnfrage());
        assertSame(a, e2.getAnfrage());
        verify(emailRepository).save(e1);
        verify(emailRepository).save(e2);

        when(anfrageRepository.findById(1L)).thenReturn(Optional.empty());
        clearInvocations(emailRepository);
        listener.handleEmailAddressChanged(event(EntityType.ANFRAGE, List.of("a@example.com")));
        verifyNoInteractions(emailRepository);
    }

    @Test
    @DisplayName("Projekt: Mails werden zugeordnet; unbekanntes Projekt wird ignoriert")
    void projekt() {
        Projekt p = new Projekt();
        p.setId(1L);
        Email e = mail(1L);
        when(projektRepository.findById(1L)).thenReturn(Optional.of(p));
        when(emailRepository.findUnassignedByAddress("a@example.com")).thenReturn(List.of(e));

        listener.handleEmailAddressChanged(event(EntityType.PROJEKT, List.of("a@example.com")));
        assertSame(p, e.getProjekt());
        assertEquals(EmailZuordnungTyp.PROJEKT, e.getZuordnungTyp());

        when(projektRepository.findById(1L)).thenReturn(Optional.empty());
        clearInvocations(emailRepository);
        listener.handleEmailAddressChanged(event(EntityType.PROJEKT, List.of("a@example.com")));
        verifyNoInteractions(emailRepository);
    }

    // ---- LIEFERANT ----

    @Test
    @DisplayName("Lieferant unbekannt: keine Mail-Abfrage")
    void lieferantUnbekannt() {
        when(lieferantenRepository.findById(1L)).thenReturn(Optional.empty());
        listener.handleEmailAddressChanged(event(EntityType.LIEFERANT, List.of("a@example.com")));
        verifyNoInteractions(emailRepository);
    }

    @Test
    @DisplayName("Lieferant: Domain-Mails werden zugeordnet, Anfrage-Flag zurueckgesetzt, Anhaenge verarbeitet; Adresse ohne @ wird uebersprungen")
    void lieferantDomain() {
        Lieferanten l = new Lieferanten();
        l.setId(1L);
        Email e = mail(5L);
        e.setPotentialInquiry(true);
        e.setInquiryScore(80);
        Email inquiry = mail(6L);
        inquiry.setPotentialInquiry(true);
        inquiry.setInquiryScore(50);
        when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(l));
        when(emailRepository.findUnassignedByDomain("muster-gmbh.example.com")).thenReturn(List.of(e));
        when(emailRepository.findInquiriesByDomain("muster-gmbh.example.com")).thenReturn(List.of(inquiry));
        when(emailRepository.findByLieferantIdWithAttachments(1L)).thenReturn(List.of());
        when(attachmentService.processLieferantAttachments(e)).thenReturn(2);

        listener.handleEmailAddressChanged(event(EntityType.LIEFERANT,
                List.of("Info@Muster-GmbH.example.com", "keine-adresse")));

        assertEquals(EmailZuordnungTyp.LIEFERANT, e.getZuordnungTyp());
        assertSame(l, e.getLieferant());
        assertFalse(e.isPotentialInquiry());
        assertEquals(0, e.getInquiryScore());
        assertFalse(inquiry.isPotentialInquiry());
        assertEquals(0, inquiry.getInquiryScore());
        verify(emailRepository).saveAndFlush(e);
        verify(emailRepository).saveAndFlush(inquiry);
        verify(attachmentService).processLieferantAttachments(e);
        verify(emailRepository, never()).findUnassignedByDomain(eq("keine-adresse"));
    }

    @Test
    @DisplayName("Lieferant: Fehler bei Anhangverarbeitung stoppt den Backfill nicht")
    void lieferantAnhangFehler() {
        Lieferanten l = new Lieferanten();
        l.setId(1L);
        Email e1 = mail(5L);
        Email e2 = mail(6L);
        when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(l));
        when(emailRepository.findUnassignedByDomain("example.com")).thenReturn(List.of(e1, e2));
        when(emailRepository.findInquiriesByDomain("example.com")).thenReturn(List.of());
        when(emailRepository.findByLieferantIdWithAttachments(1L)).thenReturn(List.of());
        when(attachmentService.processLieferantAttachments(e1)).thenThrow(new RuntimeException("KI nicht erreichbar"));
        when(attachmentService.processLieferantAttachments(e2)).thenReturn(1);

        assertDoesNotThrow(() -> listener.handleEmailAddressChanged(event(EntityType.LIEFERANT, List.of("a@example.com"))));

        verify(attachmentService).processLieferantAttachments(e1);
        verify(attachmentService).processLieferantAttachments(e2);
        assertSame(l, e2.getLieferant());
    }

    @Test
    @DisplayName("Lieferant: bereits zugeordnete Mails mit unverarbeiteten PDFs werden nachverarbeitet, andere nicht")
    void lieferantBereitsZugeordnet() {
        Lieferanten l = new Lieferanten();
        l.setId(1L);
        when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(l));
        when(emailRepository.findUnassignedByDomain(anyString())).thenReturn(List.of());
        when(emailRepository.findInquiriesByDomain(anyString())).thenReturn(List.of());

        Email ohneAnhang = mail(1L);
        Email mitPdf = mail(2L);
        mitPdf.addAttachment(anhang("Rechnung.PDF", false));
        Email verarbeitet = mail(3L);
        verarbeitet.addAttachment(anhang("rechnung.pdf", true));
        Email nurBild = mail(4L);
        nurBild.addAttachment(anhang("foto.png", false));
        Email ohneName = mail(5L);
        ohneName.addAttachment(anhang(null, false));
        Email fehler = mail(6L);
        fehler.addAttachment(anhang("kaputt.pdf", false));
        when(emailRepository.findByLieferantIdWithAttachments(1L))
                .thenReturn(List.of(ohneAnhang, mitPdf, verarbeitet, nurBild, ohneName, fehler));
        when(attachmentService.processLieferantAttachments(mitPdf)).thenReturn(3);
        when(attachmentService.processLieferantAttachments(fehler)).thenThrow(new RuntimeException("x"));

        listener.handleEmailAddressChanged(event(EntityType.LIEFERANT, List.of("a@example.com")));

        verify(attachmentService).processLieferantAttachments(mitPdf);
        verify(attachmentService).processLieferantAttachments(fehler);
        verify(attachmentService, never()).processLieferantAttachments(verarbeitet);
        verify(attachmentService, never()).processLieferantAttachments(nurBild);
        verify(attachmentService, never()).processLieferantAttachments(ohneName);
        verify(attachmentService, never()).processLieferantAttachments(ohneAnhang);
    }

    private static EmailAttachment anhang(String name, boolean verarbeitet) {
        EmailAttachment a = new EmailAttachment();
        a.setOriginalFilename(name);
        a.setAiProcessed(verarbeitet);
        return a;
    }
}
