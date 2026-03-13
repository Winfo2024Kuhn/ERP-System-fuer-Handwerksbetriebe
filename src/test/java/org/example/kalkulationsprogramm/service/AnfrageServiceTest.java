package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageErstellenDto;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnfrageServiceTest {

    @Test
    void erstelltAnfrageUndGibtDto() {
        AnfrageRepository anfrageRepository = mock(AnfrageRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AnfrageDokumentRepository anfrageDokumentRepository = mock(AnfrageDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AnfrageService service = new AnfrageService(anfrageRepository, dateiSpeicherService, anfrageDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        when(anfrageRepository.save(any(Anfrage.class))).thenAnswer(invocation -> {
            Anfrage a = invocation.getArgument(0);
            a.setId(42L);
            return a;
        });

        AnfrageErstellenDto dto = new AnfrageErstellenDto();
        dto.setBauvorhaben("Bau?");
        dto.setKundenId(99L);
        dto.setAnlegedatum(LocalDate.of(2024, 1, 2));

        Kunde k = new Kunde();
        k.setId(99L);
        k.setName("Test");
        when(kundeRepository.findById(99L)).thenReturn(Optional.of(k));
        AnfrageResponseDto result = service.erstelleAnfrage(dto);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getBauvorhaben()).isEqualTo("Bau");
        assertThat(result.getKundenName()).isEqualTo("Test");
        assertThat(result.getAnlegedatum()).isEqualTo(LocalDate.of(2024, 1, 2));

        verify(anfrageRepository, atLeastOnce()).save(any(Anfrage.class));
        // keine weiteren strikten Erwartungen an anfrageDokumentRepository in diesem
        // Test
    }

    @Test
    void findeDtoSetztAnfragesnummerAusGeschaeftsdokument() {
        AnfrageRepository anfrageRepository = mock(AnfrageRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AnfrageDokumentRepository anfrageDokumentRepository = mock(AnfrageDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AnfrageService service = new AnfrageService(anfrageRepository, dateiSpeicherService, anfrageDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        Anfrage anfrage = new Anfrage();
        anfrage.setId(5L);
        Kunde k = new Kunde();
        k.setName("Kunde");
        anfrage.setKunde(k);
        when(anfrageRepository.findById(5L)).thenReturn(Optional.of(anfrage));

        AnfrageGeschaeftsdokument doc = new AnfrageGeschaeftsdokument();
        doc.setId(11L);
        doc.setGeschaeftsdokumentart("Angebot");
        doc.setDokumentid("ANG-123");
        when(anfrageDokumentRepository.findByAnfrageId(5L)).thenReturn(List.of(doc));

        AnfrageResponseDto dto = service.findeDto(5L);
        assertThat(dto).isNotNull();
        assertThat(dto.getAnfragesnummer()).isEqualTo("ANG-123");
    }

    @Test
    void loescheEntferntDateienUndAktualisiertProjekt() {
        AnfrageRepository anfrageRepository = mock(AnfrageRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AnfrageDokumentRepository anfrageDokumentRepository = mock(AnfrageDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AnfrageService service = new AnfrageService(anfrageRepository, dateiSpeicherService, anfrageDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        Projekt projekt = new Projekt();
        projekt.setId(9L);

        Anfrage anfrage = new Anfrage();
        anfrage.setId(7L);
        anfrage.setProjekt(projekt);
        when(anfrageRepository.findById(7L)).thenReturn(Optional.of(anfrage));

        AnfrageDokument d1 = new AnfrageDokument();
        d1.setId(1L);
        AnfrageDokument d2 = new AnfrageDokument();
        d2.setId(2L);
        when(anfrageDokumentRepository.findByAnfrageId(7L)).thenReturn(List.of(d1, d2));

        boolean result = service.loesche(7L);

        assertThat(result).isTrue();
        verify(dateiSpeicherService).loescheAnfrageDatei(1L);
        verify(dateiSpeicherService).loescheAnfrageDatei(2L);
        verify(anfrageRepository).delete(anfrage);
        verify(dateiSpeicherService).aktualisiereProjektFinanzstatus(9L);
    }

    @Test
    void loescheGibtFalseZurueckWennNichtGefunden() {
        AnfrageRepository anfrageRepository = mock(AnfrageRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AnfrageDokumentRepository anfrageDokumentRepository = mock(AnfrageDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AnfrageService service = new AnfrageService(anfrageRepository, dateiSpeicherService, anfrageDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        when(anfrageRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = service.loesche(999L);
        assertThat(result).isFalse();
        verifyNoInteractions(dateiSpeicherService, anfrageDokumentRepository);
    }

    @Test
    void alleFiltertAnfragenMitProjektRaus() {
        AnfrageRepository anfrageRepository = mock(AnfrageRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AnfrageDokumentRepository anfrageDokumentRepository = mock(AnfrageDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AnfrageService service = new AnfrageService(anfrageRepository, dateiSpeicherService, anfrageDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        Anfrage a1 = new Anfrage();
        a1.setId(1L);
        Kunde k1 = new Kunde();
        k1.setName("A1");
        a1.setKunde(k1);

        Anfrage a2 = new Anfrage();
        a2.setId(2L);
        Kunde k2 = new Kunde();
        k2.setName("A2");
        a2.setKunde(k2);
        Projekt p = new Projekt();
        p.setId(10L);
        a2.setProjekt(p);

        when(anfrageRepository.findAll()).thenReturn(List.of(a1, a2));

        List<AnfrageResponseDto> result = service.alle();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getKundenName()).isEqualTo("A1");
    }

    @Test
    void erstelltAnfrageOhneKundenIdOhneFehler() {
        AnfrageRepository anfrageRepository = mock(AnfrageRepository.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        AnfrageDokumentRepository anfrageDokumentRepository = mock(AnfrageDokumentRepository.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        AnfrageService service = new AnfrageService(anfrageRepository, dateiSpeicherService, anfrageDokumentRepository,
                kundeRepository, null, eventPublisher, ausgangsGeschaeftsDokumentService);

        when(anfrageRepository.save(any(Anfrage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kundeRepository.findById(anyLong())).thenReturn(java.util.Optional.empty());

        AnfrageErstellenDto dto = new AnfrageErstellenDto();
        dto.setBauvorhaben("BV");
        dto.setKunde(null); // keine Pflicht
        dto.setKundenId(null);

        AnfrageResponseDto response = service.erstelleAnfrage(dto);

        assertThat(response).isNotNull();
        verify(anfrageRepository).save(any(Anfrage.class));
        verify(kundeRepository, never()).findById(anyLong());
    }
}
