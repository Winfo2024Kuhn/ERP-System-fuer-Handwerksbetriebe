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
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
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
        void setztBezahltUndAbgeschlossenWennAlleRechnungenGeschriebenUndBezahltSind() {
            Projekt projekt = new Projekt();
            projekt.setId(1L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("1000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = gebuchteRechnung(
                    AusgangsGeschaeftsDokumentTyp.RECHNUNG, "1000.00");

            when(projektRepository.findById(1L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(anfrage, rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(1L)).thenReturn(false);
            when(projektDokumentRepository.sumBezahlteRechnungenByProjektId(1L))
                    .thenReturn(new BigDecimal("1000.00"));

            service.aktualisiereProjektPreisAusDokumenten(1L);

            assertThat(projekt.isBezahlt()).isTrue();
            assertThat(projekt.isAbgeschlossen()).isTrue();
            verify(projektRepository).save(projekt);
        }

        @Test
        void beendetProjektNichtSobaldEineRechnungGeschriebenWird() {
            // Fehlerfall: Die Rechnung ist gerade erst erstellt (noch nicht gebucht),
            // es gibt also noch keinen Offenen Posten und keine Zahlung. Früher wurde
            // das Projekt trotzdem sofort als bezahlt und beendet markiert.
            Projekt projekt = new Projekt();
            projekt.setId(30L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            angebot.setBetragBrutto(new BigDecimal("1000.00"));
            angebot.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("1000.00"));
            rechnung.setStorniert(false);
            rechnung.setGebucht(false);

            when(projektRepository.findById(30L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(30L))
                    .thenReturn(List.of(angebot, rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(30L)).thenReturn(false);
            // Selbst wenn die bezahlte Summe passt, verhindert schon der Entwurf das Beenden.
            when(projektDokumentRepository.sumBezahlteRechnungenByProjektId(30L))
                    .thenReturn(new BigDecimal("1000.00"));

            service.aktualisiereProjektPreisAusDokumenten(30L);

            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
            verify(projektRepository).save(projekt);
        }

        @Test
        void oeffnetFaelschlichBeendetesProjektWiederWennRechnungNurEntwurfIst() {
            // Projekte, die der alte Fehler schon beim Schreiben der Rechnung beendet
            // hat, gehen bei der nächsten Neuberechnung wieder auf.
            Projekt projekt = new Projekt();
            projekt.setId(31L);
            projekt.setBruttoPreis(new BigDecimal("1000.00"));
            projekt.setBezahlt(true);
            projekt.setAbgeschlossen(true);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("1000.00"));
            rechnung.setStorniert(false);
            rechnung.setGebucht(false);

            when(projektRepository.findById(31L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(31L)).thenReturn(List.of(rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(31L)).thenReturn(false);

            service.aktualisiereProjektPreisAusDokumenten(31L);

            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
        }

        @Test
        void beendetProjektNichtSolangeDerNachtragNochNichtBezahltIst() {
            // AB (8000) + Nachtragsangebot (2000) = 10000. Die bezahlte Rechnung deckt
            // nur die AB ab – der Nachtrag ist weder berechnet noch bezahlt.
            Projekt projekt = new Projekt();
            projekt.setId(32L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setId(320L);
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            ab.setBetragBrutto(new BigDecimal("8000.00"));
            ab.setStorniert(false);

            AusgangsGeschaeftsDokument nachtrag = new AusgangsGeschaeftsDokument();
            nachtrag.setId(321L);
            nachtrag.setTyp(AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT);
            nachtrag.setBetragBrutto(new BigDecimal("2000.00"));
            nachtrag.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = gebuchteRechnung(
                    AusgangsGeschaeftsDokumentTyp.RECHNUNG, "8000.00");

            when(projektRepository.findById(32L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(32L))
                    .thenReturn(List.of(ab, nachtrag, rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(32L)).thenReturn(false);
            when(projektDokumentRepository.sumBezahlteRechnungenByProjektId(32L))
                    .thenReturn(new BigDecimal("8000.00"));

            service.aktualisiereProjektPreisAusDokumenten(32L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("10000.00");
            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
        }

        @Test
        void beendetProjektNichtWennRechnungenGebuchtAberNochNichtVollBezahltSind() {
            // Alle Rechnungen geschrieben und gebucht, kein Offener Posten mehr unbezahlt,
            // aber die bezahlte Summe reicht nicht – etwa weil ein Offener Posten fehlt
            // oder sein Betrag vom Rechnungsbetrag abweicht. Dann bleibt das Projekt offen.
            Projekt projekt = new Projekt();
            projekt.setId(33L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            angebot.setBetragBrutto(new BigDecimal("5000.00"));
            angebot.setStorniert(false);

            AusgangsGeschaeftsDokument teil = gebuchteRechnung(
                    AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG, "2000.00");
            AusgangsGeschaeftsDokument schluss = gebuchteRechnung(
                    AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG, "3000.00");

            when(projektRepository.findById(33L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(33L))
                    .thenReturn(List.of(angebot, teil, schluss));
            when(projektDokumentRepository.existsOffenePostenByProjektId(33L)).thenReturn(false);
            when(projektDokumentRepository.sumBezahlteRechnungenByProjektId(33L))
                    .thenReturn(new BigDecimal("2000.00"));

            service.aktualisiereProjektPreisAusDokumenten(33L);

            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
        }

        @Test
        void beendetProjektNichtErneutWennBenutzerDenHakenSelbstEntferntHat() {
            Projekt projekt = new Projekt();
            projekt.setId(10L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);
            // Benutzer hat den Haken "Beendet" von Hand entfernt.
            projekt.setAbgeschlossenManuell(true);

            AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            angebot.setBetragBrutto(new BigDecimal("1000.00"));
            angebot.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = gebuchteRechnung(
                    AusgangsGeschaeftsDokumentTyp.RECHNUNG, "1000.00");

            when(projektRepository.findById(10L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(10L))
                    .thenReturn(List.of(angebot, rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(10L)).thenReturn(false);
            when(projektDokumentRepository.sumBezahlteRechnungenByProjektId(10L))
                    .thenReturn(new BigDecimal("1000.00"));

            service.aktualisiereProjektPreisAusDokumenten(10L);

            // Bezahlt-Status läuft weiter automatisch, der Haken bleibt aber offen.
            assertThat(projekt.isBezahlt()).isTrue();
            assertThat(projekt.isAbgeschlossen()).isFalse();
            verify(projektRepository).save(projekt);
        }

        @Test
        void oeffnetManuellBeendetesProjektNichtWiederWennOffenePostenAuftauchen() {
            Projekt projekt = new Projekt();
            projekt.setId(11L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            // Benutzer hat den Haken "Beendet" von Hand gesetzt.
            projekt.setAbgeschlossen(true);
            projekt.setAbgeschlossenManuell(true);

            AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            angebot.setBetragBrutto(new BigDecimal("5000.00"));
            angebot.setStorniert(false);

            when(projektRepository.findById(11L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(11L)).thenReturn(List.of(angebot));
            when(projektDokumentRepository.existsOffenePostenByProjektId(11L)).thenReturn(true);

            service.aktualisiereProjektPreisAusDokumenten(11L);

            assertThat(projekt.isBezahlt()).isFalse();
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

            AusgangsGeschaeftsDokument rechnung = gebuchteRechnung(
                    AusgangsGeschaeftsDokumentTyp.RECHNUNG, "8000.00");

            when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(5L))
                    .thenReturn(List.of(anfrage, ab, rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(5L)).thenReturn(false);
            when(projektDokumentRepository.sumBezahlteRechnungenByProjektId(5L))
                    .thenReturn(new BigDecimal("8000.00"));

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

            AusgangsGeschaeftsDokument abschlag = gebuchteRechnung(
                    AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG, "4000.00");
            AusgangsGeschaeftsDokument schluss = gebuchteRechnung(
                    AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG, "6000.00");

            when(projektRepository.findById(7L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(7L))
                    .thenReturn(List.of(angebot, ab, nachtrag, abschlag, schluss));
            when(projektDokumentRepository.existsOffenePostenByProjektId(7L)).thenReturn(false);
            // Abschlag und Schlussrechnung sind beide bezahlt – inklusive Nachtrag.
            when(projektDokumentRepository.sumBezahlteRechnungenByProjektId(7L))
                    .thenReturn(new BigDecimal("10000.00"));

            service.aktualisiereProjektPreisAusDokumenten(7L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("10000.00");
            assertThat(projekt.isBezahlt()).isTrue();
            assertThat(projekt.isAbgeschlossen()).isTrue();
        }

        @Test
        void nimmtRechnungssummeAlsPreisWennEsKeinAngebotUndKeineAbGibt() {
            // Reparaturauftrag: Es wird direkt eine Rechnung im Programm geschrieben,
            // ohne vorheriges Angebot/AB. Vorher blieb der Bruttopreis auf 0 stehen
            // und musste von Hand nachgetragen werden.
            Projekt projekt = new Projekt();
            projekt.setId(20L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);

            AusgangsGeschaeftsDokument rechnung = gebuchteRechnung(
                    AusgangsGeschaeftsDokumentTyp.RECHNUNG, "1190.00");
            rechnung.setDokumentNummer("2026/07/00001");

            when(projektRepository.findById(20L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(20L)).thenReturn(List.of(rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(20L)).thenReturn(false);
            when(projektDokumentRepository.sumBezahlteRechnungenByProjektId(20L))
                    .thenReturn(new BigDecimal("1190.00"));

            service.aktualisiereProjektPreisAusDokumenten(20L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("1190.00");
            assertThat(projekt.isBezahlt()).isTrue();
        }

        @Test
        void nimmtSummeDerNacherfasstenRechnungenAlsPreisWennKeineInternenDokumenteExistieren() {
            // Rechnungen wurden außerhalb des Programms geschrieben und über
            // "Offene Posten → Manuell erfassen" nachgetragen.
            Projekt projekt = new Projekt();
            projekt.setId(21L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);

            when(projektRepository.findById(21L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(21L)).thenReturn(List.of());
            when(projektDokumentRepository.findRechnungenFuerPreisberechnung(21L)).thenReturn(List.of(
                    nacherfassteRechnung("RE-2026-001", "500.00"),
                    nacherfassteRechnung("RE-2026-002", "300.00")));
            when(projektDokumentRepository.existsOffenePostenByProjektId(21L)).thenReturn(false);

            service.aktualisiereProjektPreisAusDokumenten(21L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("800.00");
        }

        @Test
        void zaehltDieselbeRechnungsnummerNurEinmal() {
            // Eine im Programm gebuchte Rechnung erzeugt zusätzlich einen
            // Offene-Posten-Eintrag mit derselben Nummer – der darf nicht doppelt zählen.
            Projekt projekt = new Projekt();
            projekt.setId(22L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setDokumentNummer("2026/07/00042");
            rechnung.setBetragBrutto(new BigDecimal("1000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(22L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(22L)).thenReturn(List.of(rechnung));
            when(projektDokumentRepository.findRechnungenFuerPreisberechnung(22L)).thenReturn(List.of(
                    nacherfassteRechnung("2026/07/00042", "1000.00")));
            when(projektDokumentRepository.existsOffenePostenByProjektId(22L)).thenReturn(false);

            service.aktualisiereProjektPreisAusDokumenten(22L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("1000.00");
        }

        @Test
        void angebotHatVorrangVorDerRechnungssumme() {
            // Solange ein Angebot/AB existiert, ist dessen Preis maßgeblich – auch wenn
            // bisher nur ein Abschlag berechnet wurde.
            Projekt projekt = new Projekt();
            projekt.setId(23L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);

            AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
            angebot.setId(230L);
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            angebot.setBetragBrutto(new BigDecimal("5000.00"));
            angebot.setStorniert(false);

            AusgangsGeschaeftsDokument abschlag = new AusgangsGeschaeftsDokument();
            abschlag.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            abschlag.setDokumentNummer("2026/07/00007");
            abschlag.setBetragBrutto(new BigDecimal("2000.00"));
            abschlag.setStorniert(false);

            when(projektRepository.findById(23L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(23L)).thenReturn(List.of(angebot, abschlag));

            service.aktualisiereProjektPreisAusDokumenten(23L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("5000.00");
            assertThat(projekt.isBezahlt()).isFalse();
        }

        @Test
        void zaehltStornierteRechnungNichtUeberIhrenOffenenPostenEintrag() {
            // Beim Stornieren bleibt der Offene-Posten-Eintrag mit derselben Nummer
            // bestehen. Er darf den stornierten Betrag nicht als "nacherfasste"
            // Rechnung zurück in den Auftragspreis holen.
            Projekt projekt = new Projekt();
            projekt.setId(24L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);

            AusgangsGeschaeftsDokument storniert = new AusgangsGeschaeftsDokument();
            storniert.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            storniert.setDokumentNummer("2026/07/00013");
            storniert.setBetragBrutto(new BigDecimal("3000.00"));
            storniert.setStorniert(true);

            when(projektRepository.findById(24L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(24L)).thenReturn(List.of(storniert));
            // Der Offene-Posten-Eintrag der stornierten Rechnung existiert weiterhin
            when(projektDokumentRepository.findRechnungenFuerPreisberechnung(24L)).thenReturn(List.of(
                    nacherfassteRechnung("2026/07/00013", "3000.00")));

            service.aktualisiereProjektPreisAusDokumenten(24L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("0.00");
            assertThat(projekt.isBezahlt()).isFalse();
        }

        @Test
        void ignoriertNacherfassteRechnungOhneNummer() {
            // Ohne Dokumentnummer lässt sich weder gegen die internen Dokumente
            // abgleichen noch eine Dublette erkennen – solche Einträge zählen nicht mit.
            Projekt projekt = new Projekt();
            projekt.setId(25L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);

            when(projektRepository.findById(25L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(25L)).thenReturn(List.of());
            when(projektDokumentRepository.findRechnungenFuerPreisberechnung(25L)).thenReturn(List.of(
                    nacherfassteRechnung(null, "400.00"),
                    nacherfassteRechnung("RE-2026-007", "600.00")));
            when(projektDokumentRepository.existsOffenePostenByProjektId(25L)).thenReturn(false);

            service.aktualisiereProjektPreisAusDokumenten(25L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("600.00");
        }

        @Test
        void behaeltBezahltStatusWennProjektPreisHatAberKeineDokumente() {
            // Ein Altprojekt mit hinterlegtem Preis und bezahlter Rechnung darf durch
            // einen beliebigen Datei-Upload nicht auf "nicht bezahlt" zurückfallen.
            Projekt projekt = new Projekt();
            projekt.setId(26L);
            projekt.setBruttoPreis(new BigDecimal("2000.00"));
            projekt.setBezahlt(true);

            when(projektRepository.findById(26L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(26L)).thenReturn(List.of());
            when(projektDokumentRepository.findRechnungenFuerPreisberechnung(26L)).thenReturn(List.of(
                    nacherfassteRechnung("RE-2026-008", "2000.00")));
            when(projektDokumentRepository.existsOffenePostenByProjektId(26L)).thenReturn(false);
            when(projektDokumentRepository.sumBezahlteRechnungenByProjektId(26L))
                    .thenReturn(new BigDecimal("2000.00"));

            service.aktualisiereProjektPreisAusDokumenten(26L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("2000.00");
            assertThat(projekt.isBezahlt()).isTrue();
        }

        private AusgangsGeschaeftsDokument gebuchteRechnung(AusgangsGeschaeftsDokumentTyp typ, String betrag) {
            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(typ);
            rechnung.setBetragBrutto(new BigDecimal(betrag));
            rechnung.setStorniert(false);
            rechnung.setGebucht(true);
            return rechnung;
        }

        private ProjektGeschaeftsdokument nacherfassteRechnung(String nummer, String betrag) {
            ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
            rechnung.setDokumentid(nummer);
            rechnung.setGeschaeftsdokumentart("Rechnung");
            rechnung.setBruttoBetrag(new BigDecimal(betrag));
            return rechnung;
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

    /**
     * Gemeinsamer Weg für jeden Bezahlt-Haken (Offene-Posten-Center, Barzahlung,
     * Storno einer Barzahlung): Der Projektstatus muss sofort nachziehen.
     */
    @Nested
    class SetzeOffenenPostenBezahlt {

        @Test
        void abhakenDerLetztenRechnungSetztProjektAufBezahltUndBeendet() {
            Projekt projekt = projektMitAngebot(40L, false, false);
            ProjektGeschaeftsdokument posten = offenerPosten(projekt, false);

            when(projektRepository.findById(40L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(40L))
                    .thenReturn(List.of(angebot("1500.00"), gebuchteRechnung("1500.00")));
            when(projektDokumentRepository.existsOffenePostenByProjektId(40L)).thenReturn(false);
            when(projektDokumentRepository.sumBezahlteRechnungenByProjektId(40L))
                    .thenReturn(new BigDecimal("1500.00"));

            service.setzeOffenenPostenBezahlt(posten, true);

            assertThat(posten.isBezahlt()).isTrue();
            verify(projektDokumentRepository).saveAndFlush(posten);
            assertThat(projekt.isBezahlt()).isTrue();
            assertThat(projekt.isAbgeschlossen()).isTrue();
            verify(projektRepository).save(projekt);
        }

        @Test
        void zuruecksetzenNimmtBezahltSofortWiederWeg() {
            // z.B. Storno einer Barzahlung: Die Rechnung ist wieder offen, das Projekt
            // darf nicht weiter "Bezahlt" zeigen.
            Projekt projekt = projektMitAngebot(41L, true, true);
            ProjektGeschaeftsdokument posten = offenerPosten(projekt, true);

            when(projektRepository.findById(41L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(41L))
                    .thenReturn(List.of(angebot("1500.00"), gebuchteRechnung("1500.00")));
            when(projektDokumentRepository.existsOffenePostenByProjektId(41L)).thenReturn(true);

            service.setzeOffenenPostenBezahlt(posten, false);

            assertThat(posten.isBezahlt()).isFalse();
            verify(projektDokumentRepository).saveAndFlush(posten);
            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
        }

        @Test
        void postenOhneProjektWirdNurGespeichert() {
            ProjektGeschaeftsdokument posten = offenerPosten(null, false);

            service.setzeOffenenPostenBezahlt(posten, true);

            assertThat(posten.isBezahlt()).isTrue();
            verify(projektDokumentRepository).saveAndFlush(posten);
            verifyNoInteractions(projektRepository, dokumentRepository);
        }

        private Projekt projektMitAngebot(Long id, boolean bezahlt, boolean abgeschlossen) {
            Projekt projekt = new Projekt();
            projekt.setId(id);
            projekt.setBruttoPreis(new BigDecimal("1500.00"));
            projekt.setBezahlt(bezahlt);
            projekt.setAbgeschlossen(abgeschlossen);
            return projekt;
        }

        private ProjektGeschaeftsdokument offenerPosten(Projekt projekt, boolean bezahlt) {
            ProjektGeschaeftsdokument posten = new ProjektGeschaeftsdokument();
            posten.setProjekt(projekt);
            posten.setGeschaeftsdokumentart("Rechnung");
            posten.setBruttoBetrag(new BigDecimal("1500.00"));
            posten.setBezahlt(bezahlt);
            return posten;
        }

        private AusgangsGeschaeftsDokument angebot(String betrag) {
            AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            angebot.setBetragBrutto(new BigDecimal(betrag));
            angebot.setStorniert(false);
            return angebot;
        }

        private AusgangsGeschaeftsDokument gebuchteRechnung(String betrag) {
            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal(betrag));
            rechnung.setStorniert(false);
            rechnung.setGebucht(true);
            return rechnung;
        }
    }

    @Nested
    class TrageFehlendePreiseNach {

        @Test
        void zaehltNurProjekteDieDurchDenNachlaufEinenPreisBekommen() {
            // Projekt 30 hat eine Rechnung → bekommt einen Preis.
            // Projekt 31 hat gar keine Dokumente → bleibt ohne Preis.
            Projekt mitRechnung = new Projekt();
            mitRechnung.setId(30L);
            mitRechnung.setBruttoPreis(BigDecimal.ZERO);

            Projekt ohneDokumente = new Projekt();
            ohneDokumente.setId(31L);
            ohneDokumente.setBruttoPreis(BigDecimal.ZERO);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setDokumentNummer("2026/07/00099");
            rechnung.setBetragBrutto(new BigDecimal("750.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findIdsOhneBruttoPreis()).thenReturn(List.of(30L, 31L));
            when(projektRepository.findById(30L)).thenReturn(Optional.of(mitRechnung));
            when(projektRepository.findById(31L)).thenReturn(Optional.of(ohneDokumente));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(30L)).thenReturn(List.of(rechnung));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(31L)).thenReturn(List.of());
            when(projektDokumentRepository.existsOffenePostenByProjektId(30L)).thenReturn(false);

            var ergebnis = service.trageFehlendePreiseNach();

            assertThat(ergebnis.geprueft()).isEqualTo(2);
            assertThat(ergebnis.nachgetragen()).isEqualTo(1);
            assertThat(mitRechnung.getBruttoPreis()).isEqualByComparingTo("750.00");
            assertThat(ohneDokumente.getBruttoPreis()).isEqualByComparingTo("0.00");
        }

        @Test
        void machtNichtsWennAlleProjekteEinenPreisHaben() {
            when(projektRepository.findIdsOhneBruttoPreis()).thenReturn(List.of());

            var ergebnis = service.trageFehlendePreiseNach();

            assertThat(ergebnis.geprueft()).isZero();
            assertThat(ergebnis.nachgetragen()).isZero();
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
