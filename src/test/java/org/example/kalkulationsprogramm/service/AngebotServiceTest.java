package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Angebot;
import org.example.kalkulationsprogramm.domain.AngebotDokument;
import org.example.kalkulationsprogramm.domain.AngebotGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Angebot.AngebotErstellenDto;
import org.example.kalkulationsprogramm.dto.Angebot.AngebotResponseDto;
import org.example.kalkulationsprogramm.repository.AngebotDokumentRepository;
import org.example.kalkulationsprogramm.repository.AngebotRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AngebotServiceTest {

    @Test
    void erstelltAngebotUndGibtDto() {
        AngebotRepository angebotRepository = mock(AngebotRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AngebotDokumentRepository angebotDokumentRepository = mock(AngebotDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AngebotService service = new AngebotService(angebotRepository, dateiSpeicherService, angebotDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        when(angebotRepository.save(any(Angebot.class))).thenAnswer(invocation -> {
            Angebot a = invocation.getArgument(0);
            a.setId(42L);
            return a;
        });

        AngebotErstellenDto dto = new AngebotErstellenDto();
        dto.setBauvorhaben("Bau?");
        dto.setKundenId(99L);
        dto.setAnlegedatum(LocalDate.of(2024, 1, 2));

        Kunde k = new Kunde();
        k.setId(99L);
        k.setName("Test");
        when(kundeRepository.findById(99L)).thenReturn(Optional.of(k));
        AngebotResponseDto result = service.erstelleAngebot(dto);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getBauvorhaben()).isEqualTo("Bau");
        assertThat(result.getKundenName()).isEqualTo("Test");
        assertThat(result.getAnlegedatum()).isEqualTo(LocalDate.of(2024, 1, 2));

        verify(angebotRepository, atLeastOnce()).save(any(Angebot.class));
        // keine weiteren strikten Erwartungen an angebotDokumentRepository in diesem
        // Test
    }

    @Test
    void findeDtoSetztAngebotsnummerAusGeschaeftsdokument() {
        AngebotRepository angebotRepository = mock(AngebotRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AngebotDokumentRepository angebotDokumentRepository = mock(AngebotDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AngebotService service = new AngebotService(angebotRepository, dateiSpeicherService, angebotDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        Angebot angebot = new Angebot();
        angebot.setId(5L);
        Kunde k = new Kunde();
        k.setName("Kunde");
        angebot.setKunde(k);
        when(angebotRepository.findById(5L)).thenReturn(Optional.of(angebot));

        AngebotGeschaeftsdokument doc = new AngebotGeschaeftsdokument();
        doc.setId(11L);
        doc.setGeschaeftsdokumentart("Angebot");
        doc.setDokumentid("ANG-123");
        when(angebotDokumentRepository.findByAngebotId(5L)).thenReturn(List.of(doc));

        AngebotResponseDto dto = service.findeDto(5L);
        assertThat(dto).isNotNull();
        assertThat(dto.getAngebotsnummer()).isEqualTo("ANG-123");
    }

    @Test
    void loescheEntferntDateienUndAktualisiertProjekt() {
        AngebotRepository angebotRepository = mock(AngebotRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AngebotDokumentRepository angebotDokumentRepository = mock(AngebotDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AngebotService service = new AngebotService(angebotRepository, dateiSpeicherService, angebotDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        Projekt projekt = new Projekt();
        projekt.setId(9L);

        Angebot angebot = new Angebot();
        angebot.setId(7L);
        angebot.setProjekt(projekt);
        when(angebotRepository.findById(7L)).thenReturn(Optional.of(angebot));

        AngebotDokument d1 = new AngebotDokument();
        d1.setId(1L);
        AngebotDokument d2 = new AngebotDokument();
        d2.setId(2L);
        when(angebotDokumentRepository.findByAngebotId(7L)).thenReturn(List.of(d1, d2));

        boolean result = service.loesche(7L);

        assertThat(result).isTrue();
        verify(dateiSpeicherService).loescheAngebotDatei(1L);
        verify(dateiSpeicherService).loescheAngebotDatei(2L);
        verify(angebotRepository).delete(angebot);
        verify(dateiSpeicherService).aktualisiereProjektFinanzstatus(9L);
    }

    @Test
    void loescheGibtFalseZurueckWennNichtGefunden() {
        AngebotRepository angebotRepository = mock(AngebotRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AngebotDokumentRepository angebotDokumentRepository = mock(AngebotDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AngebotService service = new AngebotService(angebotRepository, dateiSpeicherService, angebotDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        when(angebotRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = service.loesche(999L);
        assertThat(result).isFalse();
        verifyNoInteractions(dateiSpeicherService, angebotDokumentRepository);
    }

    @Test
    void alleFiltertAngeboteMitProjektRaus() {
        AngebotRepository angebotRepository = mock(AngebotRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AngebotDokumentRepository angebotDokumentRepository = mock(AngebotDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AngebotService service = new AngebotService(angebotRepository, dateiSpeicherService, angebotDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        Angebot a1 = new Angebot();
        a1.setId(1L);
        Kunde k1 = new Kunde();
        k1.setName("A1");
        a1.setKunde(k1);

        Angebot a2 = new Angebot();
        a2.setId(2L);
        Kunde k2 = new Kunde();
        k2.setName("A2");
        a2.setKunde(k2);
        Projekt p = new Projekt();
        p.setId(10L);
        a2.setProjekt(p);

        when(angebotRepository.findAllWithKundenEmails()).thenReturn(List.of(a1, a2));

        List<AngebotResponseDto> result = service.alle();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getKundenName()).isEqualTo("A1");
    }

    @Test
    void erstelltAngebotOhneKundenIdOhneFehler() {
        AngebotRepository angebotRepository = mock(AngebotRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AngebotDokumentRepository angebotDokumentRepository = mock(AngebotDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AngebotService service = new AngebotService(angebotRepository, dateiSpeicherService, angebotDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        when(angebotRepository.save(any(Angebot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kundeRepository.findById(anyLong())).thenReturn(java.util.Optional.empty());

        AngebotErstellenDto dto = new AngebotErstellenDto();
        dto.setBauvorhaben("BV");
        dto.setKunde(null); // keine Pflicht
        dto.setKundenId(null);

        AngebotResponseDto response = service.erstelleAngebot(dto);

        assertThat(response).isNotNull();
        verify(angebotRepository).save(any(Angebot.class));
        verify(kundeRepository, never()).findById(anyLong());
    }
}
