package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BelegServiceMobileOwnershipTest {
    @Mock private BelegRepository belege;
    @Mock private BelegSplitService split;
    @Mock private BelegVorschlagService vorschlaege;
    @Mock private BelegKostenstellenAnteilRepository anteile;
    @InjectMocks private BelegService service;

    @BeforeEach
    void mobileAnmeldung() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new MobilePrincipal(7L), null, List.of()));
        lenient().when(vorschlaege.ermittle(any())).thenReturn(new BelegVorschlagService.Vorschlaege(null, null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private Beleg beleg(Long uploaderId) {
        Beleg beleg = new Beleg();
        beleg.setId(40L);
        if (uploaderId != null) {
            Mitarbeiter uploader = new Mitarbeiter();
            uploader.setId(uploaderId);
            uploader.setVorname("Max");
            uploader.setNachname("Mustermann");
            beleg.setUploadedBy(uploader);
        }
        return beleg;
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {8L})
    void fremdeOderNichtZugeordneteBelegeSindMobilNichtLesbar(Long uploaderId) {
        when(belege.findById(40L)).thenReturn(Optional.of(beleg(uploaderId)));
        assertThatThrownBy(() -> service.getBeleg(40L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.getRawBeleg(40L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {8L})
    void fremdeOderNichtZugeordnetePositionenWerdenVorJederMutationAbgewiesen(Long uploaderId) {
        Beleg fremd = beleg(uploaderId);
        lenient().when(belege.findById(40L)).thenReturn(Optional.of(fremd));
        lenient().when(split.aktualisiereAuswahl(40L, Set.of(12L))).thenReturn(fremd);
        assertThatThrownBy(() -> service.setzePositionsAuswahl(40L, List.of(12L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(split);
    }

    @Test
    void eigeneBelegeBleibenLesbarUndBearbeitbar() {
        Beleg eigener = beleg(7L);
        when(belege.findById(40L)).thenReturn(Optional.of(eigener));
        when(split.aktualisiereAuswahl(40L, Set.of(12L))).thenReturn(eigener);
        assertThat(service.getBeleg(40L).getId()).isEqualTo(40L);
        assertThat(service.getRawBeleg(40L)).isSameAs(eigener);
        assertThat(service.setzePositionsAuswahl(40L, List.of(12L)).getId()).isEqualTo(40L);
        verify(split).aktualisiereAuswahl(40L, Set.of(12L));
    }

    @Test
    void desktopZugriffBleibtFuerAndereUploaderMoeglich() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("desktop-user", null, List.of()));
        Beleg fremd = beleg(8L);
        when(belege.findById(40L)).thenReturn(Optional.of(fremd));
        when(split.aktualisiereAuswahl(40L, Set.of(12L))).thenReturn(fremd);
        assertThat(service.getBeleg(40L).getUploadedById()).isEqualTo(8L);
        assertThat(service.getRawBeleg(40L)).isSameAs(fremd);
        assertThat(service.setzePositionsAuswahl(40L, List.of(12L)).getId()).isEqualTo(40L);
    }
}
