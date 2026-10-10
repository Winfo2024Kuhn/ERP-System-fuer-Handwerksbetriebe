package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.mail.PostfachZugang;
import org.example.kalkulationsprogramm.service.mail.PostfachZugangService;
import org.example.kalkulationsprogramm.service.mail.SentMailArchiver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;

@ExtendWith(MockitoExtension.class)
class PostfachVersandServiceTest {

    @Mock private EmailAbsenderRepository postfachRepository;
    @Mock private FrontendUserProfileRepository frontendUserProfileRepository;
    @Mock private PostfachZugangService postfachZugangService;
    @Mock private SystemSettingsService systemSettingsService;
    @Mock private SentMailArchiver sentMailArchiver;

    @InjectMocks private PostfachVersandService service;

    private EmailAbsender info;
    private EmailAbsender rechnungen;
    private EmailAbsender max;

    @BeforeEach
    void setUp() {
        info = postfach(1L, "info@example.com", true);
        rechnungen = postfach(2L, "rechnungen@example.com", false);
        max = postfach(3L, "max@example.com", false);
        lenient().when(postfachRepository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(info));
    }

    private static EmailAbsender postfach(Long id, String adresse, boolean haupt) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse(adresse);
        p.setHauptpostfach(haupt);
        return p;
    }

    private static Email eingang(String an, String cc, EmailAbsender... postfaecher) {
        Email email = new Email();
        email.setDirection(EmailDirection.IN);
        email.setRecipient(an);
        email.setCc(cc);
        long id = 1;
        for (EmailAbsender p : postfaecher) {
            email.ordnePostfachZu(p, "INBOX", id);
            email.getPostfachZuordnungen().get(email.getPostfachZuordnungen().size() - 1).setId(id++);
        }
        return email;
    }

    @Nested
    class NeueMail {

        @Test
        void geschaeftsdokumentGehtUeberRechnungsPostfach() {
            when(postfachRepository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc())
                    .thenReturn(Optional.of(rechnungen));

            assertThat(service.postfachFuerNeueMail(3L, 7L, true)).isSameAs(rechnungen);
        }

        @Test
        void geschaeftsdokumentOhneRechnungsPostfachGehtUeberHauptpostfach() {
            when(postfachRepository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc())
                    .thenReturn(Optional.empty());

            assertThat(service.postfachFuerNeueMail(null, null, true)).isSameAs(info);
        }

        @Test
        void gewaehltesPostfachGewinnt() {
            when(postfachRepository.findById(2L)).thenReturn(Optional.of(rechnungen));

            assertThat(service.postfachFuerNeueMail(2L, 7L, false)).isSameAs(rechnungen);
        }

        @Test
        void ausgeschaltetesPostfachWirdAbgelehnt() {
            rechnungen.setAktiv(false);
            when(postfachRepository.findById(2L)).thenReturn(Optional.of(rechnungen));

            assertThatThrownBy(() -> service.postfachFuerNeueMail(2L, null, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("anderes wählen");
        }

        @Test
        void unbekanntesPostfachWirdAbgelehnt() {
            when(postfachRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.postfachFuerNeueMail(Long.MAX_VALUE, null, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void ohneAuswahlDasEigene() {
            FrontendUserProfile benutzer = new FrontendUserProfile();
            benutzer.setEmailAbsender(max);
            when(frontendUserProfileRepository.findById(7L)).thenReturn(Optional.of(benutzer));

            assertThat(service.postfachFuerNeueMail(null, 7L, false)).isSameAs(max);
        }

        @Test
        void eigenesAusgeschaltetDannHauptpostfach() {
            max.setAktiv(false);
            FrontendUserProfile benutzer = new FrontendUserProfile();
            benutzer.setEmailAbsender(max);
            when(frontendUserProfileRepository.findById(7L)).thenReturn(Optional.of(benutzer));

            assertThat(service.postfachFuerNeueMail(null, 7L, false)).isSameAs(info);
        }

        @Test
        void ohneHauptpostfachDasErsteAktive() {
            when(postfachRepository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(postfachRepository.findFirstByAktivTrueOrderBySortierungAscIdAsc()).thenReturn(Optional.of(max));

            assertThat(service.postfachFuerNeueMail(null, null, false)).isSameAs(max);
        }

        @Test
        void ganzOhnePostfaecherNull() {
            when(postfachRepository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(postfachRepository.findFirstByAktivTrueOrderBySortierungAscIdAsc()).thenReturn(Optional.empty());

            assertThat(service.postfachFuerNeueMail(null, null, false)).isNull();
        }
    }

    @Nested
    class AntwortPostfach {

        @Test
        void eingangAnRechnungenUndInfoAntwortetVonRechnungen() {
            Email email = eingang("rechnungen@example.com", "info@example.com", info, rechnungen);

            assertThat(service.antwortPostfach(email)).isSameAs(rechnungen);
        }

        @Test
        void eingangNurInCcAntwortetVonDiesemPostfach() {
            Email email = eingang("kollege@example.org", "Max <MAX@example.com>", info, max);

            assertThat(service.antwortPostfach(email)).isSameAs(max);
        }

        @Test
        void aliasAdresseAntwortetVomHauptpostfach() {
            Email email = eingang("verkauf@example.com", null, max, info);

            assertThat(service.antwortPostfach(email)).isSameAs(info);
        }

        @Test
        void sonstDasErstePostfach() {
            Email email = eingang("verkauf@example.com", null, max, rechnungen);

            assertThat(service.antwortPostfach(email)).isSameAs(max);
        }

        @Test
        void eingangOhnePostfachVomHauptpostfach() {
            assertThat(service.antwortPostfach(eingang("info@example.com", null))).isSameAs(info);
        }

        @Test
        void eigeneGesendeteMailVomVersandPostfach() {
            Email email = eingang("kunde@example.org", null, rechnungen);
            email.setDirection(EmailDirection.OUT);

            assertThat(service.antwortPostfach(email)).isSameAs(rechnungen);
        }

        @Test
        void alteGesendeteMailUeberAbsenderAdresse() {
            Email email = new Email();
            email.setDirection(EmailDirection.OUT);
            email.setFromAddress("Rechnungen <rechnungen@example.com>");
            when(postfachRepository.findAllByOrderBySortierungAscIdAsc()).thenReturn(List.of(info, rechnungen));

            assertThat(service.antwortPostfach(email)).isSameAs(rechnungen);
        }

        @Test
        void alteGesendeteMailOhneBekannteAdresseVomHauptpostfach() {
            Email email = new Email();
            email.setDirection(EmailDirection.OUT);
            email.setFromAddress(null);

            assertThat(service.antwortPostfach(email)).isSameAs(info);
        }

        @Test
        void ausgeschaltetesPostfachAntwortetVomHauptpostfach() {
            rechnungen.setAktiv(false);
            Email email = eingang("rechnungen@example.com", null, info, rechnungen);

            assertThat(service.antwortPostfach(email)).isSameAs(info);
        }

        @Test
        void ohneMailHauptpostfach() {
            assertThat(service.antwortPostfach(null)).isSameAs(info);
        }

        @Test
        void ohneZuordnungslisteHauptpostfach() {
            Email email = new Email();
            email.setDirection(EmailDirection.IN);
            email.setPostfachZuordnungen(null);

            assertThat(service.antwortPostfach(email)).isSameAs(info);
        }
    }

    @Nested
    class VersandBauen {

        private PostfachZugang zugang(EmailAbsender p, boolean mitPasswort) {
            return new PostfachZugang(p.getId(), p.getEmailAdresse(), p.getAnzeigename(), p.getEmailAdresse(),
                    mitPasswort ? "pw" : null, "mail.example.com", 465, "mail.example.com", 993, p.isHauptpostfach());
        }

        @Test
        void postfachMitZugangVersendetSelbst() {
            max.setAnzeigename("Max Mustermann");
            when(postfachZugangService.zugangVon(max)).thenReturn(zugang(max, true));

            PostfachVersandService.Versand versand = service.versandUeber(max);

            assertThat(versand.absenderAdresse()).isEqualTo("max@example.com");
            assertThat(versand.postfach()).isSameAs(max);
            assertThat(versand.dienst()).isNotNull();
        }

        @Test
        void postfachOhneZugangVersendetUeberHauptpostfachMitEigenerAdresse() {
            when(postfachZugangService.zugangVon(max)).thenReturn(zugang(max, false));
            when(systemSettingsService.getStandardMailKonto()).thenReturn(new SystemSettingsService.MailKonto(
                    "mail.example.com", 465, "info@example.com", "pw", "info@example.com", "Musterbetrieb"));

            PostfachVersandService.Versand versand = service.versandUeber(max);

            assertThat(versand.absenderAdresse()).isEqualTo("max@example.com");
            assertThat(versand.postfach()).isSameAs(max);
        }

        @Test
        void ohnePostfachStandardKonto() {
            when(systemSettingsService.getStandardMailKonto()).thenReturn(new SystemSettingsService.MailKonto(
                    "mail.example.com", 465, "alt@example.com", "pw", "alt@example.com", ""));

            PostfachVersandService.Versand versand = service.versandUeber(null);

            assertThat(versand.absenderAdresse()).isEqualTo("alt@example.com");
            assertThat(versand.postfach()).isNull();
        }

        @Test
        void eigenesPostfachOhneBenutzer() {
            assertThat(service.eigenesPostfach(null)).isEmpty();
            when(frontendUserProfileRepository.findById(9L)).thenReturn(Optional.empty());
            assertThat(service.eigenesPostfach(9L)).isEmpty();
        }
    }

    @Nested
    class Einzelversand {

        @Test
        void bereinigtUndEntferntDoppelte() {
            assertThat(service.pruefeEinzelversand(List.of(" a@example.com", "A@example.com", "", "b@example.com"),
                    List.of(" ")))
                    .containsExactly("a@example.com", "b@example.com");
        }

        @Test
        void ccIstNichtErlaubt() {
            assertThatThrownBy(() -> service.pruefeEinzelversand(List.of("a@example.com"), List.of("c@example.com")))
                    .hasMessage("Beim einzelnen Verschicken bitte keine Kopie-Empfänger eintragen.");
        }

        @Test
        void ohneEmpfaengerNichtErlaubt() {
            assertThatThrownBy(() -> service.pruefeEinzelversand(null, null))
                    .hasMessage("Bitte mindestens einen Empfänger angeben.");
        }

        @Test
        void hoechstensFuenfzig() {
            List<String> viele = IntStream.rangeClosed(1, 51).mapToObj(i -> "kunde" + i + "@example.com").toList();

            assertThatThrownBy(() -> service.pruefeEinzelversand(viele, null))
                    .hasMessage("Höchstens 50 Empfänger pro Sammel-Mail.");
            assertThat(service.pruefeEinzelversand(viele.subList(0, 50), null)).hasSize(50);
        }

        @Test
        void teilfehlerHaltenDieAnderenNichtAuf() {
            List<String> versucht = new ArrayList<>();
            PostfachVersandService.EinzelversandErgebnis<String> ergebnis = service.versendeEinzeln(
                    List.of("a@example.com", "kaputt@example.org", "b@example.com"), adresse -> {
                        versucht.add(adresse);
                        if (adresse.startsWith("kaputt")) {
                            throw new SendFailedException("550 unknown user");
                        }
                        return "id-" + adresse;
                    });

            assertThat(versucht).hasSize(3);
            assertThat(ergebnis.verschickt()).containsExactly("id-a@example.com", "id-b@example.com");
            assertThat(ergebnis.fehlgeschlagen()).containsExactly(
                    new PostfachVersandService.Fehlschlag("kaputt@example.org", "Empfänger vom Mail-Server abgelehnt"));
        }

        @Test
        void anmeldefehlerBrichtAb() {
            List<String> versucht = new ArrayList<>();
            PostfachVersandService.EinzelversandErgebnis<String> ergebnis = service.versendeEinzeln(
                    List.of("a@example.com", "b@example.com", "c@example.com"), adresse -> {
                        versucht.add(adresse);
                        throw new MessagingException("Versand", new AuthenticationFailedException("535"));
                    });

            assertThat(versucht).containsExactly("a@example.com");
            assertThat(ergebnis.verschickt()).isEmpty();
            assertThat(ergebnis.fehlgeschlagen()).hasSize(3)
                    .allMatch(f -> f.grund().equals("Anmeldung am Postfach fehlgeschlagen"));
        }

        @Test
        void fehlerGruende() {
            assertThat(PostfachVersandService.fehlerGrund(new AddressException("kaputt"))).isEqualTo("Adresse ungültig");
            assertThat(PostfachVersandService.fehlerGrund(new MessagingException("x", new SendFailedException("y"))))
                    .isEqualTo("Empfänger vom Mail-Server abgelehnt");
            assertThat(PostfachVersandService.fehlerGrund(new IllegalStateException("x"))).isEqualTo("Versand fehlgeschlagen");
        }
    }

    @Test
    void dienstUebernimmtAnzeigename() {
        assertThat(service.dienst(new SystemSettingsService.MailKonto("h", 465, "u", "p", "a@example.com", ""), "Name"))
                .isNotNull();
    }
}
