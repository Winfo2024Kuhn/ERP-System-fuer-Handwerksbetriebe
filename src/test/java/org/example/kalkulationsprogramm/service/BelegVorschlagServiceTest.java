package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BelegVorschlagServiceTest {
    @Mock private BelegRepository belegRepository;
    @Mock private SachkontoRepository sachkontoRepository;
    @Mock private KostenstelleRepository kostenstelleRepository;
    private BelegVorschlagService service;
    private Beleg beleg;
    private Lieferanten lieferant;

    @BeforeEach
    void setUp() {
        service = new BelegVorschlagService(belegRepository, sachkontoRepository, kostenstelleRepository);
        beleg = new Beleg();
        beleg.setId(42L);
        lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Musterbaustoffe GmbH");
        beleg.setLieferant(lieferant);
    }

    @Test
    void kiHatVorrangVorStandardUndHistorie() {
        var konto = konto(1L, true);
        var kostenstelle = kostenstelle(2L, true);
        beleg.setKiVorgeschlagenerSachkontoId(1L);
        beleg.setKiVorgeschlagenerKostenstelleId(2L);
        beleg.setKiKostenkontoBegruendung("Material für die Baustelle");
        lieferant.setStandardKostenstelle(kostenstelle(3L, true));
        when(sachkontoRepository.findById(1L)).thenReturn(Optional.of(konto));
        when(kostenstelleRepository.findById(2L)).thenReturn(Optional.of(kostenstelle));

        var vorschlaege = service.ermittle(beleg);

        assertThat(vorschlaege.sachkonto()).isNotNull();
        assertThat(vorschlaege.sachkonto().id()).isEqualTo(1L);
        assertThat(vorschlaege.sachkonto().quelle()).isEqualTo(BelegVorschlagService.Quelle.KI);
        assertThat(vorschlaege.sachkonto().nummer()).isEqualTo("4980");
        assertThat(vorschlaege.sachkonto().begruendung()).contains("Die KI schlägt das vor", "Material");
        assertThat(vorschlaege.kostenstelle()).isNotNull();
        assertThat(vorschlaege.kostenstelle().id()).isEqualTo(2L);
        assertThat(vorschlaege.kostenstelle().quelle()).isEqualTo(BelegVorschlagService.Quelle.KI);
        verifyNoInteractions(belegRepository);
    }

    @Test
    void standardKostenstelleHatVorrangVorHistorieOhneKi() {
        lieferant.setStandardKostenstelle(kostenstelle(3L, true));
        historie(List.of(historisch(konto(8L, true), kostenstelle(9L, true))));
        var vorschlaege = service.ermittle(beleg);
        assertThat(vorschlaege.sachkonto()).isNotNull();
        assertThat(vorschlaege.sachkonto().id()).isEqualTo(8L);
        assertThat(vorschlaege.sachkonto().quelle()).isEqualTo(BelegVorschlagService.Quelle.HISTORIE);
        assertThat(vorschlaege.kostenstelle()).isNotNull();
        assertThat(vorschlaege.kostenstelle().id()).isEqualTo(3L);
        assertThat(vorschlaege.kostenstelle().quelle()).isEqualTo(BelegVorschlagService.Quelle.LIEFERANT_STANDARD);
        assertThat(vorschlaege.kostenstelle().begruendung()).contains("Musterbaustoffe GmbH", "Standard");
    }

    @Test
    void historieZaehltIdsUndLiefertHaeufigsteZuordnungAusLetztenZwanzig() {
        historie(List.of(historisch(konto(1L, true), kostenstelle(8L, true)),
                historisch(konto(2L, true), kostenstelle(9L, true)),
                historisch(konto(2L, true), kostenstelle(9L, true))));
        var vorschlaege = service.ermittle(beleg);
        assertThat(vorschlaege.sachkonto()).isNotNull();
        assertThat(vorschlaege.sachkonto().id()).isEqualTo(2L);
        assertThat(vorschlaege.sachkonto().begruendung())
                .isEqualTo("Bei Musterbaustoffe GmbH hast du zuletzt 2-mal dieses Konto genommen.");
        assertThat(vorschlaege.kostenstelle()).isNotNull();
        assertThat(vorschlaege.kostenstelle().id()).isEqualTo(9L);
        assertThat(vorschlaege.kostenstelle().quelle()).isEqualTo(BelegVorschlagService.Quelle.HISTORIE);
        assertThat(vorschlaege.kostenstelle().nummer()).isNull();
        verify(belegRepository, times(1)).findLetzteGepruefteByLieferant(7L, 42L, PageRequest.of(0, 20));
    }

    @Test
    void beiGleichstandGewinntDieJuengsteZuordnung() {
        historie(List.of(historisch(konto(2L, true), kostenstelle(9L, true)),
                historisch(konto(1L, true), kostenstelle(8L, true)),
                historisch(konto(1L, true), kostenstelle(8L, true)),
                historisch(konto(2L, true), kostenstelle(9L, true))));
        var vorschlaege = service.ermittle(beleg);
        assertThat(vorschlaege.sachkonto()).isNotNull();
        assertThat(vorschlaege.sachkonto().id()).isEqualTo(2L);
        assertThat(vorschlaege.kostenstelle()).isNotNull();
        assertThat(vorschlaege.kostenstelle().id()).isEqualTo(9L);
    }

    @Test
    void deaktiviertesKiKontoFaelltAufAktiveHistorieZurueck() {
        beleg.setKiVorgeschlagenerSachkontoId(1L);
        when(sachkontoRepository.findById(1L)).thenReturn(Optional.of(konto(1L, false)));
        historie(List.of(historisch(konto(1L, false), null), historisch(konto(2L, true), null)));
        var vorschlaege = service.ermittle(beleg);
        assertThat(vorschlaege.sachkonto()).isNotNull();
        assertThat(vorschlaege.sachkonto().id()).isEqualTo(2L);
        assertThat(vorschlaege.sachkonto().quelle()).isEqualTo(BelegVorschlagService.Quelle.HISTORIE);
        assertThat(vorschlaege.kostenstelle()).isNull();
    }

    @Test
    void deaktivierteKiUndStandardKostenstelleFallenAufHistorieZurueck() {
        beleg.setKiVorgeschlagenerKostenstelleId(1L);
        when(kostenstelleRepository.findById(1L)).thenReturn(Optional.of(kostenstelle(1L, false)));
        lieferant.setStandardKostenstelle(kostenstelle(2L, false));
        historie(List.of(historisch(null, kostenstelle(1L, false)),
                historisch(null, kostenstelle(3L, true))));
        var vorschlaege = service.ermittle(beleg);
        assertThat(vorschlaege.kostenstelle()).isNotNull();
        assertThat(vorschlaege.kostenstelle().id()).isEqualTo(3L);
        assertThat(vorschlaege.kostenstelle().quelle()).isEqualTo(BelegVorschlagService.Quelle.HISTORIE);
        assertThat(vorschlaege.sachkonto()).isNull();
    }

    @Test
    void geloeschteKiZuordnungenOhneHistorieBleibenLeer() {
        beleg.setKiVorgeschlagenerSachkontoId(1L);
        beleg.setKiVorgeschlagenerKostenstelleId(2L);
        var vorschlaege = service.ermittle(beleg);
        assertThat(vorschlaege.sachkonto()).isNull();
        assertThat(vorschlaege.kostenstelle()).isNull();
    }

    @Test
    void ohneLieferantBleibenNurKiVorschlaegeMitOptionalerBegruendung() {
        beleg.setLieferant(null);
        beleg.setKiVorgeschlagenerSachkontoId(1L);
        beleg.setKiVorgeschlagenerKostenstelleId(2L);
        beleg.setKiKostenkontoBegruendung("  ");
        when(sachkontoRepository.findById(1L)).thenReturn(Optional.of(konto(1L, true)));
        when(kostenstelleRepository.findById(2L)).thenReturn(Optional.of(kostenstelle(2L, true)));
        var vorschlaege = service.ermittle(beleg);
        assertThat(vorschlaege.sachkonto()).isNotNull();
        assertThat(vorschlaege.sachkonto().quelle()).isEqualTo(BelegVorschlagService.Quelle.KI);
        assertThat(vorschlaege.kostenstelle()).isNotNull();
        assertThat(vorschlaege.kostenstelle().quelle()).isEqualTo(BelegVorschlagService.Quelle.KI);
        assertThat(vorschlaege.sachkonto().begruendung()).isEqualTo("Die KI schlägt das vor.");
        verifyNoInteractions(belegRepository);
    }

    @Test
    void ohneLieferantUndKiGibtEsKeineVorschlaege() {
        beleg.setLieferant(null);
        var vorschlaege = service.ermittle(beleg);
        assertThat(vorschlaege.sachkonto()).isNull();
        assertThat(vorschlaege.kostenstelle()).isNull();
        verifyNoInteractions(belegRepository, sachkontoRepository, kostenstelleRepository);
    }

    private void historie(List<Beleg> belege) {
        when(belegRepository.findLetzteGepruefteByLieferant(7L, 42L, PageRequest.of(0, 20)))
                .thenReturn(belege);
    }

    private static Sachkonto konto(Long id, boolean aktiv) {
        var konto = new Sachkonto();
        konto.setId(id);
        konto.setNummer("4980");
        konto.setBezeichnung("Betriebsbedarf");
        konto.setAktiv(aktiv);
        return konto;
    }

    private static Kostenstelle kostenstelle(Long id, boolean aktiv) {
        var kostenstelle = new Kostenstelle();
        kostenstelle.setId(id);
        kostenstelle.setBezeichnung("Werkstatt");
        kostenstelle.setAktiv(aktiv);
        return kostenstelle;
    }

    private static Beleg historisch(Sachkonto konto, Kostenstelle kostenstelle) {
        var beleg = new Beleg();
        beleg.setSachkonto(konto);
        beleg.setKostenstelle(kostenstelle);
        return beleg;
    }
}
