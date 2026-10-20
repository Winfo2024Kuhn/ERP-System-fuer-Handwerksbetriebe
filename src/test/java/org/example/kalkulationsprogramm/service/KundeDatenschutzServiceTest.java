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
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.KundeLoeschAktion;
import org.example.kalkulationsprogramm.domain.KundeLoeschprotokoll;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Kunde.KundeLoeschPruefungDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeLoeschprotokollRepository;
import org.example.kalkulationsprogramm.repository.KundeNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
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
            unterlagen(List.of(), List.of(), List.of());

            assertThat(service.pruefe(7L)).isEqualTo(new KundeLoeschPruefungDto(true, List.of(), null));
        }

        @Test
        @DisplayName("Rechnung 8 Jahre, Angebot 6 Jahre, jeweils ab Jahresende; die längste Frist zählt")
        void fristen() {
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
        }

        @Test
        @DisplayName("Anfragen zählen als Geschäftsbrief; fehlende Daten nehmen das Erstelldatum bzw. heute")
        void anfrageUndFehlendeDaten() {
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
