package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

/** Wer sieht welches Postfach? Alle Zweige der Regel aus Spec 2.1. */
@ExtendWith(MockitoExtension.class)
class PostfachSichtbarkeitServiceTest {

    @Mock private EmailAbsenderRepository postfachRepository;
    @Mock private EmailPostfachZuordnungRepository zuordnungRepository;
    @Mock private FrontendUserProfileService frontendUserProfileService;
    @Mock private org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;

    @InjectMocks private PostfachSichtbarkeitService service;

    private final EmailAbsender info = postfach(1L, true, true);
    private final EmailAbsender rechnungen = postfach(2L, false, false);
    private final EmailAbsender max = postfach(3L, false, false);
    private final EmailAbsender werkstatt = postfach(4L, false, false);
    private final EmailAbsender allgemein = postfach(5L, false, true);

    @BeforeEach
    void postfaecher() {
        lenient().when(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc())
                .thenReturn(List.of(info, rechnungen, max, werkstatt, allgemein));
    }

    private static EmailAbsender postfach(Long id, boolean haupt, boolean fuerAlle) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse("postfach" + id + "@example.com");
        p.setHauptpostfach(haupt);
        p.setSichtbarFuerAlle(fuerAlle);
        return p;
    }

    private static Authentication angemeldet(String name, String... rollen) {
        return new UsernamePasswordAuthenticationToken(name, null, AuthorityUtils.createAuthorityList(rollen));
    }

    private static FrontendUserProfile benutzer(Long id, EmailAbsender eigenes, Long... abteilungIds) {
        FrontendUserProfile u = new FrontendUserProfile();
        u.setId(id);
        u.setDisplayName("Max Mustermann");
        u.setUsername("max");
        u.setEmailAbsender(eigenes);
        if (abteilungIds.length > 0) {
            Mitarbeiter m = new Mitarbeiter();
            for (Long abteilungId : abteilungIds) {
                Abteilung a = new Abteilung();
                a.setId(abteilungId);
                a.setName("Abteilung " + abteilungId);
                m.getAbteilungen().add(a);
            }
            u.setMitarbeiter(m);
        }
        return u;
    }

    @Nested
    class Admin {

        @Test
        void adminSiehtAllesOhneAbfrage() {
            assertThat(service.fuer(angemeldet("chefin", "ROLE_ADMIN", "ROLE_USER"))).isEqualTo(PostfachSichtbarkeit.ALLES);
            verifyNoInteractions(postfachRepository, frontendUserProfileService);
        }

        @Test
        void rolleUserIstKeinAdmin() {
            assertThat(PostfachSichtbarkeitService.istAdmin(angemeldet("max", "ROLE_USER"))).isFalse();
            assertThat(PostfachSichtbarkeitService.istAdmin(null)).isFalse();
        }

        @Test
        void anonymMitAdminRolleZaehltNicht() {
            Authentication anonym = new AnonymousAuthenticationToken("key", "anonymousUser",
                    AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
            assertThat(PostfachSichtbarkeitService.istAdmin(anonym)).isFalse();
        }

        @Test
        void ohneRollenlisteKeinAdmin() {
            Authentication ohne = org.mockito.Mockito.mock(Authentication.class);
            when(ohne.getAuthorities()).thenReturn(null);
            assertThat(PostfachSichtbarkeitService.istAdmin(ohne)).isFalse();
        }
    }

    @Nested
    class OhneBenutzer {

        @Test
        void ohneAnmeldungNurHauptpostfachUndFuerAlle() {
            PostfachSichtbarkeit sicht = service.fuer(null);

            assertThat(sicht.alles()).isFalse();
            assertThat(sicht.sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 5L);
            verifyNoInteractions(frontendUserProfileService);
        }

        @Test
        void anonymeAnmeldungIstKeinBenutzer() {
            Authentication anonym = new AnonymousAuthenticationToken("key", "anonymousUser",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

            assertThat(service.fuer(anonym).sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 5L);
            verifyNoInteractions(frontendUserProfileService);
        }

        @Test
        void leererNameIstKeinBenutzer() {
            assertThat(service.fuer(angemeldet(" ")).sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 5L);
            Authentication ohneName = org.mockito.Mockito.mock(Authentication.class);
            assertThat(service.fuer(ohneName).sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 5L);
            verifyNoInteractions(frontendUserProfileService);
        }

        @Test
        void unbekannterBenutzerNurHauptpostfachUndFuerAlle() {
            when(frontendUserProfileService.findByUsername("unbekannt")).thenReturn(Optional.empty());

            assertThat(service.fuer(angemeldet("unbekannt", "ROLE_USER")).sichtbarePostfachIds())
                    .containsExactlyInAnyOrder(1L, 5L);
            verify(postfachRepository, never()).findIdsFreigegebenFuerBenutzer(anyLong());
        }

        @Test
        void benutzerOhneIdWieOhneBenutzer() {
            assertThat(service.fuerBenutzer(new FrontendUserProfile()).sichtbarePostfachIds())
                    .containsExactlyInAnyOrder(1L, 5L);
            verify(postfachRepository, never()).findIdsFreigegebenFuerBenutzer(any());
        }
    }

    @Nested
    class MitBenutzer {

        @Test
        void eigenesPostfachIstSichtbar() {
            when(frontendUserProfileService.findByUsername("max")).thenReturn(Optional.of(benutzer(7L, max)));
            when(postfachRepository.findIdsFreigegebenFuerBenutzer(7L)).thenReturn(List.of());

            PostfachSichtbarkeit sicht = service.fuer(angemeldet("max", "ROLE_USER"));

            assertThat(sicht.sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 3L, 5L);
            verify(postfachRepository, never()).findIdsFreigegebenFuerAbteilungen(anyCollection());
        }

        @Test
        void direktFreigegebenIstSichtbar() {
            when(frontendUserProfileService.findByUsername("max")).thenReturn(Optional.of(benutzer(7L, null)));
            when(postfachRepository.findIdsFreigegebenFuerBenutzer(7L)).thenReturn(List.of(2L));

            assertThat(service.fuer(angemeldet("max")).sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 2L, 5L);
        }

        @Test
        void abteilungFreigegebenIstSichtbar() {
            when(frontendUserProfileService.findByUsername("max")).thenReturn(Optional.of(benutzer(7L, null, 20L, 21L)));
            when(postfachRepository.findIdsFreigegebenFuerBenutzer(7L)).thenReturn(List.of());
            when(postfachRepository.findIdsFreigegebenFuerAbteilungen(Set.of(20L, 21L))).thenReturn(List.of(4L));

            assertThat(service.fuer(angemeldet("max")).sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 4L, 5L);
        }

        @Test
        void abteilungOhneIdWirdIgnoriert() {
            FrontendUserProfile u = benutzer(7L, null, 20L);
            Abteilung ohneId = new Abteilung();
            u.getMitarbeiter().getAbteilungen().add(ohneId);
            when(postfachRepository.findIdsFreigegebenFuerBenutzer(7L)).thenReturn(List.of());
            when(postfachRepository.findIdsFreigegebenFuerAbteilungen(Set.of(20L))).thenReturn(List.of());

            assertThat(service.fuerBenutzer(u).sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 5L);
        }

        @Test
        void mitarbeiterOhneAbteilungenFragtNichtNach() {
            FrontendUserProfile u = benutzer(7L, null);
            Mitarbeiter m = new Mitarbeiter();
            m.setAbteilungen(null);
            u.setMitarbeiter(m);
            when(postfachRepository.findIdsFreigegebenFuerBenutzer(7L)).thenReturn(List.of());

            assertThat(service.fuerBenutzer(u).sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 5L);
            verify(postfachRepository, never()).findIdsFreigegebenFuerAbteilungen(anyCollection());
        }

        @Test
        void ausgeschaltetesPostfachZaehltNichtAuchWennFreigegeben() {
            // Das ausgeschaltete Postfach 9 liefert findByAktivTrue gar nicht erst.
            when(frontendUserProfileService.findByUsername("max")).thenReturn(Optional.of(benutzer(7L, postfach(9L, false, false))));
            when(postfachRepository.findIdsFreigegebenFuerBenutzer(7L)).thenReturn(List.of(9L));

            assertThat(service.fuer(angemeldet("max")).sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 5L);
        }

        @Test
        void hauptpostfachSiehtJederAuchWennNichtFuerAlle() {
            info.setSichtbarFuerAlle(false);

            assertThat(service.fuer(null).sichtbarePostfachIds()).contains(1L);
        }
    }

    @Nested
    class VerborgeneMails {

        @Test
        void adminOderOhneSichtbarkeitVerbirgtNichts() {
            assertThat(service.verborgeneEmailIds(PostfachSichtbarkeit.ALLES)).isEmpty();
            assertThat(service.verborgeneEmailIds(null)).isEmpty();
            verifyNoInteractions(zuordnungRepository);
        }

        @Test
        void fragtMitDenSichtbarenPostfaechern() {
            when(zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(Set.of(1L, 5L))).thenReturn(Set.of(10L, 11L));

            assertThat(service.verborgeneEmailIds(new PostfachSichtbarkeit(false, Set.of(1L, 5L))))
                    .containsExactlyInAnyOrder(10L, 11L);
        }

        @Test
        void ohneSichtbaresPostfachPlatzhalterStattLeererListe() {
            when(zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(Set.of(-1L))).thenReturn(Set.of(10L));

            assertThat(service.verborgeneEmailIds(new PostfachSichtbarkeit(false, Set.of()))).containsExactly(10L);
        }

        @Test
        void nullVomRepositoryIstLeer() {
            when(zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(any())).thenReturn(null);

            assertThat(service.verborgeneEmailIds(new PostfachSichtbarkeit(false, Set.of(1L)))).isEmpty();
        }
    }

    @Nested
    class NichtLesbareMails {

        private org.example.kalkulationsprogramm.domain.Email mail(Long id, EmailAbsender postfach) {
            org.example.kalkulationsprogramm.domain.Email e = new org.example.kalkulationsprogramm.domain.Email();
            e.setId(id);
            e.ordnePostfachZu(postfach, "INBOX", null);
            return e;
        }

        @Test
        void nurFremdeUndNichtZugeordneteSindGesperrt() {
            org.example.kalkulationsprogramm.domain.Email eigene = mail(10L, info);
            org.example.kalkulationsprogramm.domain.Email fremde = mail(20L, max);
            org.example.kalkulationsprogramm.domain.Email projektMail = mail(30L, max);
            projektMail.setProjekt(new org.example.kalkulationsprogramm.domain.Projekt());
            when(emailRepository.findAllById(Set.of(10L, 20L, 30L, 40L))).thenReturn(List.of(eigene, fremde, projektMail));
            java.util.List<Long> ids = new java.util.ArrayList<>(List.of(10L, 20L, 30L, 40L));
            ids.add(null);

            assertThat(service.nichtLesbareEmailIds(ids, new PostfachSichtbarkeit(false, Set.of(1L))))
                    .containsExactly(20L);
        }

        @Test
        void adminOderNichtsZuPruefenOhneAbfrage() {
            assertThat(service.nichtLesbareEmailIds(List.of(20L), PostfachSichtbarkeit.ALLES)).isEmpty();
            assertThat(service.nichtLesbareEmailIds(List.of(20L), null)).isEmpty();
            assertThat(service.nichtLesbareEmailIds(null, new PostfachSichtbarkeit(false, Set.of(1L)))).isEmpty();
            java.util.List<Long> nurNull = new java.util.ArrayList<>();
            nurNull.add(null);
            assertThat(service.nichtLesbareEmailIds(nurNull, new PostfachSichtbarkeit(false, Set.of(1L)))).isEmpty();
            verifyNoInteractions(emailRepository);
        }
    }

    @Nested
    class RechnungsPostfach {

        @Test
        void siehtJederAuchOhneFreigabe() {
            rechnungenAlsGeschaeftsdokumentPostfach();

            assertThat(service.fuer(null).sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 2L, 5L);
        }

        @Test
        void ausgeschaltetesRechnungsPostfachZaehltNicht() {
            // findByAktivTrue liefert ausgeschaltete gar nicht erst.
            EmailAbsender aus = postfach(6L, false, false);
            aus.setFuerGeschaeftsdokumente(true);
            when(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()).thenReturn(List.of(info));

            assertThat(service.fuer(null).sichtbarePostfachIds()).containsExactly(1L);
        }

        private void rechnungenAlsGeschaeftsdokumentPostfach() {
            rechnungen.setFuerGeschaeftsdokumente(true);
        }
    }

    @Nested
    class NichtSichtbarePostfaecher {

        @Test
        void nurVorhandeneUndNichtSichtbare() {
            when(postfachRepository.findAllById(Set.of(1L, 3L, 77L))).thenReturn(List.of(info, max));
            java.util.List<Long> ids = new java.util.ArrayList<>(List.of(1L, 3L, 77L));
            ids.add(null);

            assertThat(service.nichtSichtbarePostfachIds(ids, new PostfachSichtbarkeit(false, Set.of(1L))))
                    .containsExactly(3L);
        }

        @Test
        void adminOderNichtsZuPruefenOhneAbfrage() {
            assertThat(service.nichtSichtbarePostfachIds(List.of(3L), PostfachSichtbarkeit.ALLES)).isEmpty();
            assertThat(service.nichtSichtbarePostfachIds(List.of(3L), null)).isEmpty();
            assertThat(service.nichtSichtbarePostfachIds(null, new PostfachSichtbarkeit(false, Set.of(1L)))).isEmpty();
            java.util.List<Long> nurNull = new java.util.ArrayList<>();
            nurNull.add(null);
            assertThat(service.nichtSichtbarePostfachIds(nurNull, new PostfachSichtbarkeit(false, Set.of(1L)))).isEmpty();
            verify(postfachRepository, never()).findAllById(any());
        }
    }
}
