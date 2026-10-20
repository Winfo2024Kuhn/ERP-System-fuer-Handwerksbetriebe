package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.KundeLoeschAktion;
import org.example.kalkulationsprogramm.domain.KundeLoeschprotokoll;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Mahnstufe;
import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.dto.Kunde.KundeLoeschPruefungDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeLoeschprotokollRepository;
import org.example.kalkulationsprogramm.repository.KundeNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.SprachnachrichtRepository;
import org.example.kalkulationsprogramm.repository.TelefonAnrufRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class KundeDatenschutzServiceTest {

    @Mock KundeRepository kundeRepository;
    @Mock ProjektRepository projektRepository;
    @Mock AnfrageRepository anfrageRepository;
    @Mock AusgangsGeschaeftsDokumentRepository dokumentRepository;
    @Mock KundeNotizRepository notizRepository;
    @Mock KontaktRufnummerRepository rufnummerRepository;
    @Mock KalenderEintragRepository kalenderRepository;
    @Mock TelefonAnrufRepository anrufRepository;
    @Mock SprachnachrichtRepository sprachnachrichtRepository;
    @Mock KundeLoeschprotokollRepository protokollRepository;
    @Mock DokumentFreigabeRepository freigabeRepository;
    @Mock ProjektDokumentRepository projektDokumentRepository;
    @InjectMocks KundeDatenschutzService service;

    private Kunde kunde() {
        Kunde kunde = new Kunde();
        kunde.setId(7L);
        kunde.setKundennummer("1007");
        kunde.setName("Max Mustermann");
        kunde.setStrasse("Musterweg 1");
        kunde.setTelefon("0931 123456");
        kunde.setMobiltelefon("0170 1234567");
        kunde.setAnsprechspartner("Erika Musterfrau");
        kunde.setKundenEmails(new ArrayList<>(List.of("max@example.com")));
        given(kundeRepository.findById(7L)).willReturn(Optional.of(kunde));
        return kunde;
    }

    private static AusgangsGeschaeftsDokument dokument(long id, AusgangsGeschaeftsDokumentTyp typ, LocalDate datum) {
        AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
        d.setId(id);
        d.setTyp(typ);
        d.setDatum(datum);
        return d;
    }

    private void unterlagen(List<Projekt> projekte, List<Anfrage> anfragen, List<AusgangsGeschaeftsDokument> amKunden) {
        given(projektRepository.findByKundenId_Id(7L)).willReturn(projekte);
        given(anfrageRepository.findByKundeId(7L)).willReturn(anfragen);
        given(dokumentRepository.findByKundeIdOrderByDatumDesc(7L)).willReturn(amKunden);
    }

    private KundeLoeschprotokoll protokoll() {
        ArgumentCaptor<KundeLoeschprotokoll> eintrag = ArgumentCaptor.forClass(KundeLoeschprotokoll.class);
        verify(protokollRepository).save(eintrag.capture());
        return eintrag.getValue();
    }

    @Nested
    class Pruefung {

        @Test
        @DisplayName("Ohne Unterlagen ist der Kunde löschbar")
        void loeschbar() {
            kunde();
            unterlagen(List.of(), List.of(), List.of());

            assertThat(service.pruefe(7L)).isEqualTo(new KundeLoeschPruefungDto(true, List.of(), null, false));
        }

        @Test
        @DisplayName("Rechnung 8 Jahre, Angebot 6 Jahre, jeweils ab Jahresende; die längste Frist zählt")
        void fristen() {
            kunde();
            Projekt projekt = new Projekt();
            projekt.setId(3L);
            projekt.setAnlegedatum(LocalDate.of(2024, 3, 1));
            projekt.setAbschlussdatum(LocalDate.of(2027, 6, 30));
            projekt.setAbgeschlossen(true);
            unterlagen(List.of(projekt), List.of(), List.of(dokument(1L, AusgangsGeschaeftsDokumentTyp.ANGEBOT,
                    LocalDate.of(2026, 1, 15))));
            // Dieselbe Rechnung hängt am Kunden und am Projekt: zählt nur einmal.
            AusgangsGeschaeftsDokument rechnung = dokument(2L, AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG,
                    LocalDate.of(2026, 12, 31));
            given(dokumentRepository.findByProjektIdIn(List.of(3L))).willReturn(List.of(rechnung,
                    dokument(1L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, LocalDate.of(2026, 1, 15))));

            KundeLoeschPruefungDto pruefung = service.pruefe(7L);

            assertThat(pruefung.loeschbar()).isFalse();
            assertThat(pruefung.hindernisse()).containsExactly("1 Projekt", "2 Angebote/Rechnungen");
            // Rechnung 2026 + 8 = 2034; Projektabschluss 2027 + 6 = 2033; Angebot 2026 + 6 = 2032
            assertThat(pruefung.aufbewahrenBis()).isEqualTo(LocalDate.of(2034, 12, 31));
            assertThat(pruefung.anonymisierbar()).isFalse();
        }

        @Test
        @DisplayName("Anfragen zählen als Geschäftsbrief; fehlende Daten nehmen das Erstelldatum bzw. heute")
        void anfrageUndFehlendeDaten() {
            kunde();
            Anfrage anfrage = new Anfrage();
            anfrage.setId(4L);
            anfrage.setAnlegedatum(LocalDate.of(2025, 5, 5));
            AusgangsGeschaeftsDokument ohneDatum = dokument(5L, AusgangsGeschaeftsDokumentTyp.ZWEITE_MAHNUNG, null);
            ohneDatum.setErstelltAm(LocalDateTime.of(2020, 1, 1, 8, 0));
            unterlagen(List.of(), List.of(anfrage, new Anfrage()), List.of(ohneDatum));

            KundeLoeschPruefungDto pruefung = service.pruefe(7L);

            assertThat(pruefung.hindernisse()).containsExactly("2 Anfragen", "1 Angebot/Rechnung");
            // Die Anfrage ohne Datum zählt ab heute – das ist die längste Frist.
            assertThat(pruefung.aufbewahrenBis()).isEqualTo(LocalDate.of(LocalDate.now().getYear() + 6, 12, 31));
        }

        @Test
        @DisplayName("Gesperrt und Frist vorbei: anonymisierbar; Fristende kommt frisch aus den Unterlagen")
        void anonymisierbar() {
            Kunde kunde = kunde();
            kunde.setGesperrtAm(LocalDateTime.of(2016, 2, 1, 8, 0));
            kunde.setAufbewahrenBis(LocalDate.of(2023, 12, 31));
            // Nach der Sperre kam eine Gutschrift dazu: 2016 + 8 = 2024, das gespeicherte Datum ist veraltet.
            unterlagen(List.of(), List.of(), List.of(dokument(1L, AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT,
                    LocalDate.of(2016, 3, 1))));

            KundeLoeschPruefungDto pruefung = service.pruefe(7L);

            assertThat(pruefung.aufbewahrenBis()).isEqualTo(LocalDate.of(2024, 12, 31));
            assertThat(pruefung.anonymisierbar()).isTrue();

            kunde.setAnonymisiertAm(LocalDateTime.now());
            assertThat(service.pruefe(7L).anonymisierbar()).isFalse();
        }

        @Test
        @DisplayName("Gesperrt, aber Frist läuft noch (bzw. endet heute): nicht anonymisierbar")
        void fristLaeuftNoch() {
            kunde().setGesperrtAm(LocalDateTime.now());
            unterlagen(List.of(), List.of(), List.of(dokument(1L, AusgangsGeschaeftsDokumentTyp.RECHNUNG,
                    LocalDate.now().minusYears(8))));

            KundeLoeschPruefungDto pruefung = service.pruefe(7L);

            // Rechnung vor 8 Jahren: Frist endet am 31.12. dieses Jahres, also frühestens morgen frei.
            assertThat(pruefung.aufbewahrenBis()).isEqualTo(LocalDate.of(LocalDate.now().getYear(), 12, 31));
            assertThat(pruefung.anonymisierbar()).isFalse();
        }

        @Test
        @DisplayName("Mahnungen und hochgeladene Rechnungen am Projekt zählen mit")
        void projektBelege() {
            kunde();
            Projekt projekt = new Projekt();
            projekt.setId(3L);
            projekt.setAbschlussdatum(LocalDate.of(2020, 5, 1));
            projekt.setAbgeschlossen(true);
            unterlagen(List.of(projekt), List.of(), List.of());
            ProjektGeschaeftsdokument mahnung = new ProjektGeschaeftsdokument();
            mahnung.setGeschaeftsdokumentart("Mahnung");
            mahnung.setMahnstufe(Mahnstufe.ZWEITE_MAHNUNG);
            mahnung.setRechnungsdatum(LocalDate.of(2029, 2, 1));
            ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
            rechnung.setGeschaeftsdokumentart("Rechnung");
            rechnung.setUploadDatum(LocalDate.of(2021, 3, 3));
            given(projektDokumentRepository.findGeschaeftsdokumenteByProjektIdIn(List.of(3L)))
                    .willReturn(List.of(rechnung, mahnung));

            // Mahnung 2029 + 6 = 2035 schlägt Rechnung 2021 + 8 = 2029 und Projekt 2020 + 6 = 2026.
            assertThat(service.pruefe(7L).aufbewahrenBis()).isEqualTo(LocalDate.of(2035, 12, 31));
            assertThat(KundeDatenschutzService.fristende(rechnung)).isEqualTo(LocalDate.of(2029, 12, 31));
            ProjektGeschaeftsdokument ohneDatum = new ProjektGeschaeftsdokument();
            assertThat(KundeDatenschutzService.fristende(ohneDatum))
                    .isEqualTo(LocalDate.of(LocalDate.now().getYear() + 6, 12, 31));
        }

        @Test
        void fristende() {
            assertThat(KundeDatenschutzService.fristende(LocalDate.of(2026, 1, 1), 8)).isEqualTo(LocalDate.of(2034, 12, 31));
            assertThat(KundeDatenschutzService.fristende(LocalDate.of(2026, 12, 31), 6)).isEqualTo(LocalDate.of(2032, 12, 31));
        }
    }

    @Nested
    class Loeschen {

        @Test
        @DisplayName("Kunde ohne Unterlagen: Nebendaten weg, Kunde gelöscht, Protokoll ohne Namen")
        void loescht() {
            Kunde kunde = kunde();
            unterlagen(List.of(), List.of(), List.of());

            service.loesche(7L, "chef");

            verify(notizRepository).loescheAlleVonKunde(7L);
            verify(rufnummerRepository).loescheAlleVonKunde(7L);
            verify(kalenderRepository).loeseVonKunde(7L);
            verify(anrufRepository).loeseVonKunde(7L);
            verify(sprachnachrichtRepository).loeseVonKunde(7L);
            verify(kundeRepository).delete(kunde);
            KundeLoeschprotokoll eintrag = protokoll();
            assertThat(eintrag.getAktion()).isEqualTo(KundeLoeschAktion.GELOESCHT);
            assertThat(eintrag.getKundeId()).isEqualTo(7L);
            assertThat(eintrag.getKundennummer()).isEqualTo("1007");
            assertThat(eintrag.getAusgefuehrtVon()).isEqualTo("chef");
            assertThat(eintrag.getAusgefuehrtAm()).isNotNull();
        }

        @Test
        @DisplayName("Mit Unterlagen: 409 mit Klartext, nichts wird gelöscht")
        void unterlagenBlockieren() {
            kunde();
            unterlagen(List.of(new Projekt(), new Projekt()), List.of(), List.of());

            assertThatThrownBy(() -> service.loesche(7L, "chef"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getReason()).contains("2 Projekte").contains("Daten löschen und sperren");
                    });
            verify(kundeRepository, never()).delete(any(Kunde.class));
            verifyNoInteractions(notizRepository, protokollRepository);
        }

        @Test
        @DisplayName("Gesperrte Kunden erst nach Fristablauf: 409")
        void gesperrt() {
            kunde().setGesperrtAm(LocalDateTime.now());

            assertThatThrownBy(() -> service.loesche(7L, "chef"))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            verify(kundeRepository, never()).delete(any(Kunde.class));
        }

        @Test
        @DisplayName("Ungültige und unbekannte IDs")
        void ids() {
            for (Long id : new Long[] { null, 0L, -1L }) {
                assertThatThrownBy(() -> service.loesche(id, "chef"))
                        .isInstanceOfSatisfying(ResponseStatusException.class,
                                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
            }
            given(kundeRepository.findById(Long.MAX_VALUE)).willReturn(Optional.empty());
            assertThatThrownBy(() -> service.loesche(Long.MAX_VALUE, "chef"))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    class Sperren {

        @Test
        @DisplayName("Löscht Kontaktdaten, Adressen an Projekten/Anfragen und Nebendaten; Name und Anschrift bleiben")
        void sperrt() {
            Kunde kunde = kunde();
            Projekt projekt = new Projekt();
            projekt.setId(3L);
            projekt.setAnlegedatum(LocalDate.of(2026, 2, 1));
            projekt.setAbgeschlossen(true);
            projekt.setKundenEmails(new ArrayList<>(List.of("max@example.com")));
            Anfrage anfrage = new Anfrage();
            anfrage.setId(4L);
            anfrage.setAnlegedatum(LocalDate.of(2026, 1, 10));
            anfrage.setKundenEmails(new ArrayList<>(List.of("max@example.com")));
            unterlagen(List.of(projekt), List.of(anfrage), List.of(
                    dokument(1L, AusgangsGeschaeftsDokumentTyp.RECHNUNG, LocalDate.of(2026, 3, 1))));

            service.sperre(7L, "  Löschwunsch per E-Mail  ", "chef");

            assertThat(kunde.getTelefon()).isNull();
            assertThat(kunde.getMobiltelefon()).isNull();
            assertThat(kunde.getAnsprechspartner()).isNull();
            assertThat(kunde.getKundenEmails()).isEmpty();
            assertThat(projekt.getKundenEmails()).isEmpty();
            assertThat(anfrage.getKundenEmails()).isEmpty();
            assertThat(kunde.getName()).isEqualTo("Max Mustermann");
            assertThat(kunde.getStrasse()).isEqualTo("Musterweg 1");
            assertThat(kunde.istGesperrt()).isTrue();
            assertThat(kunde.getAufbewahrenBis()).isEqualTo(LocalDate.of(2034, 12, 31));
            verify(notizRepository).loescheAlleVonKunde(7L);
            verify(rufnummerRepository).loescheAlleVonKunde(7L);
            verify(kalenderRepository).loeseVonKunde(7L);
            verify(anrufRepository).loeseVonKunde(7L);
            verify(sprachnachrichtRepository).loeseVonKunde(7L);
            verify(kundeRepository).save(kunde);
            verify(kundeRepository, never()).delete(any(Kunde.class));
            KundeLoeschprotokoll eintrag = protokoll();
            assertThat(eintrag.getAktion()).isEqualTo(KundeLoeschAktion.GESPERRT);
            assertThat(eintrag.getGrund()).isEqualTo("Löschwunsch per E-Mail");
            assertThat(eintrag.getAufbewahrenBis()).isEqualTo(LocalDate.of(2034, 12, 31));
        }

        @Test
        @DisplayName("Schon gesperrt: bleibt unverändert, kein zweiter Protokolleintrag")
        void schonGesperrt() {
            Kunde kunde = kunde();
            kunde.setGesperrtAm(LocalDateTime.of(2026, 1, 1, 8, 0));

            service.sperre(7L, null, "chef");

            assertThat(kunde.getGesperrtAm()).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 0));
            verifyNoInteractions(protokollRepository, notizRepository, projektRepository);
        }

        @Test
        @DisplayName("Ohne Unterlagen gibt es nichts aufzubewahren: 409, bitte direkt löschen")
        void nichtsAufzubewahren() {
            Kunde kunde = kunde();
            unterlagen(List.of(), List.of(), List.of());

            assertThatThrownBy(() -> service.sperre(7L, "", "chef"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getReason()).contains("direkt löschen");
                    });
            assertThat(kunde.istGesperrt()).isFalse();
            assertThat(kunde.getTelefon()).isNotNull();
        }

        @Test
        @DisplayName("Laufende Projekte: 409 – eine spätere Schlussrechnung würde die Frist verlängern")
        void laufendesProjekt() {
            Kunde kunde = kunde();
            Projekt laufend = new Projekt();
            laufend.setId(3L);
            Projekt fertig = new Projekt();
            fertig.setId(4L);
            fertig.setAbgeschlossen(true);
            unterlagen(List.of(laufend, fertig), List.of(), List.of());

            assertThat(service.pruefe(7L).hindernisse()).containsExactly("2 Projekte (1 noch nicht abgeschlossen)");
            assertThatThrownBy(() -> service.sperre(7L, null, "chef"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getReason()).startsWith("Bei diesem Kunden läuft noch 1 Projekt.");
                    });
            assertThat(kunde.istGesperrt()).isFalse();
            verifyNoInteractions(protokollRepository, notizRepository);
        }

        @Test
        @DisplayName("Leerer Grund wird nicht gespeichert")
        void leererGrund() {
            kunde();
            Projekt fertig = new Projekt();
            fertig.setAbgeschlossen(true);
            unterlagen(List.of(fertig), List.of(), List.of());

            service.sperre(7L, "   ", null);

            assertThat(protokoll().getGrund()).isNull();
        }
    }

    @Nested
    class Anonymisieren {

        private Kunde gesperrterKunde() {
            Kunde kunde = kunde();
            kunde.setAnrede(Anrede.HERR);
            kunde.setPlz("97070");
            kunde.setOrt("Würzburg");
            kunde.setGesperrtAm(LocalDateTime.of(2016, 2, 1, 8, 0));
            kunde.setAufbewahrenBis(LocalDate.of(2024, 12, 31));
            return kunde;
        }

        private DokumentFreigabe freigabe(FreigabeStatus status) {
            DokumentFreigabe f = new DokumentFreigabe();
            f.setStatus(status);
            f.setKundeName("Max Mustermann");
            f.setKundeEmail("max@example.com");
            return f;
        }

        @Test
        @DisplayName("Nach Fristende: Name, Anrede, Anschrift, Baustellen-Anschrift und Freigabe-Namen weg; Belege bleiben")
        void anonymisiert() {
            Kunde kunde = gesperrterKunde();
            Projekt projekt = new Projekt();
            projekt.setId(3L);
            projekt.setBauvorhaben("Carport Mustermann");
            projekt.setStrasse("Baustraße 2");
            projekt.setPlz("97070");
            projekt.setOrt("Würzburg");
            projekt.setAbschlussdatum(LocalDate.of(2016, 1, 10));
            projekt.setAbgeschlossen(true);
            projekt.setKundenEmails(new ArrayList<>(List.of("max@example.com")));
            Anfrage anfrage = new Anfrage();
            anfrage.setId(4L);
            anfrage.setAnlegedatum(LocalDate.of(2015, 6, 1));
            anfrage.setProjektStrasse("Baustraße 2");
            anfrage.setProjektPlz("97070");
            anfrage.setProjektOrt("Würzburg");
            AusgangsGeschaeftsDokument rechnung = dokument(1L, AusgangsGeschaeftsDokumentTyp.RECHNUNG,
                    LocalDate.of(2016, 1, 15));
            rechnung.setHtmlInhalt("<p>Max Mustermann, Musterweg 1</p>");
            unterlagen(List.of(projekt), List.of(anfrage), List.of(rechnung));
            DokumentFreigabe offen = freigabe(FreigabeStatus.EXPIRED);
            DokumentFreigabe angenommen = freigabe(FreigabeStatus.ACCEPTED);
            DokumentFreigabe anfrageFreigabe = freigabe(FreigabeStatus.REVOKED);
            // Code bestätigt, aber nie angenommen: auch diese Angaben gehen weg.
            anfrageFreigabe.setCodeBestaetigtEmail("max@example.com");
            anfrageFreigabe.setUnterzeichnerVorname("Max");
            anfrageFreigabe.setUnterzeichnerNachname("Mustermann");
            anfrageFreigabe.setUnterzeichnerName("Max Mustermann");
            angenommen.setUnterzeichnerName("Max Mustermann");
            given(freigabeRepository.findByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, List.of(1L)))
                    .willReturn(List.of(offen));
            given(freigabeRepository.findFuerProjekte(List.of(3L))).willReturn(List.of(angenommen));
            given(freigabeRepository.findFuerAnfragen(List.of(4L))).willReturn(List.of(anfrageFreigabe));

            service.anonymisiere(7L, "chef");

            assertThat(kunde.getName()).isEqualTo("Gelöschter Kunde 1007");
            assertThat(kunde.getAnrede()).isNull();
            assertThat(kunde.getStrasse()).isNull();
            assertThat(kunde.getPlz()).isNull();
            assertThat(kunde.getOrt()).isNull();
            assertThat(kunde.getTelefon()).isNull();
            assertThat(kunde.getAnsprechspartner()).isNull();
            assertThat(kunde.getKundenEmails()).isEmpty();
            assertThat(kunde.getKundennummer()).isEqualTo("1007");
            assertThat(kunde.istAnonymisiert()).isTrue();
            assertThat(kunde.getAufbewahrenBis()).isEqualTo(LocalDate.of(2024, 12, 31));
            assertThat(projekt.getStrasse()).isNull();
            assertThat(projekt.getPlz()).isNull();
            assertThat(projekt.getOrt()).isNull();
            assertThat(projekt.getKundenEmails()).isEmpty();
            assertThat(projekt.getBauvorhaben()).isEqualTo("Carport Mustermann");
            assertThat(anfrage.getProjektStrasse()).isNull();
            assertThat(anfrage.getProjektPlz()).isNull();
            assertThat(anfrage.getProjektOrt()).isNull();
            // Belege bleiben unverändert (unbegrenzte Aufbewahrung, GoBD).
            assertThat(rechnung.getHtmlInhalt()).isEqualTo("<p>Max Mustermann, Musterweg 1</p>");
            assertThat(offen.getKundeName()).isNull();
            assertThat(offen.getKundeEmail()).isNull();
            assertThat(anfrageFreigabe.getKundeName()).isNull();
            assertThat(anfrageFreigabe.getKundeEmail()).isNull();
            assertThat(anfrageFreigabe.getCodeBestaetigtEmail()).isNull();
            assertThat(anfrageFreigabe.getUnterzeichnerVorname()).isNull();
            assertThat(anfrageFreigabe.getUnterzeichnerNachname()).isNull();
            assertThat(anfrageFreigabe.getUnterzeichnerName()).isNull();
            // Angenommen: E-Mail ist Teil des Auftragsnachweises und bleibt.
            assertThat(angenommen.getKundeName()).isNull();
            assertThat(angenommen.getKundeEmail()).isEqualTo("max@example.com");
            assertThat(angenommen.getUnterzeichnerName()).isEqualTo("Max Mustermann");
            verify(kundeRepository).save(kunde);
            KundeLoeschprotokoll eintrag = protokoll();
            assertThat(eintrag.getAktion()).isEqualTo(KundeLoeschAktion.ANONYMISIERT);
            assertThat(eintrag.getKundennummer()).isEqualTo("1007");
            assertThat(eintrag.getGrund()).isNull();
            assertThat(eintrag.getAufbewahrenBis()).isEqualTo(LocalDate.of(2024, 12, 31));
            assertThat(eintrag.getAusgefuehrtVon()).isEqualTo("chef");
        }

        @Test
        @DisplayName("Ohne Unterlagen genügt die Sperre; Freigaben werden nicht gesucht")
        void ohneUnterlagen() {
            Kunde kunde = gesperrterKunde();
            unterlagen(List.of(), List.of(), List.of());

            service.anonymisiere(7L, null);

            assertThat(kunde.istAnonymisiert()).isTrue();
            verifyNoInteractions(freigabeRepository);
        }

        @Test
        @DisplayName("Gutschrift nach der Sperre verlängert die Frist: 409 mit dem neuen Datum, nichts geändert")
        void fristLaeuftNoch() {
            Kunde kunde = gesperrterKunde();
            int jahr = LocalDate.now().getYear();
            unterlagen(List.of(), List.of(), List.of(
                    dokument(1L, AusgangsGeschaeftsDokumentTyp.RECHNUNG, LocalDate.of(2016, 1, 15)),
                    dokument(2L, AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT, LocalDate.of(jahr - 1, 5, 1))));

            assertThatThrownBy(() -> service.anonymisiere(7L, "chef"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getReason()).contains("31.12." + (jahr + 7));
                    });
            assertThat(kunde.getName()).isEqualTo("Max Mustermann");
            assertThat(kunde.istAnonymisiert()).isFalse();
            verify(kundeRepository, never()).save(any(Kunde.class));
            verifyNoInteractions(protokollRepository, freigabeRepository);
        }

        @Test
        @DisplayName("Nicht gesperrt: 409, nichts geändert")
        void nichtGesperrt() {
            Kunde kunde = kunde();

            assertThatThrownBy(() -> service.anonymisiere(7L, "chef"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getReason()).contains("Nur gesperrte Kunden");
                    });
            assertThat(kunde.getName()).isEqualTo("Max Mustermann");
            verifyNoInteractions(protokollRepository, projektRepository);
        }

        @Test
        @DisplayName("Schon anonymisiert: bleibt unverändert, kein zweiter Protokolleintrag")
        void schonAnonymisiert() {
            Kunde kunde = gesperrterKunde();
            LocalDateTime damals = LocalDateTime.of(2025, 3, 1, 10, 0);
            kunde.setAnonymisiertAm(damals);

            service.anonymisiere(7L, "chef");

            assertThat(kunde.getAnonymisiertAm()).isEqualTo(damals);
            verifyNoInteractions(protokollRepository, projektRepository);
        }

        @Test
        @DisplayName("Ungültige und unbekannte IDs")
        void ids() {
            for (Long id : new Long[] { null, 0L, -1L }) {
                assertThatThrownBy(() -> service.anonymisiere(id, "chef"))
                        .isInstanceOfSatisfying(ResponseStatusException.class,
                                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
            }
            given(kundeRepository.findById(Long.MAX_VALUE)).willReturn(Optional.empty());
            assertThatThrownBy(() -> service.anonymisiere(Long.MAX_VALUE, "chef"))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    class FristNachziehen {

        @Test
        @DisplayName("Gutschrift nach der Sperre verlängert die Frist; kürzere Mahnung nicht")
        void gutschriftVerlaengert() {
            Kunde kunde = new Kunde();
            kunde.setGesperrtAm(LocalDateTime.of(2026, 1, 5, 9, 0));
            kunde.setAufbewahrenBis(LocalDate.of(2033, 12, 31));

            assertThat(KundeDatenschutzService.verlaengereAufbewahrung(kunde,
                    dokument(1L, AusgangsGeschaeftsDokumentTyp.ZAHLUNGSERINNERUNG, LocalDate.of(2027, 2, 1)))).isFalse();
            assertThat(kunde.getAufbewahrenBis()).isEqualTo(LocalDate.of(2033, 12, 31));

            assertThat(KundeDatenschutzService.verlaengereAufbewahrung(kunde,
                    dokument(2L, AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT, LocalDate.of(2027, 2, 1)))).isTrue();
            assertThat(kunde.getAufbewahrenBis()).isEqualTo(LocalDate.of(2035, 12, 31));
        }

        @Test
        @DisplayName("Nicht gesperrt, ohne Kunde oder ohne gespeichertes Fristende")
        void sonderfaelle() {
            AusgangsGeschaeftsDokument storno = dokument(1L, AusgangsGeschaeftsDokumentTyp.STORNO, LocalDate.of(2027, 2, 1));
            Kunde offen = new Kunde();
            assertThat(KundeDatenschutzService.verlaengereAufbewahrung(offen, storno)).isFalse();
            assertThat(offen.getAufbewahrenBis()).isNull();
            assertThat(KundeDatenschutzService.verlaengereAufbewahrung(null, storno)).isFalse();

            Kunde gesperrt = new Kunde();
            gesperrt.setGesperrtAm(LocalDateTime.now());
            assertThat(KundeDatenschutzService.verlaengereAufbewahrung(gesperrt, null)).isFalse();
            assertThat(KundeDatenschutzService.verlaengereAufbewahrung(gesperrt, storno)).isTrue();
            assertThat(gesperrt.getAufbewahrenBis()).isEqualTo(LocalDate.of(2035, 12, 31));

            gesperrt.setAufbewahrenBis(LocalDate.of(2020, 12, 31));
            gesperrt.setAnonymisiertAm(LocalDateTime.now());
            assertThat(KundeDatenschutzService.verlaengereAufbewahrung(gesperrt, storno)).isFalse();
            assertThat(gesperrt.getAufbewahrenBis()).isEqualTo(LocalDate.of(2020, 12, 31));
        }

        @Test
        @DisplayName("Aktuelles Fristende für die Anzeige kommt frisch aus den Unterlagen")
        void aktuellesFristende() {
            kunde().setGesperrtAm(LocalDateTime.of(2026, 1, 5, 9, 0));
            unterlagen(List.of(), List.of(), List.of(dokument(1L, AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT,
                    LocalDate.of(2027, 2, 1))));

            assertThat(service.aktuellesFristende(7L)).isEqualTo(LocalDate.of(2035, 12, 31));
        }
    }

    @Test
    @DisplayName("Neue Unterlagen für anonymisierte Kunden: 409; gesperrt oder ohne Kunde ok")
    void pruefeNichtAnonymisiert() {
        Kunde gesperrt = new Kunde();
        gesperrt.setGesperrtAm(LocalDateTime.now());
        KundeDatenschutzService.pruefeNichtAnonymisiert(gesperrt);
        KundeDatenschutzService.pruefeNichtAnonymisiert(null);

        gesperrt.setAnonymisiertAm(LocalDateTime.now());
        assertThatThrownBy(() -> KundeDatenschutzService.pruefeNichtAnonymisiert(gesperrt))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("anonymisiert");
                });
    }

    @Test
    @DisplayName("Änderungen an gesperrten Kunden: 409; ohne Kunde nichts zu prüfen")
    void pruefeNichtGesperrt() {
        Kunde offen = new Kunde();
        KundeDatenschutzService.pruefeNichtGesperrt(offen);
        KundeDatenschutzService.pruefeNichtGesperrt(null);

        Kunde gesperrt = new Kunde();
        gesperrt.setGesperrtAm(LocalDateTime.now());
        assertThatThrownBy(() -> KundeDatenschutzService.pruefeNichtGesperrt(gesperrt))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
