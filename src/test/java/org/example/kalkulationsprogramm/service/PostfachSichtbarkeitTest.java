package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Reine Sichtbarkeitsregel ohne Datenbank: welche Mail sieht bzw. öffnet ein Benutzer? */
class PostfachSichtbarkeitTest {

    private static final PostfachSichtbarkeit NUR_INFO = new PostfachSichtbarkeit(false, Set.of(1L));

    private static EmailAbsender postfach(Long id) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse("postfach" + id + "@example.com");
        return p;
    }

    private static Email mailIn(Long... postfachIds) {
        Email email = new Email();
        email.setId(99L);
        for (Long id : postfachIds) {
            email.ordnePostfachZu(postfach(id), "INBOX", null);
        }
        return email;
    }

    @Nested
    class Postfaecher {

        @Test
        void adminSiehtJedesPostfach() {
            assertThat(PostfachSichtbarkeit.ALLES.siehtPostfach(42L)).isTrue();
            assertThat(PostfachSichtbarkeit.ALLES.siehtPostfach((Long) null)).isTrue();
        }

        @Test
        void benutzerSiehtNurFreigegebene() {
            assertThat(NUR_INFO.siehtPostfach(1L)).isTrue();
            assertThat(NUR_INFO.siehtPostfach(2L)).isFalse();
            assertThat(NUR_INFO.siehtPostfach((Long) null)).isFalse();
        }

        @Test
        void postfachObjekt() {
            assertThat(NUR_INFO.siehtPostfach(postfach(1L))).isTrue();
            assertThat(NUR_INFO.siehtPostfach(postfach(2L))).isFalse();
            assertThat(NUR_INFO.siehtPostfach((EmailAbsender) null)).isFalse();
        }

        @Test
        void listeWirdKopiertUndNullIstLeer() {
            Set<Long> ids = new HashSet<>(Set.of(1L));
            PostfachSichtbarkeit sicht = new PostfachSichtbarkeit(false, ids);
            ids.add(2L);

            assertThat(sicht.siehtPostfach(2L)).isFalse();
            assertThat(new PostfachSichtbarkeit(false, null).sichtbarePostfachIds()).isEmpty();
        }
    }

    @Nested
    class Mails {

        @Test
        void ohneMailNichts() {
            assertThat(NUR_INFO.siehtEmail(null)).isFalse();
            assertThat(NUR_INFO.darfLesen(null)).isFalse();
        }

        @Test
        void mailOhneZuordnungGiltAlsHauptpostfachMail() {
            assertThat(NUR_INFO.siehtEmail(mailIn())).isTrue();

            Email ohneListe = new Email();
            ohneListe.setPostfachZuordnungen(null);
            assertThat(NUR_INFO.siehtEmail(ohneListe)).isTrue();
        }

        @Test
        void mailInSichtbaremPostfach() {
            assertThat(NUR_INFO.siehtEmail(mailIn(1L))).isTrue();
        }

        @Test
        void mailInSichtbaremUndFremdemPostfachIstSichtbar() {
            assertThat(NUR_INFO.siehtEmail(mailIn(2L, 1L))).isTrue();
        }

        @Test
        void mailNurInFremdemPostfachIstUnsichtbar() {
            assertThat(NUR_INFO.siehtEmail(mailIn(2L))).isFalse();
            assertThat(NUR_INFO.darfLesen(mailIn(2L))).isFalse();
        }

        @Test
        void zuordnungOhnePostfachZaehltNicht() {
            Email email = new Email();
            EmailPostfachZuordnung kaputt = new EmailPostfachZuordnung();
            kaputt.setEmail(email);
            email.getPostfachZuordnungen().add(kaputt);

            assertThat(NUR_INFO.siehtEmail(email)).isFalse();
        }

        @Test
        void adminSiehtAuchFremdeMails() {
            assertThat(PostfachSichtbarkeit.ALLES.siehtEmail(mailIn(2L))).isTrue();
            assertThat(PostfachSichtbarkeit.ALLES.darfLesen(mailIn(2L))).isTrue();
        }
    }

    @Nested
    class LesenUeberZuordnung {

        @Test
        void projektMailDarfGelesenAberNichtBearbeitetWerden() {
            Email email = mailIn(2L);
            email.setProjekt(new Projekt());

            assertThat(NUR_INFO.siehtEmail(email)).isFalse();
            assertThat(NUR_INFO.darfLesen(email)).isTrue();
        }

        @Test
        void anfrageMailDarfGelesenWerden() {
            Email email = mailIn(2L);
            email.setAnfrage(new Anfrage());

            assertThat(NUR_INFO.darfLesen(email)).isTrue();
        }

        @Test
        void lieferantenMailDarfGelesenWerden() {
            Email email = mailIn(2L);
            email.setLieferant(new Lieferanten());

            assertThat(NUR_INFO.darfLesen(email)).isTrue();
        }

        @Test
        void sichtbareMailDarfImmerGelesenWerden() {
            assertThat(NUR_INFO.darfLesen(mailIn(1L))).isTrue();
        }
    }
}
