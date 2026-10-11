package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsPostfachDto;
import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsnotizDto;
import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsnotizSpeichernRequest;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachRefDto;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Abwesenheitsnotizen pro Postfach: Mitarbeiter nur für das eigene und allgemeine Postfächer,
 * Admins für alle. Dummy-Daten: Max Mustermann, Erika Musterfrau.
 */
@ExtendWith(MockitoExtension.class)
class AbwesenheitsnotizServiceTest {

    @Mock private OutOfOfficeScheduleRepository notizRepository;
    @Mock private EmailAbsenderRepository postfachRepository;
    @Mock private EmailSignatureRepository signaturRepository;
    @Mock private FrontendUserProfileRepository benutzerRepository;
    @Mock private FrontendUserProfileService benutzerService;
    @Mock private PostfachSichtbarkeitService sichtbarkeitService;
    @InjectMocks private AbwesenheitsnotizService service;

    private static final Authentication MAX = new UsernamePasswordAuthenticationToken("max", "x",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    private static final Authentication ADMIN = new UsernamePasswordAuthenticationToken("chef", "x",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    // info@ = Hauptpostfach, buero@ = allgemein, max@ = Max, erika@ = Erika,
    // rechnung@ = reines Rechnungs-Postfach, alt@ = läuft aus, geheim@ = allgemein, aber für Max unsichtbar
    private EmailAbsender info;
    private EmailAbsender buero;
    private EmailAbsender max;
    private EmailAbsender erika;
    private EmailAbsender rechnung;
    private EmailAbsender alt;
    private EmailAbsender geheim;

    private static EmailAbsender postfach(long id, String adresse) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse(adresse);
        p.setSortierung((int) id);
        return p;
    }

    @BeforeEach
    void setUp() {
        info = postfach(1, "info@example.com");
        info.setHauptpostfach(true);
        buero = postfach(2, "buero@example.com");
        max = postfach(3, "max.mustermann@example.com");
        erika = postfach(4, "erika.musterfrau@example.com");
        rechnung = postfach(5, "rechnung@example.com");
        rechnung.setFuerGeschaeftsdokumente(true);
        alt = postfach(6, "alt@example.com");
        alt.setLaeuftAus(true);
        geheim = postfach(8, "geheim@example.com");

        List<EmailAbsender> alle = List.of(info, buero, max, erika, rechnung, alt, geheim);
        lenient().when(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()).thenReturn(alle);
        for (EmailAbsender p : alle) {
            lenient().when(postfachRepository.findById(p.getId())).thenReturn(Optional.of(p));
        }
        lenient().when(benutzerRepository.findEmailAbsenderIdsMitBesitzer()).thenReturn(List.of(3L, 4L));

        FrontendUserProfile maxProfil = new FrontendUserProfile();
        maxProfil.setId(30L);
        maxProfil.setEmailAbsender(max);
        lenient().when(benutzerService.findByUsername("max")).thenReturn(Optional.of(maxProfil));
        lenient().when(benutzerService.findByUsername("chef")).thenReturn(Optional.empty());
        // Max sieht alles außer geheim@ (Erikas Postfach ist ihm freigegeben – bleibt trotzdem ihres).
        lenient().when(sichtbarkeitService.fuer(MAX))
                .thenReturn(new PostfachSichtbarkeit(false, Set.of(1L, 2L, 3L, 4L, 5L, 6L)));
        lenient().when(notizRepository.save(any())).thenAnswer(inv -> {
            OutOfOfficeSchedule n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId(99L);
            }
            return n;
        });
    }

    private static AbwesenheitsnotizSpeichernRequest anfrage(Long id, Long... postfachIds) {
        return new AbwesenheitsnotizSpeichernRequest(id, "Urlaub", LocalDate.of(2026, 12, 21),
                LocalDate.of(2027, 1, 6), null, true, null, "Bin im Urlaub.",
                postfachIds == null ? null : new ArrayList<>(Arrays.asList(postfachIds)));
    }

    private static OutOfOfficeSchedule notiz(long id, EmailAbsender... postfaecher) {
        OutOfOfficeSchedule n = new OutOfOfficeSchedule();
        n.setId(id);
        n.setTitle("Notiz " + id);
        n.setStartAt(LocalDate.of(2026, 12, 1));
        n.setEndAt(LocalDate.of(2026, 12, 31));
        n.getPostfaecher().addAll(List.of(postfaecher));
        return n;
    }

    private static List<Long> ids(List<AbwesenheitsPostfachDto> postfaecher) {
        return postfaecher.stream().map(AbwesenheitsPostfachDto::id).toList();
    }

    @Nested
    class WaehlbarePostfaecher {

        @Test
        void mitarbeiter_eigenesZuerstDannAllgemeineDieErSieht() {
            List<AbwesenheitsPostfachDto> ergebnis = service.waehlbarePostfaecher(MAX);

            assertThat(ids(ergebnis)).containsExactly(3L, 1L, 2L);
            assertThat(ergebnis.get(0).eigenes()).isTrue();
            assertThat(ergebnis.get(1).eigenes()).isFalse();
        }

        @Test
        void admin_alleDieAntwortenKoennen() {
            assertThat(ids(service.waehlbarePostfaecher(ADMIN))).containsExactly(1L, 2L, 3L, 4L, 8L);
        }

        @Test
        void rechnungsPostfachAlsHauptpostfachIstWaehlbar() {
            info.setFuerGeschaeftsdokumente(true);

            assertThat(ids(service.waehlbarePostfaecher(MAX))).contains(1L);
        }

        @Test
        void ohneAnmeldung_nurAllgemeineSichtbare() {
            when(sichtbarkeitService.fuer(null)).thenReturn(new PostfachSichtbarkeit(false, Set.of(1L)));

            assertThat(ids(service.waehlbarePostfaecher(null))).containsExactly(1L);
        }
    }

    @Nested
    class Liste {

        @Test
        void mitarbeiterSiehtNurNotizenDieErBearbeitenDarf() {
            when(notizRepository.findAllByOrderByStartAtDescIdDesc()).thenReturn(List.of(
                    notiz(1, max), notiz(2, info, buero), notiz(3, erika), notiz(4, max, erika), notiz(5)));

            List<AbwesenheitsnotizDto> liste = service.liste(MAX);

            assertThat(liste).extracting(AbwesenheitsnotizDto::id).containsExactly(1L, 2L);
            assertThat(liste.get(1).postfaecher()).extracting(PostfachRefDto::emailAdresse)
                    .containsExactly("info@example.com", "buero@example.com");
        }

        @Test
        void adminSiehtAlle() {
            when(notizRepository.findAllByOrderByStartAtDescIdDesc()).thenReturn(List.of(notiz(3, erika), notiz(5)));

            assertThat(service.liste(ADMIN)).hasSize(2);
        }
    }

    @Nested
    class Speichern {

        @Test
        void mitarbeiterLegtNotizFuerEigenesUndAllgemeinesAn() {
            EmailSignature signatur = new EmailSignature();
            signatur.setId(11L);
            signatur.setName("Max Mustermann");
            when(signaturRepository.findById(11L)).thenReturn(Optional.of(signatur));
            AbwesenheitsnotizSpeichernRequest anfrage = new AbwesenheitsnotizSpeichernRequest(null, "  Urlaub ",
                    LocalDate.of(2026, 12, 21), LocalDate.of(2027, 1, 6), 11L, true, "  ", "<p>Bin weg</p>",
                    List.of(3L, 1L, 3L));

            AbwesenheitsnotizDto dto = service.speichern(anfrage, MAX);

            assertThat(dto.id()).isEqualTo(99L);
            assertThat(dto.title()).isEqualTo("Urlaub");
            assertThat(dto.subjectTemplate()).isEqualTo(AbwesenheitsnotizService.STANDARD_BETREFF);
            assertThat(dto.signature().name()).isEqualTo("Max Mustermann");
            assertThat(dto.postfaecher()).extracting(PostfachRefDto::id).containsExactly(1L, 3L);
        }

        @Test
        void fremdesPersoenlichesPostfach403() {
            assertThatThrownBy(() -> service.speichern(anfrage(null, 3L, 4L), MAX))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("eigenes Postfach");
            verify(notizRepository, never()).save(any());
        }

        @Test
        void unsichtbaresAllgemeinesPostfach403() {
            assertThatThrownBy(() -> service.speichern(anfrage(null, 8L), MAX))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void adminDarfJedesPostfach() {
            AbwesenheitsnotizDto dto = service.speichern(anfrage(null, 4L, 8L), ADMIN);

            assertThat(dto.postfaecher()).extracting(PostfachRefDto::id).containsExactly(4L, 8L);
        }

        @Test
        void auslaufendesOderRechnungsPostfach400() {
            assertThatThrownBy(() -> service.speichern(anfrage(null, 6L), ADMIN))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Postfach");
            assertThatThrownBy(() -> service.speichern(anfrage(null, 5L), ADMIN))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void unbekanntesPostfach400() {
            when(postfachRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.speichern(anfrage(null, Long.MAX_VALUE), MAX))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("gibt es nicht");
        }

        @Test
        void ohnePostfach400() {
            assertThatThrownBy(() -> service.speichern(anfrage(null), MAX))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Bitte mindestens ein Postfach auswählen.");
            assertThatThrownBy(() -> service.speichern(anfrage(null, (Long[]) null), MAX))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void pflichtfelderUndLaengen400() {
            LocalDate a = LocalDate.of(2026, 12, 21);
            LocalDate b = LocalDate.of(2027, 1, 6);
            List<Long> p = List.of(3L);
            List<AbwesenheitsnotizSpeichernRequest> ungueltig = List.of(
                    new AbwesenheitsnotizSpeichernRequest(null, " ", a, b, null, true, null, null, p),
                    new AbwesenheitsnotizSpeichernRequest(null, "x".repeat(201), a, b, null, true, null, null, p),
                    new AbwesenheitsnotizSpeichernRequest(null, "Urlaub", null, b, null, true, null, null, p),
                    new AbwesenheitsnotizSpeichernRequest(null, "Urlaub", a, null, null, true, null, null, p),
                    new AbwesenheitsnotizSpeichernRequest(null, "Urlaub", b, a, null, true, null, null, p),
                    new AbwesenheitsnotizSpeichernRequest(null, "Urlaub", a, b, null, true, "x".repeat(301), null, p),
                    new AbwesenheitsnotizSpeichernRequest(null, "Urlaub", a, b, null, true, null, "x".repeat(100_001), p));
            for (AbwesenheitsnotizSpeichernRequest r : ungueltig) {
                assertThatThrownBy(() -> service.speichern(r, MAX)).isInstanceOf(IllegalArgumentException.class);
            }
            assertThatThrownBy(() -> service.speichern(null, MAX)).isInstanceOf(IllegalArgumentException.class);
            verify(notizRepository, never()).save(any());
        }

        @Test
        void unbekannteSignatur400() {
            when(signaturRepository.findById(77L)).thenReturn(Optional.empty());
            AbwesenheitsnotizSpeichernRequest r = new AbwesenheitsnotizSpeichernRequest(null, "Urlaub",
                    LocalDate.of(2026, 12, 21), LocalDate.of(2027, 1, 6), 77L, true, null, null, List.of(3L));

            assertThatThrownBy(() -> service.speichern(r, MAX)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void bestehendeNotizAendernErsetztPostfaecher() {
            OutOfOfficeSchedule bestehend = notiz(10, max, info);
            when(notizRepository.findById(10L)).thenReturn(Optional.of(bestehend));

            AbwesenheitsnotizDto dto = service.speichern(anfrage(10L, 2L), MAX);

            assertThat(dto.id()).isEqualTo(10L);
            assertThat(bestehend.getPostfaecher()).containsExactly(buero);
        }

        @Test
        void notizEinesKollegen_wieNichtVorhanden404() {
            when(notizRepository.findById(11L)).thenReturn(Optional.of(notiz(11, erika)));

            assertThatThrownBy(() -> service.speichern(anfrage(11L, 3L), MAX))
                    .isInstanceOf(NoSuchElementException.class);
            verify(notizRepository, never()).save(any());
        }

        @Test
        void unbekannteNotiz404() {
            when(notizRepository.findById(-1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.speichern(anfrage(-1L, 3L), MAX))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    class Nachricht {

        @Test
        void gefaehrlichesHtmlWirdBeimSpeichernEntfernt() {
            AbwesenheitsnotizSpeichernRequest r = new AbwesenheitsnotizSpeichernRequest(null, "Urlaub",
                    LocalDate.of(2026, 12, 21), LocalDate.of(2027, 1, 6), null, true, null,
                    "<p>Bin weg</p><img src=x onerror=\"alert(1)\"><script>alert(2)</script>", List.of(3L));

            AbwesenheitsnotizDto dto = service.speichern(r, MAX);

            assertThat(dto.bodyTemplate()).contains("<p>Bin weg</p>")
                    .doesNotContain("onerror").doesNotContain("<script");
        }

        @Test
        void reinerTextBleibtUnveraendert() {
            AbwesenheitsnotizSpeichernRequest r = new AbwesenheitsnotizSpeichernRequest(null, "Urlaub",
                    LocalDate.of(2026, 12, 21), LocalDate.of(2027, 1, 6), null, true, null,
                    "Bin vom {{start}} bis {{ende}} weg.\nGruß, Max & Team 1 < 2", List.of(3L));

            assertThat(service.speichern(r, MAX).bodyTemplate())
                    .isEqualTo("Bin vom {{start}} bis {{ende}} weg.\nGruß, Max & Team 1 < 2");
        }

        @Test
        void htmlErkennungBleibtBeiLangenTextenSchnell() {
            String boese = "<a".repeat(50_000);
            long start = System.nanoTime();

            assertThat(AbwesenheitsnotizService.siehtNachHtmlAus(boese)).isFalse();
            assertThat(AbwesenheitsnotizService.siehtNachHtmlAus(boese + ">")).isTrue();
            assertThat(System.nanoTime() - start).isLessThan(2_000_000_000L);
            assertThat(AbwesenheitsnotizService.siehtNachHtmlAus(null)).isFalse();
            assertThat(AbwesenheitsnotizService.siehtNachHtmlAus("1 < 2 und 3 > 2")).isFalse();
        }
    }

    @Nested
    class Loeschen {

        @Test
        void eigeneNotiz() {
            OutOfOfficeSchedule eigene = notiz(1, max);
            when(notizRepository.findById(1L)).thenReturn(Optional.of(eigene));

            service.loeschen(1L, MAX);

            verify(notizRepository).delete(eigene);
        }

        @Test
        void notizEinesKollegen404() {
            when(notizRepository.findById(2L)).thenReturn(Optional.of(notiz(2, erika)));

            assertThatThrownBy(() -> service.loeschen(2L, MAX)).isInstanceOf(NoSuchElementException.class);
            verify(notizRepository, never()).delete(any());
        }

        @Test
        void altNotizOhnePostfach_nurAdmin() {
            OutOfOfficeSchedule alt = notiz(3);
            when(notizRepository.findById(3L)).thenReturn(Optional.of(alt));

            assertThatThrownBy(() -> service.loeschen(3L, MAX)).isInstanceOf(NoSuchElementException.class);
            service.loeschen(3L, ADMIN);
            verify(notizRepository).delete(alt);
        }

        @Test
        void unbekannt404() {
            when(notizRepository.findById(0L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.loeschen(0L, ADMIN)).isInstanceOf(NoSuchElementException.class);
        }
    }
}
