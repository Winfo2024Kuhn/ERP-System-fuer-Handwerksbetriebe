package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieErstellenDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Anlegen, Bearbeiten und Löschen von Ordnern auf der Seite „Leistungen & Kategorien“. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProduktkategorieServicePflegeTest {

    @Mock ProduktkategorieRepository produktkategorieRepository;
    @Mock ProjektRepository projektRepository;
    @Mock DateiSpeicherService dateiSpeicherService;
    @Mock LeistungRepository leistungRepository;

    ProduktkategorieService service;
    Produktkategorie gelaender;
    MockMultipartFile foto = new MockMultipartFile("bild", "gelaender.png", "image/png", new byte[] { 1, 2 });

    @BeforeEach
    void setUp() {
        service = new ProduktkategorieService(produktkategorieRepository, projektRepository,
                new ProduktkategorieMapper(), dateiSpeicherService, leistungRepository);
        gelaender = KategorieBaumTest.ordner(2, "Geländer", null);
        gelaender.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        when(produktkategorieRepository.findById(2L)).thenReturn(Optional.of(gelaender));
        when(produktkategorieRepository.save(any())).thenAnswer(inv -> {
            Produktkategorie k = inv.getArgument(0);
            if (k.getId() == null) {
                k.setId(9L);
            }
            return k;
        });
    }

    private static ProduktkategorieErstellenDto dto(String name, Long parentId) {
        ProduktkategorieErstellenDto dto = new ProduktkategorieErstellenDto();
        dto.setBezeichnung(name);
        dto.setParentId(parentId);
        return dto;
    }

    private static void erwarteFehler(Runnable aufruf, HttpStatus status, String textteil) {
        assertThatThrownBy(aufruf::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).contains(textteil);
        });
    }

    @Nested
    class Anlegen {

        @Test
        void unterordnerUebernimmtEinheitDesElternordners() {
            ProduktkategorieResponseDto neu = service.erstelleKategorie(dto("  Edelstahl ", 2L), null);

            assertThat(neu.getBezeichnung()).isEqualTo("Edelstahl");
            assertThat(neu.getVerrechnungseinheit()).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
            assertThat(neu.getParentId()).isEqualTo(2L);
            verify(dateiSpeicherService, never()).speichereBild(any());
        }

        @Test
        void hauptordnerOhneEinheitBekommtStueck() {
            ProduktkategorieResponseDto neu = service.erstelleKategorie(dto("Tore", null), null);

            assertThat(neu.getVerrechnungseinheit()).isEqualTo(Verrechnungseinheit.STUECK);
            assertThat(neu.getParentId()).isNull();
        }

        @Test
        void gewaehlteEinheitUndBildWerdenUebernommen() {
            ProduktkategorieErstellenDto dto = dto("Fliesen", null);
            dto.setVerrechnungseinheit(Verrechnungseinheit.QUADRATMETER);
            when(dateiSpeicherService.speichereBild(foto)).thenReturn("abc.png");

            ProduktkategorieResponseDto neu = service.erstelleKategorie(dto, foto);

            assertThat(neu.getVerrechnungseinheit()).isEqualTo(Verrechnungseinheit.QUADRATMETER);
            assertThat(neu.getBildUrl()).isEqualTo("/api/images/abc.png");
        }

        @Test
        void elternordnerMitLeistungenNurMitMitnehmen() {
            Leistung handlauf = new Leistung();
            handlauf.setKategorie(gelaender);
            when(leistungRepository.findByKategorieId(2L)).thenReturn(List.of(handlauf));

            erwarteFehler(() -> service.erstelleKategorie(dto("Edelstahl", 2L), null), HttpStatus.BAD_REQUEST,
                    "In „Geländer“ liegen 1 Leistungen");

            ProduktkategorieErstellenDto mitnehmen = dto("Edelstahl", 2L);
            mitnehmen.setLeistungenMitnehmen(true);
            service.erstelleKategorie(mitnehmen, null);

            assertThat(handlauf.getKategorie().getBezeichnung()).isEqualTo("Edelstahl");
            verify(leistungRepository).saveAll(List.of(handlauf));
        }

        @Test
        void unbekannterElternordnerIst400() {
            erwarteFehler(() -> service.erstelleKategorie(dto("Edelstahl", 999L), null), HttpStatus.BAD_REQUEST,
                    "existiert nicht mehr");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   " })
        void ohneNameIst400(String name) {
            erwarteFehler(() -> service.erstelleKategorie(dto(name, null), null), HttpStatus.BAD_REQUEST,
                    "einen Namen");
        }

        @Test
        void zuLangerNameIst400() {
            erwarteFehler(() -> service.erstelleKategorie(dto("x".repeat(10_001), null), null),
                    HttpStatus.BAD_REQUEST, "zu lang");
        }

        @ParameterizedTest
        @ValueSource(strings = { "<script>alert(1)</script>", "'; DROP TABLE produktkategorie; --" })
        void sonderzeichenWerdenUnveraendertAlsTextGespeichert(String name) {
            // React zeigt Namen als Text an; die Datenbank bekommt einen Parameter, kein SQL.
            assertThat(service.erstelleKategorie(dto(name, null), null).getBezeichnung()).isEqualTo(name);
        }

        @Test
        void falscherDateitypIst400() {
            when(dateiSpeicherService.speichereBild(foto)).thenThrow(new RuntimeException("Ungültiger Dateityp!"));

            erwarteFehler(() -> service.erstelleKategorie(dto("Tore", null), foto), HttpStatus.BAD_REQUEST,
                    "JPEG, PNG, GIF und WebP");
        }
    }

    @Nested
    class Bearbeiten {

        @Test
        void neuesBildErsetztDasAlte() {
            gelaender.setBildUrl("alt.png");
            when(dateiSpeicherService.speichereBild(foto)).thenReturn("neu.png");

            ProduktkategorieResponseDto dto = service.aktualisiereKategorie(2L, dto("Geländer", null), foto);

            assertThat(dto.getBildUrl()).isEqualTo("/api/images/neu.png");
            verify(dateiSpeicherService).loescheBild("alt.png");
            assertThat(dto.getVerrechnungseinheit()).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
        }

        @Test
        void bildEntfernen() {
            gelaender.setBildUrl("alt.png");
            ProduktkategorieErstellenDto dto = dto("Geländer", null);
            dto.setBildEntfernen(true);
            dto.setVerrechnungseinheit(Verrechnungseinheit.STUECK);

            ProduktkategorieResponseDto ergebnis = service.aktualisiereKategorie(2L, dto, null);

            assertThat(ergebnis.getBildUrl()).isNull();
            assertThat(ergebnis.getVerrechnungseinheit()).isEqualTo(Verrechnungseinheit.STUECK);
            verify(dateiSpeicherService).loescheBild("alt.png");
        }

        @Test
        void ohneBildBleibtDasBildUnveraendert() {
            gelaender.setBildUrl("alt.png");

            assertThat(service.aktualisiereKategorie(2L, dto("Geländer", null), null).getBildUrl())
                    .isEqualTo("/api/images/alt.png");
            verify(dateiSpeicherService, never()).loescheBild(any());
        }

        @Test
        void nichtLoeschbaresAltesBildStoertNicht() {
            gelaender.setBildUrl("alt.png");
            ProduktkategorieErstellenDto dto = dto("Geländer", null);
            dto.setBildEntfernen(true);
            doThrow(new RuntimeException("kaputt")).when(dateiSpeicherService).loescheBild("alt.png");

            assertThat(service.aktualisiereKategorie(2L, dto, null).getBildUrl()).isNull();
        }

        @ParameterizedTest
        @ValueSource(longs = { -1L, 0L, Long.MAX_VALUE })
        void unbekannterOrdnerIst404(long id) {
            erwarteFehler(() -> service.aktualisiereKategorie(id, dto("X", null), null), HttpStatus.NOT_FOUND,
                    "gibt es nicht mehr");
        }

        @Test
        void leererNameIst400() {
            erwarteFehler(() -> service.aktualisiereKategorie(2L, dto(" ", null), null), HttpStatus.BAD_REQUEST,
                    "einen Namen");
        }
    }

    @Nested
    class Loeschen {

        @Test
        void leererOrdnerWirdSamtBildGeloescht() {
            gelaender.setBildUrl("alt.png");

            service.loescheKategorie(2L);

            verify(dateiSpeicherService).loescheBild("alt.png");
            verify(produktkategorieRepository).delete(gelaender);
        }

        @Test
        void mitUnterordnernIst409() {
            gelaender.getUnterkategorien().add(KategorieBaumTest.ordner(3, "Edelstahl", gelaender));

            erwarteFehler(() -> service.loescheKategorie(2L), HttpStatus.CONFLICT, "wird noch verwendet");
        }

        @Test
        void mitLeistungenIst409() {
            when(leistungRepository.existsByKategorieId(2L)).thenReturn(true);

            erwarteFehler(() -> service.loescheKategorie(2L), HttpStatus.CONFLICT, "wird noch verwendet");
        }

        @Test
        void mitProjektenIst409() {
            when(projektRepository.countByProduktkategorieId(2L)).thenReturn(3L);

            erwarteFehler(() -> service.loescheKategorie(2L), HttpStatus.CONFLICT, "wird noch verwendet");
            verify(produktkategorieRepository, never()).delete(any());
        }

        @Test
        void unbekannterOrdnerIst404() {
            erwarteFehler(() -> service.loescheKategorie(99L), HttpStatus.NOT_FOUND, "gibt es nicht mehr");
        }
    }

    @Test
    void alleKategorienZaehlenProjekteAuchImLightModusUeberAlleEbenen() {
        Produktkategorie metallbau = KategorieBaumTest.ordner(1, "Metallbau", null);
        Produktkategorie gel = KategorieBaumTest.ordner(2, "Geländer", metallbau);
        when(produktkategorieRepository.findAllWithParent()).thenReturn(List.of(metallbau, gel));
        when(produktkategorieRepository.findAllParentIds()).thenReturn(List.of(1L));
        when(projektRepository.getKategorieProjektPairs()).thenReturn(List.of(
                new Object[] { 1L, 10L }, new Object[] { 2L, 10L }, new Object[] { 2L, 11L }));

        List<ProduktkategorieResponseDto> alle = service.findeAlleKategorien(true);

        assertThat(alle).extracting(ProduktkategorieResponseDto::getProjektAnzahl).containsExactly(2L, 2L);
        assertThat(alle).extracting(ProduktkategorieResponseDto::isLeaf).containsExactly(false, true);
    }

    @Nested
    class BilderInDerTransaktion {

        @org.junit.jupiter.api.AfterEach
        void aufraeumen() {
            TransactionSynchronizationManager.clearSynchronization();
        }

        private void schliesseTransaktion(int status) {
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(status));
        }

        @Test
        void altesBildVerschwindetErstNachDemCommit() {
            TransactionSynchronizationManager.initSynchronization();
            gelaender.setBildUrl("alt.png");
            when(dateiSpeicherService.speichereBild(foto)).thenReturn("neu.png");

            service.aktualisiereKategorie(2L, dto("Geländer", null), foto);
            verify(dateiSpeicherService, never()).loescheBild(any());

            schliesseTransaktion(TransactionSynchronization.STATUS_COMMITTED);
            verify(dateiSpeicherService).loescheBild("alt.png");
            verify(dateiSpeicherService, never()).loescheBild("neu.png");
        }

        @Test
        void beiRollbackBleibtDasAlteUndDasNeueVerschwindet() {
            TransactionSynchronizationManager.initSynchronization();
            gelaender.setBildUrl("alt.png");
            when(dateiSpeicherService.speichereBild(foto)).thenReturn("neu.png");

            service.aktualisiereKategorie(2L, dto("Geländer", null), foto);
            schliesseTransaktion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(dateiSpeicherService).loescheBild("neu.png");
            verify(dateiSpeicherService, never()).loescheBild("alt.png");
        }

        @Test
        void neuerOrdnerRaeumtSeinBildBeiRollbackAuf() {
            TransactionSynchronizationManager.initSynchronization();
            when(dateiSpeicherService.speichereBild(foto)).thenReturn("neu.png");

            service.erstelleKategorie(dto("Tore", null), foto);
            schliesseTransaktion(TransactionSynchronization.STATUS_COMMITTED);
            verify(dateiSpeicherService, never()).loescheBild(any());
        }
    }
}
