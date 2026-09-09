package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterErstellenDto;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class MitarbeiterMenschenServiceTest {
    @Mock MitarbeiterRepository repository;
    @InjectMocks MitarbeiterService service;

    @Test
    void neuerMenschHatDefaultTrueOhneAutomatischeKontoanlage() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.save(null, dto());
        assertThat(result.getArt()).isEqualTo(MitarbeiterArt.MENSCH);
        assertThat(result.getFuehrtZeitkonto()).isTrue();
        verify(repository).save(any(Mitarbeiter.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void neuanlageOhneZeitkontoIstMoeglich() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = dto();
        dto.setFuehrtZeitkonto(false);
        assertThat(service.save(null, dto).getFuehrtZeitkonto()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void toggleWirdVorJederMutationAbgelehnt(boolean bisher) {
        Mitarbeiter m = mensch();
        m.setFuehrtZeitkonto(bisher);
        when(repository.findById(1L)).thenReturn(Optional.of(m));
        var dto = dto();
        dto.setFuehrtZeitkonto(!bisher);
        assertThatThrownBy(() -> service.save(1L, dto))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).contains("Arbeitszeit-Historie");
                });
        assertThat(m.getFuehrtZeitkonto()).isEqualTo(bisher);
        assertThat(m.getVorname()).isEqualTo("Erika");
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void fehlendesUndUnveraendertesFlagBewahrenBestand(boolean bisher) {
        Mitarbeiter m = mensch();
        m.setFuehrtZeitkonto(bisher);
        when(repository.findById(1L)).thenReturn(Optional.of(m));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = dto();
        assertThat(dto.getFuehrtZeitkonto()).isNull();
        assertThat(service.save(1L, dto).getFuehrtZeitkonto()).isEqualTo(bisher);
        dto.setFuehrtZeitkonto(bisher);
        assertThat(service.save(1L, dto).getFuehrtZeitkonto()).isEqualTo(bisher);
    }

    @Test
    void stammZeigtAusgeschiedeneMitTypUndKontoflag() {
        Mitarbeiter m = mensch();
        m.setAktiv(false);
        m.setFuehrtZeitkonto(false);
        when(repository.findMenschen()).thenReturn(List.of(m));
        var result = service.list();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getAktiv()).isFalse();
        assertThat(result.getFirst().getArt()).isEqualTo(MitarbeiterArt.MENSCH);
        assertThat(result.getFirst().getFuehrtZeitkonto()).isFalse();
        verify(repository, never()).findAll();
    }

    @Test
    void systemIstNichtEditierbarAktivierbarOderLoeschbar() {
        Mitarbeiter system = mensch();
        system.setArt(MitarbeiterArt.SYSTEM);
        system.setAktiv(false);
        system.setFuehrtZeitkonto(false);
        when(repository.findById(1L)).thenReturn(Optional.of(system));
        var dto = dto();
        dto.setArt(MitarbeiterArt.MENSCH);
        dto.setAktiv(true);
        dto.setFuehrtZeitkonto(true);
        assertThatThrownBy(() -> service.save(1L, dto)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.generateLoginToken(1L)).isInstanceOf(ResponseStatusException.class);
        assertThat(service.findById(1L)).isEmpty();
        assertThat(system.getArt()).isEqualTo(MitarbeiterArt.SYSTEM);
        assertThat(system.getAktiv()).isFalse();
        assertThat(system.getFuehrtZeitkonto()).isFalse();
        verify(repository, never()).save(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void menschenstammErzeugtKeineSystemeintraege() {
        var dto = dto();
        dto.setArt(MitarbeiterArt.SYSTEM);
        assertThatThrownBy(() -> service.save(null, dto)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    private Mitarbeiter mensch() {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(1L);
        m.setVorname("Erika");
        m.setNachname("Mustermann");
        return m;
    }

    private MitarbeiterErstellenDto dto() {
        var dto = new MitarbeiterErstellenDto();
        dto.setVorname("Max");
        dto.setNachname("Mustermann");
        return dto;
    }
}
