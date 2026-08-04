package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LieferantArtikelpreisServiceTest {

    private static final Long LIEFERANT_ID = 7L;
    private static final Long ARTIKEL_ID = 42L;

    @Mock private LieferantenArtikelPreiseRepository artikelPreiseRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private ArtikelRepository artikelRepository;
    @Mock private LieferantArtikelpreisMapper mapper;

    private LieferantArtikelpreisService service;

    @BeforeEach
    void setUp() {
        service = new LieferantArtikelpreisService(artikelPreiseRepository, lieferantenRepository,
                artikelRepository, mapper);
    }

    private Lieferanten erstelleLieferant() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(LIEFERANT_ID);
        lieferant.setLieferantenname("Muster Stahlhandel GmbH");
        return lieferant;
    }

    private Artikel erstelleArtikel() {
        Artikel artikel = new Artikel();
        artikel.setId(ARTIKEL_ID);
        artikel.setProduktname("Blech 0,75 mm");
        return artikel;
    }

    /**
     * Das Massen-Update leert den Persistence Context. Werden Artikel und
     * Lieferant vorher geladen, sind sie danach abgehaengt und der Mapper laeuft
     * beim Werkstoff in eine LazyInitializationException - der Preis liesse sich
     * nicht mehr speichern. Deshalb ist die Reihenfolge hier festgeschrieben.
     */
    @Test
    void ladeArtikelUndLieferantErstNachDemMarkierenDerAltenStaende() {
        when(artikelPreiseRepository.findByArtikel_IdAndLieferant_IdAndAktuellTrue(ARTIKEL_ID, LIEFERANT_ID))
                .thenReturn(Optional.empty());
        when(lieferantenRepository.findById(LIEFERANT_ID)).thenReturn(Optional.of(erstelleLieferant()));
        when(artikelRepository.findById(ARTIKEL_ID)).thenReturn(Optional.of(erstelleArtikel()));
        when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                .thenAnswer(aufruf -> aufruf.getArgument(0));
        when(mapper.toDto(any(LieferantenArtikelPreise.class))).thenReturn(new LieferantArtikelpreisDto());

        Optional<LieferantArtikelpreisDto> ergebnis = service.anlegen(LIEFERANT_ID, ARTIKEL_ID,
                new BigDecimal("12.50"), "ABC-123");

        assertThat(ergebnis).isPresent();
        InOrder reihenfolge = inOrder(artikelPreiseRepository, lieferantenRepository, artikelRepository);
        reihenfolge.verify(artikelPreiseRepository).markiereBisherigeAlsVeraltet(ARTIKEL_ID, LIEFERANT_ID);
        reihenfolge.verify(lieferantenRepository).findById(LIEFERANT_ID);
        reihenfolge.verify(artikelRepository).findById(ARTIKEL_ID);
    }

    @Test
    void schreibeNeuenPreisstandMitDenUebergebenenWerten() {
        when(artikelPreiseRepository.findByArtikel_IdAndLieferant_IdAndAktuellTrue(ARTIKEL_ID, LIEFERANT_ID))
                .thenReturn(Optional.empty());
        when(lieferantenRepository.findById(LIEFERANT_ID)).thenReturn(Optional.of(erstelleLieferant()));
        when(artikelRepository.findById(ARTIKEL_ID)).thenReturn(Optional.of(erstelleArtikel()));
        when(artikelPreiseRepository.save(any(LieferantenArtikelPreise.class)))
                .thenAnswer(aufruf -> aufruf.getArgument(0));
        when(mapper.toDto(any(LieferantenArtikelPreise.class))).thenReturn(new LieferantArtikelpreisDto());

        service.anlegen(LIEFERANT_ID, ARTIKEL_ID, new BigDecimal("12.50"), "  ABC-123  ");

        org.mockito.ArgumentCaptor<LieferantenArtikelPreise> gespeichert =
                org.mockito.ArgumentCaptor.forClass(LieferantenArtikelPreise.class);
        verify(artikelPreiseRepository).save(gespeichert.capture());
        assertThat(gespeichert.getValue().getPreis()).isEqualByComparingTo("12.50");
        assertThat(gespeichert.getValue().getExterneArtikelnummer()).isEqualTo("ABC-123");
        assertThat(gespeichert.getValue().isAktuell()).isTrue();
        assertThat(gespeichert.getValue().getArtikel().getId()).isEqualTo(ARTIKEL_ID);
        assertThat(gespeichert.getValue().getLieferant().getId()).isEqualTo(LIEFERANT_ID);
    }

    /** Gleicher Preis heisst kein neues Preisereignis - sonst waechst der Verlauf sinnlos. */
    @Test
    void schreibeKeinenNeuenStandWennDerPreisGleichBleibt() {
        LieferantenArtikelPreise bisher = new LieferantenArtikelPreise();
        bisher.setPreis(new BigDecimal("12.5000"));
        bisher.setAktuell(true);
        when(artikelPreiseRepository.findByArtikel_IdAndLieferant_IdAndAktuellTrue(ARTIKEL_ID, LIEFERANT_ID))
                .thenReturn(Optional.of(bisher));
        when(artikelPreiseRepository.save(bisher)).thenReturn(bisher);
        when(mapper.toDto(bisher)).thenReturn(new LieferantArtikelpreisDto());

        service.aktualisiere(LIEFERANT_ID, ARTIKEL_ID, new BigDecimal("12.50"), "ABC-123");

        verify(artikelPreiseRepository, never()).markiereBisherigeAlsVeraltet(ARTIKEL_ID, LIEFERANT_ID);
        assertThat(bisher.getExterneArtikelnummer()).isEqualTo("ABC-123");
    }

    @Test
    void speichereNichtsWennDerArtikelUnbekanntIst() {
        when(artikelPreiseRepository.findByArtikel_IdAndLieferant_IdAndAktuellTrue(ARTIKEL_ID, LIEFERANT_ID))
                .thenReturn(Optional.empty());
        when(lieferantenRepository.findById(LIEFERANT_ID)).thenReturn(Optional.of(erstelleLieferant()));
        when(artikelRepository.findById(ARTIKEL_ID)).thenReturn(Optional.empty());

        Optional<LieferantArtikelpreisDto> ergebnis = service.anlegen(LIEFERANT_ID, ARTIKEL_ID,
                new BigDecimal("12.50"), "ABC-123");

        assertThat(ergebnis).isEmpty();
        verify(artikelPreiseRepository, never()).save(any(LieferantenArtikelPreise.class));
    }

    @Test
    void speichereNichtsOhneIds() {
        assertThat(service.anlegen(null, ARTIKEL_ID, new BigDecimal("12.50"), "ABC-123")).isEmpty();
        assertThat(service.anlegen(LIEFERANT_ID, null, new BigDecimal("12.50"), "ABC-123")).isEmpty();
        verify(artikelPreiseRepository, never()).save(any(LieferantenArtikelPreise.class));
    }
}
