package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageErstellenDto;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Aus Datenschutzgründen gesperrte Kunden (Issue #264): keine neue Anfrage und kein Wechsel auf
 * sie; die aufbewahrten Anfragen des Kunden bleiben bearbeitbar.
 */
class AnfrageServiceDatenschutzTest {

    private final AnfrageRepository anfrageRepository = mock(AnfrageRepository.class);
    private final KundeRepository kundeRepository = mock(KundeRepository.class);
    private final AnfrageService service = new AnfrageService(anfrageRepository, mock(DateiSpeicherService.class),
            mock(AnfrageDokumentRepository.class), kundeRepository,
            mock(org.example.kalkulationsprogramm.repository.EmailRepository.class),
            mock(org.example.kalkulationsprogramm.repository.ProjektRepository.class),
            mock(org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository.class),
            null, mock(org.springframework.context.ApplicationEventPublisher.class),
            mock(AusgangsGeschaeftsDokumentService.class), mock(DokumentFreigabeService.class),
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private Kunde gesperrterKunde() {
        Kunde kunde = new Kunde();
        kunde.setId(7L);
        kunde.setName("Max Mustermann");
        kunde.setGesperrtAm(LocalDateTime.of(2026, 10, 20, 9, 0));
        when(kundeRepository.findById(7L)).thenReturn(Optional.of(kunde));
        return kunde;
    }

    private static AnfrageErstellenDto mitKunde(long kundeId) {
        AnfrageErstellenDto dto = new AnfrageErstellenDto();
        dto.setKundenId(kundeId);
        dto.setBauvorhaben("Geländer Musterweg");
        return dto;
    }

    @Test
    void neueAnfrageFuerGesperrtenKundenWirdAbgelehnt() {
        gesperrterKunde();

        assertThatThrownBy(() -> service.erstelleAnfrage(mitKunde(7L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        verify(anfrageRepository, never()).save(any(Anfrage.class));
    }

    @Test
    void aufbewahrteAnfrageDesGesperrtenKundenBleibtBearbeitbar() {
        Kunde kunde = gesperrterKunde();
        Anfrage anfrage = new Anfrage();
        anfrage.setId(42L);
        anfrage.setKunde(kunde);
        when(anfrageRepository.findById(42L)).thenReturn(Optional.of(anfrage));

        AnfrageErstellenDto dto = mitKunde(7L);
        dto.setBauvorhaben("Geländer Musterweg – Rückfrage Finanzamt");
        service.aktualisiereAnfrage(42L, dto);

        assertThat(anfrage.getBauvorhaben()).isEqualTo("Geländer Musterweg – Rückfrage Finanzamt");
    }

    @Test
    void wechselAufGesperrtenKundenWirdAbgelehnt() {
        gesperrterKunde();
        Kunde anderer = new Kunde();
        anderer.setId(8L);
        Anfrage anfrage = new Anfrage();
        anfrage.setId(43L);
        anfrage.setKunde(anderer);
        when(anfrageRepository.findById(43L)).thenReturn(Optional.of(anfrage));

        assertThatThrownBy(() -> service.aktualisiereAnfrage(43L, mitKunde(7L)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(anfrage.getKunde()).isSameAs(anderer);
    }
}
