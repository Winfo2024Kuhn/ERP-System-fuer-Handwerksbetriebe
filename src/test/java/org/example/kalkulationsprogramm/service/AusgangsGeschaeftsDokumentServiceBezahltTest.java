package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Projekt;
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
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AusgangsGeschaeftsDokumentServiceBezahltTest {

    @Mock
    private AusgangsGeschaeftsDokumentRepository dokumentRepository;

    @Mock
    private AusgangsGeschaeftsDokumentCounterRepository counterRepository;

    @Mock
    private ProjektRepository projektRepository;

    @Mock
    private AnfrageRepository anfrageRepository;

    @Mock
    private KundeRepository kundeRepository;

    @Mock
    private FrontendUserProfileRepository frontendUserProfileRepository;

    @Mock
    private LeistungRepository leistungRepository;

    @Mock
    private ProduktkategorieRepository produktkategorieRepository;

    @Mock
    private ProjektDokumentRepository projektDokumentRepository;

    @Mock
    private ZeitbuchungRepository zeitbuchungRepository;

    @Mock
    private AusgangsGeschaeftsDokumentAuditService auditService;

    private AusgangsGeschaeftsDokumentService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new AusgangsGeschaeftsDokumentService(
                tempDir.toString(),
                dokumentRepository,
                counterRepository,
                projektRepository,
                anfrageRepository,
                kundeRepository,
                frontendUserProfileRepository,
                leistungRepository,
                produktkategorieRepository,
                projektDokumentRepository,
                zeitbuchungRepository,
                auditService
        );
    }

    @Nested
    class AktualisiereProjektPreisAusDokumenten {

        @Test
        void setztBezahltUndAbgeschlossenWennRechnungssummeAusreichtUndKeineOffenenPosten() {
            Projekt projekt = new Projekt();
            projekt.setId(1L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("1000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("1000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(1L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(anfrage, rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(1L)).thenReturn(false);

            service.aktualisiereProjektPreisAusDokumenten(1L);

            assertThat(projekt.isBezahlt()).isTrue();
            assertThat(projekt.isAbgeschlossen()).isTrue();
            verify(projektRepository).save(projekt);
        }

        @Test
        void setztNichtBezahltWennRechnungssummeAusreichtAberNochOffenePostenExistieren() {
            Projekt projekt = new Projekt();
            projekt.setId(2L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("5000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("5000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(2L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(2L))
                    .thenReturn(List.of(anfrage, rechnung));
            // Noch offene Posten vorhanden!
            when(projektDokumentRepository.existsOffenePostenByProjektId(2L)).thenReturn(true);

            service.aktualisiereProjektPreisAusDokumenten(2L);

            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
            verify(projektRepository).save(projekt);
        }

        @Test
        void setztNichtBezahltWennRechnungssummeNichtAusreicht() {
            Projekt projekt = new Projekt();
            projekt.setId(3L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("10000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("5000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(3L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(3L))
                    .thenReturn(List.of(anfrage, rechnung));

            service.aktualisiereProjektPreisAusDokumenten(3L);

            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
        }

        @Test
        void oeffnetProjektWiederWennOffenePostenHinzukommen() {
            Projekt projekt = new Projekt();
            projekt.setId(4L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(true);
            projekt.setAbgeschlossen(true);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("2000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("2000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(4L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(4L))
                    .thenReturn(List.of(anfrage, rechnung));
            // Offene Posten wurden hinzugefügt
            when(projektDokumentRepository.existsOffenePostenByProjektId(4L)).thenReturn(true);

            service.aktualisiereProjektPreisAusDokumenten(4L);

            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
        }

        @Test
        void auftragsbestaetigungErsetztIhrAngebot() {
            // AB wird aus dem Angebot erstellt (vorgaenger gesetzt) → das Childobjekt
            // (AB, 8000) ist maßgeblich, das ursprüngliche Angebot (5000) wird ersetzt.
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setId(50L);
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("5000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setId(51L);
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            ab.setBetragBrutto(new BigDecimal("8000.00"));
            ab.setStorniert(false);
            ab.setVorgaenger(anfrage);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("8000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(5L))
                    .thenReturn(List.of(anfrage, ab, rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(5L)).thenReturn(false);

            service.aktualisiereProjektPreisAusDokumenten(5L);

            // Preis = AB (8000), nicht Angebot + AB
            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("8000.00");
            assertThat(projekt.isBezahlt()).isTrue();
            assertThat(projekt.isAbgeschlossen()).isTrue();
        }

        @Test
        void nachtragsangebotAddiertSichZumAngebot() {
            // Vorgang 1: Angebot → AB (8000). Vorgang 2: Nachtragsangebot (2000),
            // noch nicht in eine AB überführt. Erwartet: 8000 + 2000 = 10000.
            Projekt projekt = new Projekt();
            projekt.setId(6L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);

            AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
            angebot.setId(60L);
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            angebot.setBetragBrutto(new BigDecimal("5000.00"));
            angebot.setStorniert(false);

            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setId(61L);
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            ab.setBetragBrutto(new BigDecimal("8000.00"));
            ab.setStorniert(false);
            ab.setVorgaenger(angebot);

            AusgangsGeschaeftsDokument nachtrag = new AusgangsGeschaeftsDokument();
            nachtrag.setId(62L);
            nachtrag.setTyp(AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT);
            nachtrag.setBetragBrutto(new BigDecimal("2000.00"));
            nachtrag.setStorniert(false);

            when(projektRepository.findById(6L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(6L))
                    .thenReturn(List.of(angebot, ab, nachtrag));
            when(projektDokumentRepository.existsOffenePostenByProjektId(6L)).thenReturn(false);

            service.aktualisiereProjektPreisAusDokumenten(6L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("10000.00");
        }

        @Test
        void preisAusAbUndNachtragWirdDurchMehrereRechnungenVollstaendigBezahlt() {
            // Integrativ: Preis = AB (8000) + Nachtragsangebot (2000) = 10000.
            // Abschlag (4000) + Schlussrechnung (6000) decken die Summe exakt.
            Projekt projekt = new Projekt();
            projekt.setId(7L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
            angebot.setId(70L);
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            angebot.setBetragBrutto(new BigDecimal("5000.00"));
            angebot.setStorniert(false);

            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setId(71L);
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            ab.setBetragBrutto(new BigDecimal("8000.00"));
            ab.setStorniert(false);
            ab.setVorgaenger(angebot);

            AusgangsGeschaeftsDokument nachtrag = new AusgangsGeschaeftsDokument();
            nachtrag.setId(72L);
            nachtrag.setTyp(AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT);
            nachtrag.setBetragBrutto(new BigDecimal("2000.00"));
            nachtrag.setStorniert(false);

            AusgangsGeschaeftsDokument abschlag = new AusgangsGeschaeftsDokument();
            abschlag.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            abschlag.setBetragBrutto(new BigDecimal("4000.00"));
            abschlag.setStorniert(false);

            AusgangsGeschaeftsDokument schluss = new AusgangsGeschaeftsDokument();
            schluss.setTyp(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG);
            schluss.setBetragBrutto(new BigDecimal("6000.00"));
            schluss.setStorniert(false);

            when(projektRepository.findById(7L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(7L))
                    .thenReturn(List.of(angebot, ab, nachtrag, abschlag, schluss));
            when(projektDokumentRepository.existsOffenePostenByProjektId(7L)).thenReturn(false);

            service.aktualisiereProjektPreisAusDokumenten(7L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("10000.00");
            assertThat(projekt.isBezahlt()).isTrue();
            assertThat(projekt.isAbgeschlossen()).isTrue();
        }

        @Test
        void ignoriertNullProjektId() {
            service.aktualisiereProjektPreisAusDokumenten(null);

            verifyNoInteractions(projektRepository);
        }

        @Test
        void ignoriertNichtGefundenesProjekt() {
            when(projektRepository.findById(99L)).thenReturn(Optional.empty());

            service.aktualisiereProjektPreisAusDokumenten(99L);

            verify(projektRepository, never()).save(any());
        }
    }

    @Nested
    class AktualisiereAnfragePreisAusDokumenten {

        @Test
        void haeltBetragAktuellWennAbPreisSichAendert() {
            // Anfrage hatte bereits einen Betrag (5000); die aus dem Angebot
            // erstellte AB hat einen geänderten Preis (8000) → Betrag wird aktualisiert.
            Anfrage anfrage = new Anfrage();
            anfrage.setId(80L);
            anfrage.setBetrag(new BigDecimal("5000.00"));

            AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
            angebot.setId(800L);
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            angebot.setBetragBrutto(new BigDecimal("5000.00"));
            angebot.setStorniert(false);

            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setId(801L);
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            ab.setBetragBrutto(new BigDecimal("8000.00"));
            ab.setStorniert(false);
            ab.setVorgaenger(angebot);

            when(anfrageRepository.findById(80L)).thenReturn(Optional.of(anfrage));
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(80L))
                    .thenReturn(List.of(angebot, ab));

            service.aktualisiereAnfragePreisAusDokumenten(80L);

            assertThat(anfrage.getBetrag()).isEqualByComparingTo("8000.00");
            verify(anfrageRepository).save(anfrage);
        }

        @Test
        void behaeltManuellenBetragWennKeineDokumenteVorhanden() {
            // Ohne (preisrelevante) Dokumente bleibt ein manuell erfasster Betrag erhalten.
            Anfrage anfrage = new Anfrage();
            anfrage.setId(81L);
            anfrage.setBetrag(new BigDecimal("1234.00"));

            when(anfrageRepository.findById(81L)).thenReturn(Optional.of(anfrage));
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(81L))
                    .thenReturn(List.of());

            service.aktualisiereAnfragePreisAusDokumenten(81L);

            assertThat(anfrage.getBetrag()).isEqualByComparingTo("1234.00");
            verify(anfrageRepository).save(anfrage);
        }

        @Test
        void ignoriertNullAnfrageId() {
            service.aktualisiereAnfragePreisAusDokumenten(null);

            verifyNoInteractions(anfrageRepository);
        }
    }
}
