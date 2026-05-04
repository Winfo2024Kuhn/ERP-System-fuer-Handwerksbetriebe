package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageFunnelRequestDto;
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AnfrageFunnelServiceTest {

    private KundeRepository kundeRepository;
    private AnfrageRepository anfrageRepository;
    private AnfrageNotizRepository anfrageNotizRepository;
    private MitarbeiterRepository mitarbeiterRepository;
    private KundennummerService kundennummerService;
    private DateiSpeicherService dateiSpeicherService;

    private AnfrageFunnelService service;

    private Mitarbeiter systemMitarbeiter;

    @BeforeEach
    void setUp() {
        kundeRepository = mock(KundeRepository.class);
        anfrageRepository = mock(AnfrageRepository.class);
        anfrageNotizRepository = mock(AnfrageNotizRepository.class);
        mitarbeiterRepository = mock(MitarbeiterRepository.class);
        kundennummerService = mock(KundennummerService.class);
        dateiSpeicherService = mock(DateiSpeicherService.class);

        service = new AnfrageFunnelService(
                kundeRepository, anfrageRepository, anfrageNotizRepository,
                mitarbeiterRepository, kundennummerService, dateiSpeicherService
        );

        systemMitarbeiter = new Mitarbeiter();
        systemMitarbeiter.setId(99L);
        systemMitarbeiter.setVorname("System");
        systemMitarbeiter.setNachname("Webseite");

        given(mitarbeiterRepository.findByLoginToken(AnfrageFunnelService.SYSTEM_MITARBEITER_TOKEN))
                .willReturn(Optional.of(systemMitarbeiter));
        given(anfrageRepository.save(any(Anfrage.class)))
                .willAnswer(inv -> {
                    Anfrage a = inv.getArgument(0);
                    a.setId(1L);
                    return a;
                });
        given(kundeRepository.save(any(Kunde.class))).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void legtNeuenKundenAnWennEMailUnbekannt() {
        given(kundeRepository.findByKundenEmailIgnoreCase("max@example.de")).willReturn(List.of());
        given(kundennummerService.reserviereNaechsteKundennummer()).willReturn("1042");

        AnfrageFunnelRequestDto dto = baseDto();

        Anfrage anfrage = service.verarbeiteFunnelAnfrage(dto, List.of());

        ArgumentCaptor<Kunde> kundeCaptor = ArgumentCaptor.forClass(Kunde.class);
        verify(kundeRepository).save(kundeCaptor.capture());
        Kunde gespeicherter = kundeCaptor.getValue();
        assertThat(gespeicherter.getKundennummer()).isEqualTo("1042");
        assertThat(gespeicherter.getName()).isEqualTo("Max Mustermann");
        assertThat(gespeicherter.getKundenEmails()).containsExactly("max@example.de");
        assertThat(gespeicherter.getStrasse()).isEqualTo("Kleistraße 11");
        assertThat(gespeicherter.getPlz()).isEqualTo("97072");
        assertThat(gespeicherter.getOrt()).isEqualTo("Würzburg");
        assertThat(anfrage.getId()).isEqualTo(1L);
    }

    @Test
    void verwendetBestehendenKundenBeiBekannterEMail() {
        Kunde bestehend = new Kunde();
        bestehend.setId(7L);
        bestehend.setName("Max Mustermann");
        bestehend.setKundennummer("1000");
        bestehend.setKundenEmails(new java.util.ArrayList<>(List.of("max@example.de")));
        given(kundeRepository.findByKundenEmailIgnoreCase("max@example.de")).willReturn(List.of(bestehend));

        AnfrageFunnelRequestDto dto = baseDto();

        service.verarbeiteFunnelAnfrage(dto, List.of());

        verify(kundennummerService, never()).reserviereNaechsteKundennummer();
    }

    @Test
    void formattiertBauvorhabenAusServiceUndProjektarten() {
        given(kundeRepository.findByKundenEmailIgnoreCase(any())).willReturn(List.of());
        given(kundennummerService.reserviereNaechsteKundennummer()).willReturn("1042");

        AnfrageFunnelRequestDto dto = baseDto();
        dto.setServiceTyp("Neubau");
        dto.setProjektarten(List.of("Wohnhaus", "Sanierung", "Dachsanierung"));

        ArgumentCaptor<Anfrage> captor = ArgumentCaptor.forClass(Anfrage.class);
        service.verarbeiteFunnelAnfrage(dto, List.of());

        verify(anfrageRepository).save(captor.capture());
        Anfrage a = captor.getValue();
        assertThat(a.getBauvorhaben()).isEqualTo("Neubau - Wohnhaus, Sanierung, Dachsanierung");
        assertThat(a.getKurzbeschreibung()).startsWith("Neubau - Wohnhaus, Sanierung, Dachsanierung");
        assertThat(a.getKurzbeschreibung()).contains("asfdds");
        assertThat(a.getProjektStrasse()).isEqualTo("Kleistraße 11");
        assertThat(a.getProjektPlz()).isEqualTo("97072");
        assertThat(a.getProjektOrt()).isEqualTo("Würzburg");
        assertThat(a.getKundenEmails()).containsExactly("max@example.de");
    }

    @Test
    void legtNotizMitSystemMitarbeiterUndAnfrageTextAn() {
        given(kundeRepository.findByKundenEmailIgnoreCase(any())).willReturn(List.of());
        given(kundennummerService.reserviereNaechsteKundennummer()).willReturn("1042");

        AnfrageFunnelRequestDto dto = baseDto();

        service.verarbeiteFunnelAnfrage(dto, List.of());

        ArgumentCaptor<AnfrageNotiz> captor = ArgumentCaptor.forClass(AnfrageNotiz.class);
        verify(anfrageNotizRepository).save(captor.capture());
        AnfrageNotiz notiz = captor.getValue();
        assertThat(notiz.getMitarbeiter()).isSameAs(systemMitarbeiter);
        assertThat(notiz.getNotiz()).contains("Anfrage über Webseite");
        assertThat(notiz.getNotiz()).contains("max@example.de");
        assertThat(notiz.getNotiz()).contains("Service: Neubau");
        assertThat(notiz.getNotiz()).contains("Datenschutz akzeptiert");
        assertThat(notiz.getBilder()).isEmpty();
    }

    @Test
    void verlangtSystemMitarbeiterAusMigration() {
        given(mitarbeiterRepository.findByLoginToken(AnfrageFunnelService.SYSTEM_MITARBEITER_TOKEN))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.verarbeiteFunnelAnfrage(baseDto(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("System-Mitarbeiter");
    }

    private AnfrageFunnelRequestDto baseDto() {
        AnfrageFunnelRequestDto dto = new AnfrageFunnelRequestDto();
        dto.setServiceTyp("Neubau");
        dto.setProjektarten(List.of("Wohnhaus"));
        dto.setNachricht("asfdds");
        dto.setVorname("Max");
        dto.setNachname("Mustermann");
        dto.setEmail("max@example.de");
        dto.setTelefon("093692323");
        dto.setProjektAnschrift("Kleistraße 11, 97072 Würzburg");
        dto.setDatenschutzAkzeptiert(true);
        dto.setConsentIp("1.2.3.4");
        dto.setDatenschutzVersion("1.0");
        return dto;
    }
}
