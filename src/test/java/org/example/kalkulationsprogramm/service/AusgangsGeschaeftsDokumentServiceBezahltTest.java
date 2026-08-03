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

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("1000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(10L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(10L))
                    .thenReturn(List.of(angebot, rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(10L)).thenReturn(false);

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
        void nimmtRechnungssummeAlsPreisWennEsKeinAngebotUndKeineAbGibt() {
            // Reparaturauftrag: Es wird direkt eine Rechnung im Programm geschrieben,
            // ohne vorheriges Angebot/AB. Vorher blieb der Bruttopreis auf 0 stehen
            // und musste von Hand nachgetragen werden.
            Projekt projekt = new Projekt();
            projekt.setId(20L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setDokumentNummer("2026/07/00001");
            rechnung.setBetragBrutto(new BigDecimal("1190.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(20L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(20L)).thenReturn(List.of(rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(20L)).thenReturn(false);

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

            service.aktualisiereProjektPreisAusDokumenten(26L);

            assertThat(projekt.getBruttoPreis()).isEqualByComparingTo("2000.00");
            assertThat(projekt.isBezahlt()).isTrue();
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
