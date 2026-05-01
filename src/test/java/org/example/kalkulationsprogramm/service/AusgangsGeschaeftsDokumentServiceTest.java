package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentCounter;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieVorschlagDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentCounterRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AusgangsGeschaeftsDokumentServiceTest {

    @Mock private AusgangsGeschaeftsDokumentRepository dokumentRepository;
    @Mock private AusgangsGeschaeftsDokumentCounterRepository counterRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private AnfrageRepository anfrageRepository;
    @Mock private KundeRepository kundeRepository;
    @Mock private FrontendUserProfileRepository frontendUserProfileRepository;
    @Mock private LeistungRepository leistungRepository;
    @Mock private ProduktkategorieRepository produktkategorieRepository;
    @Mock private ProjektDokumentRepository projektDokumentRepository;
    @Mock private ZeitbuchungRepository zeitbuchungRepository;

    private AusgangsGeschaeftsDokumentService service;

    @BeforeEach
    void setUp() {
        service = new AusgangsGeschaeftsDokumentService(
                "uploads",
                dokumentRepository,
                counterRepository,
                projektRepository,
                anfrageRepository,
                kundeRepository,
                frontendUserProfileRepository,
                leistungRepository,
                produktkategorieRepository,
                projektDokumentRepository,
                zeitbuchungRepository);
    }

    private void mockCounterForNummer() {
        AusgangsGeschaeftsDokumentCounter counter = new AusgangsGeschaeftsDokumentCounter();
        counter.setZaehler(0L);
        when(counterRepository.findByMonatKeyForUpdate(any())).thenReturn(Optional.of(counter));
        when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class Erstellen {

        @Test
        void erstelltDokumentMitKorrekterNummer() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setBetreff("Testanfrage");

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getDokumentNummer()).isNotNull();
            assertThat(result.getDokumentNummer())
                    .matches("^AG-\\d{4}/\\d{2}/\\d{5}$");
            assertThat(result.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            assertThat(result.getBetreff()).isEqualTo("Testanfrage");
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({
                "ANGEBOT, AG",
                "AUFTRAGSBESTAETIGUNG, AB",
                "RECHNUNG, RE",
                "TEILRECHNUNG, TR",
                "ABSCHLAGSRECHNUNG, AR",
                "SCHLUSSRECHNUNG, SR",
                "GUTSCHRIFT, GU"
        })
        void vergibtKorrektesPraefixProTyp(AusgangsGeschaeftsDokumentTyp typ, String praefix) {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(typ);
            dto.setBetreff("Test");

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getDokumentNummer())
                    .matches("^" + praefix + "-\\d{4}/\\d{2}/\\d{5}$");
        }

        @Test
        void berechnetBruttoMitMwstKorrekt() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setBetragNetto(new BigDecimal("1000.00"));
            dto.setMwstSatz(new BigDecimal("0.19"));

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getBetragBrutto()).isEqualByComparingTo(new BigDecimal("1190.00"));
        }

        @Test
        void uebernimmtKundeAusProjektWennNichtExplizitGesetzt() {
            mockCounterForNummer();
            Kunde kunde = new Kunde();
            kunde.setId(10L);
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            projekt.setKundenId(kunde);
            when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setProjektId(5L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getKunde()).isEqualTo(kunde);
        }

        @Test
        void uebernimmtInhalteVomVorgaenger() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument vorgaenger = new AusgangsGeschaeftsDokument();
            vorgaenger.setId(100L);
            vorgaenger.setHtmlInhalt("<p>Vorgänger</p>");
            vorgaenger.setPositionenJson("{\"blocks\":[]}");
            Kunde kunde = new Kunde();
            kunde.setId(10L);
            vorgaenger.setKunde(kunde);
            when(dokumentRepository.findById(100L)).thenReturn(Optional.of(vorgaenger));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(2L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            dto.setVorgaengerId(100L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getHtmlInhalt()).isEqualTo("<p>Vorgänger</p>");
            assertThat(result.getPositionenJson()).isEqualTo("{\"blocks\":[]}");
            assertThat(result.getKunde()).isEqualTo(kunde);
        }

        @Test
        void wirftExceptionBeiDoppeltemBasisdokumentProProjekt() {
            when(dokumentRepository.existsByProjektIdAndVorgaengerIsNull(5L)).thenReturn(true);

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setProjektId(5L);

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Basisdokument");
        }

        @Test
        void wirftExceptionBeiDoppeltemBasisdokumentProAnfrage() {
            when(dokumentRepository.existsByAnfrageIdAndVorgaengerIsNull(7L)).thenReturn(true);

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setAnfrageId(7L);

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Basisdokument");
        }

        @Test
        void setztAbschlagsnummerKorrekt() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument vorgaenger = new AusgangsGeschaeftsDokument();
            vorgaenger.setId(50L);
            vorgaenger.setBetragNetto(new BigDecimal("10000.00"));
            vorgaenger.setBetragBrutto(new BigDecimal("11900.00"));
            when(dokumentRepository.findById(50L)).thenReturn(Optional.of(vorgaenger));
            when(dokumentRepository.countByVorgaengerIdAndTyp(50L, AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG))
                    .thenReturn(2);
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(50L)).thenReturn(Collections.emptyList());
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(3L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            dto.setVorgaengerId(50L);
            dto.setBetragNetto(new BigDecimal("500.00"));

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getAbschlagsNummer()).isEqualTo(3);
        }

        @Test
        void setztDefaultMwstSatzWennNichtGesetzt() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setBetragNetto(new BigDecimal("100.00"));

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getMwstSatz()).isEqualByComparingTo(new BigDecimal("0.19"));
        }

        @Test
        void setztDatumAufHeuteWennNichtGesetzt() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getDatum()).isEqualTo(LocalDate.now());
        }
    }

    @Nested
    class Aktualisieren {

        @Test
        void aktualisiertDokumentErfolgreich() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dokument.setBetreff("Alt");
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
            dto.setBetreff("Neu");
            dto.setBetragNetto(new BigDecimal("2000.00"));
            dto.setMwstSatz(new BigDecimal("0.19"));

            AusgangsGeschaeftsDokument result = service.aktualisieren(1L, dto);

            assertThat(result.getBetreff()).isEqualTo("Neu");
            assertThat(result.getBetragNetto()).isEqualByComparingTo(new BigDecimal("2000.00"));
            assertThat(result.getBetragBrutto()).isEqualByComparingTo(new BigDecimal("2380.00"));
        }

        @Test
        void wirftExceptionBeiGebuchtemDokument() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setGebucht(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
            dto.setBetreff("Änderung");

            assertThatThrownBy(() -> service.aktualisieren(1L, dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("gesperrt");
        }

        @Test
        void wirftExceptionBeiNichtGefundenemDokument() {
            when(dokumentRepository.findById(999L)).thenReturn(Optional.empty());

            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();

            assertThatThrownBy(() -> service.aktualisieren(999L, dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nicht gefunden");
        }
    }

    @Nested
    class Buchen {

        @Test
        void buchtRechnungErfolgreich() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setDokumentNummer("2026/03/00001");
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            dokument.setProjekt(projekt);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.existsByDokumentid("2026/03/00001")).thenReturn(false);

            AusgangsGeschaeftsDokument result = service.buchen(1L);

            assertThat(result.isGebucht()).isTrue();
            assertThat(result.getGebuchtAm()).isEqualTo(LocalDate.now());
        }

        @Test
        void buchtAnfrageNicht() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            AusgangsGeschaeftsDokument result = service.buchen(1L);

            assertThat(result.isGebucht()).isFalse();
        }

        @Test
        void gibtBereitsGebuchtesZurueckOhneAenderung() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setGebucht(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            AusgangsGeschaeftsDokument result = service.buchen(1L);

            verify(dokumentRepository, never()).save(any());
            assertThat(result.isGebucht()).isTrue();
        }

        @Test
        void wirftExceptionBeiStorniertemDokument() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setStorniert(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.buchen(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Storniert");
        }
    }

    @Nested
    class Stornieren {

        @Test
        void storniertRechnungUndErstelltGegendokument() {
            AusgangsGeschaeftsDokument original = new AusgangsGeschaeftsDokument();
            original.setId(1L);
            original.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            original.setDokumentNummer("2026/03/00001");
            original.setBetragNetto(new BigDecimal("1000.00"));
            original.setBetragBrutto(new BigDecimal("1190.00"));
            original.setMwstSatz(new BigDecimal("0.19"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(original));
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.findAllGeschaeftsdokumente()).thenReturn(Collections.emptyList());

            AusgangsGeschaeftsDokument storno = service.stornieren(1L);

            assertThat(original.isStorniert()).isTrue();
            assertThat(original.getStorniertAm()).isEqualTo(LocalDate.now());
            assertThat(storno.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.STORNO);
            assertThat(storno.getDokumentNummer()).matches("^ST-\\d{4}/\\d{2}/\\d{5}$");
            assertThat(storno.getBetragNetto()).isEqualByComparingTo(new BigDecimal("-1000.00"));
            assertThat(storno.getBetragBrutto()).isEqualByComparingTo(new BigDecimal("-1190.00"));
            assertThat(storno.isGebucht()).isTrue();
        }

        @Test
        void wirftExceptionBeiBereitsStorniertemDokument() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setStorniert(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.stornieren(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("bereits storniert");
        }

        @Test
        void wirftExceptionBeiAnfrageStornierung() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.stornieren(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Nur Rechnungen");
        }
    }

    @Nested
    class Loeschen {

        @Test
        void loeschtEntwurfMitBegruendung() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            service.loeschen(1L, "Test-Begründung");

            verify(dokumentRepository).delete(dokument);
        }

        @Test
        void wirftExceptionBeiLeeremGrund() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.loeschen(1L, ""))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Begründung");
        }

        @Test
        void wirftExceptionBeiGebuchtemDokument() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setGebucht(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.loeschen(1L, "Grund"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("GoBD");
        }

        @Test
        void wirftExceptionBeiVersandtemDokument() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dokument.setVersandDatum(LocalDate.now());
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.loeschen(1L, "Grund"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("versandte");
        }

        @Test
        void wirftExceptionBeiStornoTyp() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.STORNO);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.loeschen(1L, "Grund"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Stornorechnungen");
        }
    }

    @Nested
    class EnsureAnfrageDokument {

        @Test
        void gibtExistierendeNummerZurueck() {
            AusgangsGeschaeftsDokument existing = new AusgangsGeschaeftsDokument();
            existing.setDokumentNummer("2026/03/00001");
            when(dokumentRepository.findFirstByAnfrageIdAndTyp(5L, AusgangsGeschaeftsDokumentTyp.ANGEBOT))
                    .thenReturn(Optional.of(existing));

            String result = service.ensureAnfrageDokument(5L);

            assertThat(result).isEqualTo("2026/03/00001");
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void erstelltNeuesDokumentWennKeinesExistiert() {
            when(dokumentRepository.findFirstByAnfrageIdAndTyp(5L, AusgangsGeschaeftsDokumentTyp.ANGEBOT))
                    .thenReturn(Optional.empty());
            Anfrage anfrage = new Anfrage();
            anfrage.setId(5L);
            anfrage.setBauvorhaben("Bauprojekt");
            when(anfrageRepository.findById(5L)).thenReturn(Optional.of(anfrage));
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            String result = service.ensureAnfrageDokument(5L);

            assertThat(result).isNotNull();
            verify(dokumentRepository, atLeastOnce()).save(any());
        }

        @Test
        void gibtNullZurueckBeiNullId() {
            assertThat(service.ensureAnfrageDokument(null)).isNull();
        }
    }

    @Nested
    class Abrechnungsverlauf {

        @Test
        void berechnetRestbetragKorrekt() {
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/03/00001");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            basis.setBetragNetto(new BigDecimal("10000.00"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));

            AusgangsGeschaeftsDokument abschlag1 = new AusgangsGeschaeftsDokument();
            abschlag1.setId(2L);
            abschlag1.setDokumentNummer("2026/03/00002");
            abschlag1.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            abschlag1.setBetragNetto(new BigDecimal("3000.00"));
            abschlag1.setAbschlagsNummer(1);

            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of(abschlag1));

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getBasisdokumentBetragNetto()).isEqualByComparingTo(new BigDecimal("10000.00"));
            assertThat(verlauf.getBereitsAbgerechnet()).isEqualByComparingTo(new BigDecimal("3000.00"));
            assertThat(verlauf.getRestbetrag()).isEqualByComparingTo(new BigDecimal("7000.00"));
            assertThat(verlauf.getPositionen()).hasSize(1);
        }

        @Test
        void ignoriertStornierteRechnungenBeimRestbetrag() {
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/03/00001");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            basis.setBetragNetto(new BigDecimal("10000.00"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));

            AusgangsGeschaeftsDokument storniert = new AusgangsGeschaeftsDokument();
            storniert.setId(2L);
            storniert.setDokumentNummer("2026/03/00002");
            storniert.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            storniert.setBetragNetto(new BigDecimal("3000.00"));
            storniert.setStorniert(true);

            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of(storniert));

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getBereitsAbgerechnet()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(verlauf.getRestbetrag()).isEqualByComparingTo(new BigDecimal("10000.00"));
        }
    }

    @Nested
    class KategorieVorschlagFuerAnfrage {

        private Produktkategorie kategorieRohbau;
        private Produktkategorie kategorieFliesen;

        @BeforeEach
        void setUpKategorien() {
            kategorieRohbau = new Produktkategorie();
            kategorieRohbau.setId(10L);
            kategorieRohbau.setBezeichnung("Rohbau");
            kategorieRohbau.setVerrechnungseinheit(Verrechnungseinheit.QUADRATMETER);

            kategorieFliesen = new Produktkategorie();
            kategorieFliesen.setId(20L);
            kategorieFliesen.setBezeichnung("Fliesen");
            kategorieFliesen.setVerrechnungseinheit(Verrechnungseinheit.QUADRATMETER);
        }

        private AusgangsGeschaeftsDokument dok(AusgangsGeschaeftsDokumentTyp typ, String positionenJson) {
            AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
            d.setTyp(typ);
            d.setPositionenJson(positionenJson);
            d.setStorniert(false);
            return d;
        }

        private Leistung leistung(Long id, Produktkategorie kategorie) {
            Leistung l = new Leistung();
            l.setId(id);
            l.setBezeichnung("Leistung " + id);
            l.setEinheit(Verrechnungseinheit.QUADRATMETER);
            l.setPreis(BigDecimal.TEN);
            l.setKategorie(kategorie);
            return l;
        }

        @Test
        void liefertLeereListeWennKeineDokumente() {
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(99L))
                    .thenReturn(Collections.emptyList());

            List<KategorieVorschlagDto> result = service.berechneKategorieVorschlagFuerAnfrage(99L);

            assertThat(result).isEmpty();
        }

        @Test
        void aggregiertMengenAusAngebot() {
            String json = "[{\"type\":\"SERVICE\",\"leistungId\":1,\"quantity\":5}," +
                          "{\"type\":\"SERVICE\",\"leistungId\":2,\"quantity\":3}]";
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(dok(AusgangsGeschaeftsDokumentTyp.ANGEBOT, json)));
            when(leistungRepository.findAllById(any()))
                    .thenReturn(List.of(leistung(1L, kategorieRohbau), leistung(2L, kategorieFliesen)));
            when(produktkategorieRepository.findAllById(any()))
                    .thenReturn(List.of(kategorieRohbau, kategorieFliesen));

            List<KategorieVorschlagDto> result = service.berechneKategorieVorschlagFuerAnfrage(1L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(KategorieVorschlagDto::getQuelle)
                    .containsOnly(AusgangsGeschaeftsDokumentTyp.ANGEBOT.name());
            assertThat(result).extracting(KategorieVorschlagDto::getKategorieId, KategorieVorschlagDto::getMenge)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(10L, new BigDecimal("5.0")),
                            org.assertj.core.groups.Tuple.tuple(20L, new BigDecimal("3.0")));
        }

        @Test
        void abHatVorrangVorAngebot() {
            String angebotJson = "[{\"type\":\"SERVICE\",\"leistungId\":1,\"quantity\":99}]";
            String abJson = "[{\"type\":\"SERVICE\",\"leistungId\":1,\"quantity\":7}]";
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(1L)).thenReturn(List.of(
                    dok(AusgangsGeschaeftsDokumentTyp.ANGEBOT, angebotJson),
                    dok(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, abJson)));
            when(leistungRepository.findAllById(any()))
                    .thenReturn(List.of(leistung(1L, kategorieRohbau)));
            when(produktkategorieRepository.findAllById(any())).thenReturn(List.of(kategorieRohbau));

            List<KategorieVorschlagDto> result = service.berechneKategorieVorschlagFuerAnfrage(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMenge()).isEqualByComparingTo(new BigDecimal("7"));
            assertThat(result.get(0).getQuelle())
                    .isEqualTo(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG.name());
        }

        @Test
        void ignoriertOptionaleUndStorniertePositionen() {
            String json = "[{\"type\":\"SERVICE\",\"leistungId\":1,\"quantity\":5,\"optional\":true}," +
                          "{\"type\":\"SERVICE\",\"leistungId\":2,\"quantity\":4}]";
            AusgangsGeschaeftsDokument storniert = dok(AusgangsGeschaeftsDokumentTyp.ANGEBOT,
                    "[{\"type\":\"SERVICE\",\"leistungId\":1,\"quantity\":100}]");
            storniert.setStorniert(true);
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(storniert, dok(AusgangsGeschaeftsDokumentTyp.ANGEBOT, json)));
            when(leistungRepository.findAllById(any()))
                    .thenReturn(List.of(leistung(2L, kategorieFliesen)));
            when(produktkategorieRepository.findAllById(any())).thenReturn(List.of(kategorieFliesen));

            List<KategorieVorschlagDto> result = service.berechneKategorieVorschlagFuerAnfrage(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getKategorieId()).isEqualTo(20L);
            assertThat(result.get(0).getMenge()).isEqualByComparingTo(new BigDecimal("4"));
        }
    }
}
