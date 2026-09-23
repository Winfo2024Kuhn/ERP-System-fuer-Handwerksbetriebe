package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufFaelligkeitDto.Faelligkeit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.Gerendert;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufFaelligkeitService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufVorlagenService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class EinkaufFaelligkeitServiceTest {
    private static final Clock TODAY=Clock.fixed(LocalDate.parse("2026-09-23").atStartOfDay().toInstant(ZoneOffset.UTC),ZoneOffset.UTC);

    @Test
    void faelligeListePaginertServerseitigUndZeigtNullfristAlsTerminKlaeren() {
        var em=mock(EntityManager.class);var templates=mock(EinkaufVorlagenService.class);
        Query page=mock(Query.class);Query count=mock(Query.class);
        when(em.createNativeQuery(argThat(sql->sql!=null&&sql.startsWith("SELECT *")))).thenReturn(page);
        when(em.createNativeQuery(argThat(sql->sql!=null&&sql.startsWith("SELECT COUNT")))).thenReturn(count);
        when(page.setParameter(anyString(),any())).thenReturn(page);
        when(count.setParameter(anyString(),any())).thenReturn(count);
        when(page.getResultList()).thenReturn(List.of(
                new Object[]{"BESTELLBESTAETIGUNG",41L,"B-2026-0041",null,null,9L,"Termin klären"},
                new Object[]{"ANFRAGE_ANTWORTFRIST",40L,"PA-2026-0040",3L,java.sql.Date.valueOf("2026-09-22"),9L,"Antwortfrist überschritten"},
                new Object[]{"LIEFERTERMIN",42L,"B-2026-0042",null,java.sql.Date.valueOf("2026-09-24"),9L,"Bestätigter Liefertermin überschritten"}));
        when(count.getSingleResult()).thenReturn(2L);
        var service=new EinkaufFaelligkeitService(em,templates,TODAY);

        var result=service.liste(LocalDate.now(TODAY),9L,PageRequest.of(0,5));

        assertEquals(2,result.getTotalElements());
        assertEquals(2,result.getNumberOfElements());
        Faelligkeit due=result.getContent().get(0);
        assertEquals("BESTELLBESTAETIGUNG",due.typ());
        assertNull(due.frist());
        assertEquals("Termin klären",due.hinweis());
        assertTrue(result.getContent().stream().noneMatch(row->row.typ().equals("LIEFERTERMIN")));
        verify(page).setParameter("heute",LocalDate.parse("2026-09-23"));
        verify(page).setParameter("limit",5);
        verify(page).setParameter("offset",0L);
        verify(em,times(2)).createNativeQuery(anyString());
    }

    @Test
    void versendeteOffeneAnfrageErzeugtNurGerenderteEmpfaengervorschau() {
        var em=mock(EntityManager.class);var templates=mock(EinkaufVorlagenService.class);
        var request=mock(Einkaufsanfrage.class);var participation=mock(AnfrageLieferant.class);
        var revision=mock(AnfrageRevision.class);var template=mock(EmailTextTemplate.class);
        when(em.find(Einkaufsanfrage.class,10L)).thenReturn(request);when(em.find(AnfrageLieferant.class,20L)).thenReturn(participation);
        when(request.getAktuelleRevision()).thenReturn(revision);when(request.getPaNummer()).thenReturn("PA-26-10");
        when(revision.getId()).thenReturn(30L);when(revision.getAntwortfrist()).thenReturn(LocalDate.parse("2026-09-22"));
        when(revision.getLiefertermin()).thenReturn(LocalDate.parse("2026-10-05"));when(revision.getPositionen()).thenReturn(List.of());
        when(participation.getRevision()).thenReturn(revision);when(participation.getStatus()).thenReturn("VERSENDET");
        when(participation.getVersandAnnahmeereignis()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(participation.getAntwortAm()).thenReturn(null);when(participation.getKontakt()).thenReturn(
                new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot(5L,6L,"Dummy Stahl","test@example.com","Max Mustermann","Herr",null));
        when(participation.getRueckmeldecode()).thenReturn("DUMMY-CODE");
        var templateQuery=mockTemplateQuery(template);
        when(em.createQuery(anyString(),eq(EmailTextTemplate.class))).thenReturn(templateQuery);
        when(template.getId()).thenReturn(4L);
        when(templates.rendern(eq(4L),any())).thenReturn(new Gerendert(4L,2,"Rückmeldung zu PA-26-10","<p>Bitte Rückmeldung.</p>","dummy-hash"));
        var service=new EinkaufFaelligkeitService(em,templates,TODAY);

        var draft=service.nachfrage("ANFRAGE_ANTWORTFRIST",10L,20L);

        assertEquals("test@example.com",draft.empfaenger());
        assertEquals("Rückmeldung zu PA-26-10",draft.subject());
        verify(templates).rendern(eq(4L),any());
        verifyNoMoreInteractions(templates);
        verify(em,never()).createNativeQuery(contains("outbox"));
    }

    private static jakarta.persistence.TypedQuery<EmailTextTemplate> mockTemplateQuery(EmailTextTemplate template){
        var query=mock(jakarta.persistence.TypedQuery.class);
        when(query.setParameter(anyString(),any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultStream()).thenReturn(Stream.of(template));
        return query;
    }
}
