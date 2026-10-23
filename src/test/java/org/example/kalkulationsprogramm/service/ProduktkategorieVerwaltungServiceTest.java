package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieLoeschPruefungDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieLoeschenDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProduktkategorieVerwaltungServiceTest {

    @Mock ProduktkategorieRepository produktkategorieRepository;
    @Mock LeistungRepository leistungRepository;
    @Mock ProjektProduktkategorieRepository projektProduktkategorieRepository;
    @Mock ZeitbuchungRepository zeitbuchungRepository;
    @Mock ZeitbuchungAuditService zeitbuchungAuditService;
    @Mock AngemeldeterMitarbeiterService angemeldeterMitarbeiterService;
    @Mock ProduktkategorieService produktkategorieService;
    @Mock Authentication authentication;

    ProduktkategorieVerwaltungService service;

    // Metallbau(1) > Geländer(2) > Edelstahl(3); Metallbau(1) > Tore(4); Fliesen(5)
    Produktkategorie metallbau;
    Produktkategorie gelaender;
    Produktkategorie edelstahl;
    Produktkategorie tore;
    Produktkategorie fliesen;
    Mitarbeiter chef;

    @BeforeEach
    void setUp() {
        service = new ProduktkategorieVerwaltungService(produktkategorieRepository, leistungRepository,
                projektProduktkategorieRepository, zeitbuchungRepository, zeitbuchungAuditService,
                angemeldeterMitarbeiterService, produktkategorieService, new ProduktkategorieMapper());
        metallbau = KategorieBaumTest.ordner(1, "Metallbau", null);
        gelaender = KategorieBaumTest.ordner(2, "Geländer", metallbau);
        edelstahl = KategorieBaumTest.ordner(3, "Edelstahl", gelaender);
        tore = KategorieBaumTest.ordner(4, "Tore", metallbau);
        tore.setBildUrl("tore.png");
        fliesen = KategorieBaumTest.ordner(5, "Fliesen", null);
        when(produktkategorieRepository.findAllWithParent())
                .thenReturn(List.of(metallbau, gelaender, edelstahl, tore, fliesen));
        when(produktkategorieRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        chef = new Mitarbeiter();
        chef.setId(42L);
        when(angemeldeterMitarbeiterService.bearbeiter(authentication)).thenReturn(chef);
    }

    static Leistung leistung(long id, String name, Produktkategorie ordner) {
        Leistung l = new Leistung();
        l.setId(id);
        l.setBezeichnung(name);
        l.setKategorie(ordner);
        return l;
    }

    static Projekt projekt(long id, String bauvorhaben) {
        Projekt p = new Projekt();
        p.setId(id);
        p.setBauvorhaben(bauvorhaben);
        p.setAuftragsnummer("2026-0" + id);
        return p;
    }

    static ProjektProduktkategorie zuordnung(long id, Projekt projekt, Produktkategorie ordner, String menge) {
        ProjektProduktkategorie ppk = new ProjektProduktkategorie();
        ppk.setId(id);
        ppk.setProjekt(projekt);
        ppk.setProduktkategorie(ordner);
        ppk.setMenge(new BigDecimal(menge));
        projekt.getProjektProduktkategorien().add(ppk);
        return ppk;
    }

    static Zeitbuchung buchung(long id, ProjektProduktkategorie ppk) {
        Zeitbuchung z = new Zeitbuchung();
        z.setId(id);
        z.setProjektProduktkategorie(ppk);
        return z;
    }

    private static void erwarteFehler(Runnable aufruf, HttpStatus status, String textteil) {
        assertThatThrownBy(aufruf::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(status);
                    assertThat(e.getReason()).contains(textteil);
                });
    }

    @Nested
    class Verschieben {

        @Test
        void haengtOrdnerUnterNeuenOrdner() {
            ProduktkategorieResponseDto dto = service.verschiebe(4L, 5L);

            assertThat(tore.getUebergeordneteKategorie()).isSameAs(fliesen);
            assertThat(dto.getParentId()).isEqualTo(5L);
            assertThat(dto.isLeaf()).isTrue();
            verify(produktkategorieRepository).save(tore);
        }

        @Test
        void machtOrdnerZumHauptordner() {
            ProduktkategorieResponseDto dto = service.verschiebe(2L, null);

            assertThat(gelaender.getUebergeordneteKategorie()).isNull();
            assertThat(dto.getParentId()).isNull();
            assertThat(dto.isLeaf()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(longs = { 2L, 3L })
        void lehntSichSelbstUndEigeneUnterordnerAb(long ziel) {
            erwarteFehler(() -> service.verschiebe(2L, ziel), HttpStatus.BAD_REQUEST, "in sich selbst");
            verify(produktkategorieRepository, never()).save(any());
        }

        @Test
        void lehntZielMitLeistungenAb() {
            when(leistungRepository.existsByKategorieId(5L)).thenReturn(true);

            erwarteFehler(() -> service.verschiebe(4L, 5L), HttpStatus.BAD_REQUEST, "In „Fliesen“ liegen Leistungen");
        }

        @Test
        void unbekanntesZielIst400() {
            erwarteFehler(() -> service.verschiebe(4L, 999L), HttpStatus.BAD_REQUEST, "existiert nicht mehr");
        }

        @ParameterizedTest
        @ValueSource(longs = { -1L, 0L, Long.MAX_VALUE })
        void unbekannterOrdnerIst404(long id) {
            erwarteFehler(() -> service.verschiebe(id, null), HttpStatus.NOT_FOUND, "gibt es nicht mehr");
        }
    }

    @Nested
    class Loeschpruefung {

        @Test
        void leererOrdnerHatKeineBezuege() {
            KategorieLoeschPruefungDto pruefung = service.pruefeLoeschen(5L);

            assertThat(pruefung.hatBezuege()).isFalse();
            assertThat(pruefung.pfad()).isEqualTo("Fliesen");
            assertThat(pruefung.unterordner()).isEmpty();
            assertThat(pruefung.projekte()).isEmpty();
            assertThat(pruefung.zeitbuchungAnzahl()).isZero();
            verify(zeitbuchungRepository, never()).countByProjektProduktkategorieIdIn(anyCollection());
        }

        @Test
        void zaehltAllesInklusiveUnterordnerUndKuerztDieListen() {
            List<Leistung> leistungen = LongStream.rangeClosed(1, 25)
                    .mapToObj(i -> leistung(i, "Geländer Typ " + (char) ('Z' - i), edelstahl))
                    .toList();
            when(leistungRepository.findByKategorieIdIn(List.of(2L, 3L))).thenReturn(leistungen);
            Projekt neubau = projekt(7, "Neubau Mustermann");
            Projekt umbau = projekt(9, "Umbau Musterfrau");
            when(projektProduktkategorieRepository.findeMitProjekt(List.of(2L, 3L))).thenReturn(List.of(
                    zuordnung(70, neubau, gelaender, "3"), zuordnung(71, neubau, edelstahl, "4"),
                    zuordnung(90, umbau, edelstahl, "1")));
            when(zeitbuchungRepository.countByProjektProduktkategorieIdIn(List.of(70L, 71L, 90L))).thenReturn(12L);

            KategorieLoeschPruefungDto pruefung = service.pruefeLoeschen(2L);

            assertThat(pruefung.hatBezuege()).isTrue();
            assertThat(pruefung.bezeichnung()).isEqualTo("Geländer");
            assertThat(pruefung.pfad()).isEqualTo("Metallbau > Geländer");
            assertThat(pruefung.unterordnerAnzahl()).isEqualTo(1);
            assertThat(pruefung.unterordner()).singleElement()
                    .satisfies(u -> assertThat(u.pfad()).isEqualTo("Metallbau > Geländer > Edelstahl"));
            assertThat(pruefung.leistungAnzahl()).isEqualTo(25);
            assertThat(pruefung.leistungen()).hasSize(ProduktkategorieVerwaltungService.MAX_LISTE);
            assertThat(pruefung.leistungen().get(0).name()).isEqualTo("Geländer Typ A");
            assertThat(pruefung.leistungen().get(0).ordnerId()).isEqualTo(3L);
            assertThat(pruefung.projektAnzahl()).isEqualTo(2);
            assertThat(pruefung.projekte()).extracting(KategorieLoeschPruefungDto.ProjektEintrag::bauvorhaben)
                    .containsExactly("Umbau Musterfrau", "Neubau Mustermann");
            assertThat(pruefung.zeitbuchungAnzahl()).isEqualTo(12);
        }

        @Test
        void nurUnterordnerZaehltAuchAlsBezug() {
            assertThat(service.pruefeLoeschen(1L).hatBezuege()).isTrue();
        }

        @Test
        void unbekannterOrdnerIst404() {
            erwarteFehler(() -> service.pruefeLoeschen(null), HttpStatus.NOT_FOUND, "gibt es nicht mehr");
        }
    }

    @Nested
    class Umziehen {

        @Test
        void ohneZielIst400() {
            erwarteFehler(() -> service.loesche(4L, new KategorieLoeschenDto("UMZIEHEN", null), authentication),
                    HttpStatus.BAD_REQUEST, "Ziel-Ordner");
        }

        @Test
        void unbekanntesZielIst400() {
            erwarteFehler(() -> service.loesche(4L, new KategorieLoeschenDto("umziehen", 999L), authentication),
                    HttpStatus.BAD_REQUEST, "existiert nicht mehr");
        }

        @Test
        void zielImEigenenAstIst400() {
            erwarteFehler(() -> service.loesche(2L, new KategorieLoeschenDto("UMZIEHEN", 3L), authentication),
                    HttpStatus.BAD_REQUEST, "nicht der Ordner selbst");
        }

        @Test
        void unterordnerNichtInOrdnerMitLeistungen() {
            when(leistungRepository.existsByKategorieId(5L)).thenReturn(true);

            erwarteFehler(() -> service.loesche(2L, new KategorieLoeschenDto("UMZIEHEN", 5L), authentication),
                    HttpStatus.BAD_REQUEST, "Dort können keine Unterordner hinein");
        }

        @Test
        void leistungenNichtInOrdnerMitUnterordnern() {
            when(leistungRepository.findByKategorieId(4L)).thenReturn(List.of(leistung(1, "Tor", tore)));

            erwarteFehler(() -> service.loesche(4L, new KategorieLoeschenDto("UMZIEHEN", 2L), authentication),
                    HttpStatus.BAD_REQUEST, "hat Unterordner");
        }

        @Test
        void inhaltUndProjekteWandernMitOhneZeitenAnzufassen() {
            Leistung tor = leistung(1, "Tor", tore);
            when(leistungRepository.findByKategorieId(4L)).thenReturn(List.of(tor));
            Projekt neubau = projekt(7, "Neubau Mustermann");
            ProjektProduktkategorie ppk = zuordnung(70, neubau, tore, "2");
            when(projektProduktkategorieRepository.findeMitProjekt(List.of(4L))).thenReturn(List.of(ppk));
            when(projektProduktkategorieRepository.findFirstByProjektIdAndProduktkategorieId(7L, 5L))
                    .thenReturn(Optional.empty());

            service.loesche(4L, new KategorieLoeschenDto("UMZIEHEN", 5L), authentication);

            assertThat(tor.getKategorie()).isSameAs(fliesen);
            assertThat(ppk.getProduktkategorie()).isSameAs(fliesen);
            verify(projektProduktkategorieRepository).save(ppk);
            verify(angemeldeterMitarbeiterService, never()).bearbeiter(any());
            verify(zeitbuchungAuditService, never()).protokolliereAenderung(any(), any(), any(), any());
            verify(produktkategorieService).loescheBildDatei("tore.png");
            verify(produktkategorieRepository).delete(tore);
        }

        @Test
        void auchBeimEinfachenUmzugWirdJedeZeitProtokolliert() {
            Projekt neubau = projekt(7, "Neubau Mustermann");
            ProjektProduktkategorie ppk = zuordnung(70, neubau, tore, "2");
            Zeitbuchung montag = buchung(500, ppk);
            when(projektProduktkategorieRepository.findeMitProjekt(List.of(4L))).thenReturn(List.of(ppk));
            when(projektProduktkategorieRepository.findFirstByProjektIdAndProduktkategorieId(7L, 5L))
                    .thenReturn(Optional.empty());
            when(zeitbuchungRepository.findByProjektProduktkategorieIdIn(List.of(70L))).thenReturn(List.of(montag));

            service.loesche(4L, new KategorieLoeschenDto("UMZIEHEN", 5L), authentication);

            assertThat(montag.getProjektProduktkategorie()).isSameAs(ppk);
            assertThat(ppk.getProduktkategorie()).isSameAs(fliesen);
            assertThat(montag.getVersion()).isEqualTo(2);
            verify(zeitbuchungAuditService).protokolliereAenderung(eq(montag), eq(chef), eq(ErfassungsQuelle.DESKTOP),
                    eq("Ordner „Tore“ gelöscht, Zeit auf „Fliesen“ umgehängt"));
        }

        @Test
        void unterordnerWandernMit() {
            service.loesche(2L, new KategorieLoeschenDto("UMZIEHEN", 5L), authentication);

            assertThat(edelstahl.getUebergeordneteKategorie()).isSameAs(fliesen);
            verify(produktkategorieRepository).saveAll(List.of(edelstahl));
            verify(produktkategorieRepository).delete(gelaender);
        }

        @Test
        void hatProjektDasZielSchonWerdenMengenAddiertUndZeitenProtokolliertUmgehaengt() {
            Projekt neubau = projekt(7, "Neubau Mustermann");
            ProjektProduktkategorie alt = zuordnung(70, neubau, tore, "2");
            ProjektProduktkategorie ziel = zuordnung(71, neubau, fliesen, "3.5");
            Zeitbuchung montag = buchung(500, alt);
            when(projektProduktkategorieRepository.findeMitProjekt(List.of(4L))).thenReturn(List.of(alt));
            when(projektProduktkategorieRepository.findFirstByProjektIdAndProduktkategorieId(7L, 5L))
                    .thenReturn(Optional.of(ziel));
            when(zeitbuchungRepository.findByProjektProduktkategorieIdIn(List.of(70L))).thenReturn(List.of(montag));

            service.loesche(4L, new KategorieLoeschenDto("UMZIEHEN", 5L), authentication);

            assertThat(ziel.getMenge()).isEqualByComparingTo("5.5");
            assertThat(montag.getProjektProduktkategorie()).isSameAs(ziel);
            assertThat(montag.getVersion()).isEqualTo(2);
            assertThat(montag.getZuletztGeaendertVon()).isSameAs(chef);
            verify(zeitbuchungAuditService).protokolliereAenderung(eq(montag), eq(chef), eq(ErfassungsQuelle.DESKTOP),
                    eq("Ordner „Tore“ gelöscht, Zeit auf „Fliesen“ umgehängt"));
            verify(zeitbuchungRepository).saveAll(List.of(montag));
            assertThat(neubau.getProjektProduktkategorien()).containsExactly(ziel);
            // Sofort löschen, nicht erst per orphanRemoval beim Flush (sonst Fremdschlüssel-Fehler).
            InOrder reihenfolge = inOrder(projektProduktkategorieRepository, produktkategorieRepository);
            reihenfolge.verify(projektProduktkategorieRepository).delete(alt);
            reihenfolge.verify(produktkategorieRepository).delete(tore);
        }
    }

    @Nested
    class AllesLoeschen {

        @Test
        void loeschtAstVonUntenNachObenUndNimmtZeitenDieKategorie() {
            Projekt neubau = projekt(7, "Neubau Mustermann");
            ProjektProduktkategorie ppk = zuordnung(71, neubau, edelstahl, "4");
            Zeitbuchung montag = buchung(500, ppk);
            List<Leistung> leistungen = List.of(leistung(1, "Handlauf", edelstahl));
            when(projektProduktkategorieRepository.findeMitProjekt(List.of(2L, 3L))).thenReturn(List.of(ppk));
            when(zeitbuchungRepository.findByProjektProduktkategorieIdIn(List.of(71L))).thenReturn(List.of(montag));
            when(leistungRepository.findByKategorieIdIn(List.of(2L, 3L))).thenReturn(leistungen);

            service.loesche(2L, new KategorieLoeschenDto("ALLES", null), authentication);

            assertThat(montag.getProjektProduktkategorie()).isNull();
            verify(zeitbuchungAuditService).protokolliereAenderung(eq(montag), eq(chef), eq(ErfassungsQuelle.DESKTOP),
                    eq("Ordner „Geländer“ mit Inhalt gelöscht, Kategorie entfernt"));
            assertThat(neubau.getProjektProduktkategorien()).isEmpty();
            verify(projektProduktkategorieRepository).delete(ppk);
            verify(leistungRepository).deleteAll(leistungen);
            InOrder reihenfolge = inOrder(produktkategorieRepository);
            reihenfolge.verify(produktkategorieRepository).delete(edelstahl);
            reihenfolge.verify(produktkategorieRepository).delete(gelaender);
        }

        @Test
        void ohneProjekteBrauchtEsKeinenMitarbeiter() {
            service.loesche(5L, new KategorieLoeschenDto(" alles ", null), authentication);

            verify(angemeldeterMitarbeiterService, never()).bearbeiter(any());
            verify(zeitbuchungAuditService, never()).protokolliereAenderung(any(), any(), any(), any());
            verify(produktkategorieRepository).delete(fliesen);
        }

        @Test
        void zuordnungAusserhalbDerProjektlisteWirdDirektGeloescht() {
            Projekt neubau = projekt(7, "Neubau Mustermann");
            ProjektProduktkategorie ppk = zuordnung(71, neubau, fliesen, "1");
            neubau.setProjektProduktkategorien(new ArrayList<>());
            when(projektProduktkategorieRepository.findeMitProjekt(List.of(5L))).thenReturn(List.of(ppk));

            service.loesche(5L, new KategorieLoeschenDto("ALLES", null), authentication);

            verify(projektProduktkategorieRepository).delete(ppk);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "QUATSCH", "'; DROP TABLE produktkategorie; --", "<script>alert(1)</script>" })
    void unbekannterModusIst400(String modus) {
        erwarteFehler(() -> service.loesche(5L, new KategorieLoeschenDto(modus, null), authentication),
                HttpStatus.BAD_REQUEST, "umziehen oder mitgelöscht");
        verify(produktkategorieRepository, never()).delete(any());
    }

    @Test
    void ohneAuftragIst400() {
        erwarteFehler(() -> service.loesche(5L, null, authentication), HttpStatus.BAD_REQUEST, "umziehen");
        erwarteFehler(() -> service.loesche(5L, new KategorieLoeschenDto(null, null), authentication),
                HttpStatus.BAD_REQUEST, "umziehen");
    }

    @Test
    void loeschenUnbekannterOrdnerIst404() {
        when(produktkategorieRepository.findAllWithParent()).thenReturn(List.of());
        erwarteFehler(() -> service.loesche(77L, new KategorieLoeschenDto("ALLES", null), authentication),
                HttpStatus.NOT_FOUND, "gibt es nicht mehr");
    }
}
