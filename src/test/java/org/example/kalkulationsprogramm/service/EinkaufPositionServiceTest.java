package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.einkauf.Dokumentart;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufPositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EinkaufPositionServiceTest {
    private ArtikelRepository artikelRepository;
    private EinkaufPositionService service;

    @BeforeEach
    void setUp() {
        artikelRepository = mock(ArtikelRepository.class);
        when(artikelRepository.findById(41L)).thenReturn(Optional.of(artikel(41L, "INT-41", "Profil", "EXT-LIEF-77")));
        service = new EinkaufPositionService(artikelRepository);
    }

    @Test
    void katalogpositionNutztInterneArtikelnummerStattLieferantennummer() {
        Artikel artikel = artikel(41L, "INT-41", "Profil", "EXT-LIEF-77");
        when(artikelRepository.findById(41L)).thenReturn(Optional.of(artikel));

        PositionSnapshot result = service.validiere(position(Positionsart.ARTIKEL, 41L,
                "fremde Nummer", null, null, new Mengenbasis(new BigDecimal("2"), Einheit.STUECK,
                        new BigDecimal("2"), null, null, null), List.of(), List.of()), 7L);

        assertEquals("INT-41", result.interneReferenz());
        assertEquals("Profil", result.bezeichnung());
    }

    @Test
    void fehlendeInterneArtikelnummerBleibtAlsNachpflegefallLeer() {
        Artikel artikel = artikel(42L, null, "Rohr", "EXT-99");
        when(artikelRepository.findById(42L)).thenReturn(Optional.of(artikel));

        PositionSnapshot result = service.validiere(position(Positionsart.ARTIKEL, 42L,
                "EXT-99", "Rohr", null, basis("1", Einheit.STUECK), List.of(), List.of()), 7L);

        assertNull(result.interneReferenz());
    }

    @Test
    void meterprofilBerechnetGesamtlaengeAusStueckzahlUndEinzellaenge() {
        PositionSnapshot result = service.validiere(position(Positionsart.ARTIKEL, 41L,
                "INT-41", "Profil", null, new Mengenbasis(null, Einheit.METER,
                        new BigDecimal("4"), new BigDecimal("6000"), null, "CAD"), List.of(), List.of()), 7L);

        assertEquals(new BigDecimal("24.000000"), result.basis().menge());
    }

    @Test
    void stueckpositionLehntAbweichendeProfilstueckzahlAb() {
        PositionSnapshot inconsistent = position(Positionsart.ARTIKEL, 41L, "INT-41", "Profil", null,
                new Mengenbasis(new BigDecimal("2"), Einheit.STUECK, new BigDecimal("3"),
                        null, null, null), List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.validiere(inconsistent, 7L));
    }

    @Test
    void zeichnungsteilBenoetigtProjektKennungRevisionUndAnlage() {
        PositionSnapshot p = position(Positionsart.ZEICHNUNGSTEIL, null, "", "Lasche", null,
                basis("1", Einheit.STUECK), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> service.validiere(p, null));
        assertThrows(IllegalArgumentException.class, () -> service.validiere(p, 7L));

        PositionSnapshot complete = new PositionSnapshot(Positionsart.ZEICHNUNGSTEIL, null, "P-7-L-03",
                "D-07", "A", "Lasche", "DX51D", null, basis("1", Einheit.STUECK), null,
                null, null, null, null, List.of(), List.of(19L));
        assertDoesNotThrow(() -> service.validiere(complete, 7L));
    }

    @Test
    void oberflaecheZeugnisLieferortUndTerminVerhindernBuendelung() {
        PositionSnapshot first = position(Positionsart.ARTIKEL, 41L, "INT-41", "Profil", "S235",
                basis("2", Einheit.STUECK), List.of(new DokumentSoll(Dokumentart.ZEUGNIS_3_1,
                        "Norm", "2024", true)), List.of(3L));
        Liefergruppe group = new Liefergruppe("Werkstatt", LocalDate.parse("2026-10-01"), 7L, null);
        PositionSnapshot same = new PositionSnapshot(first.art(), first.artikelId(), first.interneReferenz(),
                first.zeichnungsnummer(), first.zeichnungsrevision(), first.bezeichnung(), first.werkstoff(),
                first.abmessung(), first.basis(), first.schnittForm(), first.winkelLinks(), first.winkelRechts(),
                first.bearbeitung(), "lackiert", first.dokumente(), first.anlageVersionIds());

        assertNotEquals(service.buendelSchluessel(first, group), service.buendelSchluessel(same, group));
        assertNotEquals(service.buendelSchluessel(first, group), service.buendelSchluessel(first,
                new Liefergruppe("Baustelle", group.bedarfstermin(), 7L, null)));
        assertEquals(service.buendelSchluessel(first, group), service.buendelSchluessel(
                new PositionSnapshot(first.art(), first.artikelId(), first.interneReferenz(), first.zeichnungsnummer(),
                        first.zeichnungsrevision(), first.bezeichnung(), first.werkstoff(), first.abmessung(),
                        first.basis(), first.schnittForm(), first.winkelLinks(), first.winkelRechts(), first.bearbeitung(), first.oberflaeche(),
                        List.of(new DokumentSoll(Dokumentart.ZEUGNIS_3_1, "Norm", "2024", true)), List.of(3L)), group));
    }

    @Test
    void buendelschluesselSindUnabhaengigVonDezimalskalaDokumentreihenfolgeUndAnlagenreihenfolge() {
        PositionSnapshot first = position(Positionsart.ARTIKEL, 41L, "INT-41", "Profil", "S235",
                new Mengenbasis(new BigDecimal("24"), Einheit.METER, new BigDecimal("4"),
                        new BigDecimal("6000"), new BigDecimal("2.4"), "CAD"),
                List.of(new DokumentSoll(Dokumentart.ZEUGNIS_3_1, "Norm A", "2", true),
                        new DokumentSoll(Dokumentart.CE_NACHWEIS, "Norm B", "1", false)), List.of(2L, 1L));
        PositionSnapshot equivalent = position(Positionsart.ARTIKEL, 41L, "INT-41", "Profil", "S235",
                new Mengenbasis(new BigDecimal("24.000000"), Einheit.METER, new BigDecimal("4.0"),
                        new BigDecimal("6000.0"), new BigDecimal("2.4000"), "CAD"),
                List.of(new DokumentSoll(Dokumentart.CE_NACHWEIS, "Norm B", "1", false),
                        new DokumentSoll(Dokumentart.ZEUGNIS_3_1, "Norm A", "2", true)), List.of(1L, 2L));
        Liefergruppe group = new Liefergruppe("Werkstatt", LocalDate.parse("2026-10-01"), 7L, null);

        assertEquals(service.buendelSchluessel(first, group), service.buendelSchluessel(equivalent, group));
    }

    @Test
    void teilmengeAendertMengeOhneTechnischenSnapshotZuVerlieren() {
        PositionSnapshot original = position(Positionsart.ARTIKEL, 41L, "INT-41", "Profil", "S235",
                basis("24", Einheit.METER), List.of(), List.of(3L));

        PositionSnapshot teil = service.mitTeilmenge(original, new BigDecimal("6"));

        assertEquals(new BigDecimal("6.000000"), teil.basis().menge());
        assertEquals(original.artikelId(), teil.artikelId());
        assertEquals(original.werkstoff(), teil.werkstoff());
        assertEquals(original.anlageVersionIds(), teil.anlageVersionIds());
    }

    private static Artikel artikel(Long id, String internal, String name, String external) {
        Artikel artikel = new Artikel();
        artikel.setId(id);
        artikel.setArtikelnummer(internal);
        artikel.setProduktname(name);
        LieferantenArtikelPreise price = new LieferantenArtikelPreise();
        price.setExterneArtikelnummer(external);
        price.setArtikel(artikel);
        artikel.getArtikelpreis().add(price);
        return artikel;
    }

    private static Mengenbasis basis(String amount, Einheit unit) {
        return new Mengenbasis(new BigDecimal(amount), unit, null, null, null, null);
    }

    private static PositionSnapshot position(Positionsart type, Long articleId, String reference,
            String name, String material, Mengenbasis basis, List<DokumentSoll> docs, List<Long> attachments) {
        return new PositionSnapshot(type, articleId, reference, null, null, name, material, null, basis,
                null, null, null, null, null, docs, attachments);
    }
}
