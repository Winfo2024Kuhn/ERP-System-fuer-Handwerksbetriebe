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

    @Nested
    class AntwortPostfachFuerBenutzer {

        /** Erika sieht nur info@ (Hauptpostfach). */
        private final PostfachSichtbarkeit nurInfo = new PostfachSichtbarkeit(false, java.util.Set.of(1L));
        private final EmailAbsender eigenes = postfach(9L, "erika@example.com", false);

        @Test
        void adminWieBisher() {
            Email mail = eingang("max@example.com", null, info, max);

            assertThat(service.antwortPostfachFuer(mail, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(max);
            assertThat(service.antwortPostfachFuer(mail, null, eigenes)).isSameAs(max);
        }

        @Test
        void nurSichtbarePostfaecherDerMailZaehlen() {
            // An max@ adressiert, liegt in info@ und max@ – Erika sieht max@ nicht: info@.
            Email mail = eingang("max@example.com", "info@example.com", info, max);

            assertThat(service.antwortPostfachFuer(mail, nurInfo, eigenes)).isSameAs(info);
        }

        @Test
        void sichtbaresPostfachAusDemAnGewinnt() {
            PostfachSichtbarkeit infoUndMax = new PostfachSichtbarkeit(false, java.util.Set.of(1L, 3L));
            Email mail = eingang("max@example.com", null, info, max);

            assertThat(service.antwortPostfachFuer(mail, infoUndMax, eigenes)).isSameAs(max);
        }

        @Test
        void mailNurInFremdemPostfachGehtUeberEigenes() {
            assertThat(service.antwortPostfachFuer(eingang("max@example.com", null, max), nurInfo, eigenes))
                    .isSameAs(eigenes);
        }

        @Test
        void ohneEigenesUeberDasHauptpostfach() {
            assertThat(service.antwortPostfachFuer(eingang("max@example.com", null, max), nurInfo, null)).isSameAs(info);
        }

        @Test
        void ausgeschaltetesEigenesZaehltNicht() {
            eigenes.setAktiv(false);

            assertThat(service.antwortPostfachFuer(eingang("max@example.com", null, max), nurInfo, eigenes)).isSameAs(info);
        }

        @Test
        void ausgeschaltetesPostfachBeimAdminGehtUebersHauptpostfach() {
            max.setAktiv(false);

            assertThat(service.antwortPostfachFuer(eingang("max@example.com", null, max), PostfachSichtbarkeit.ALLES, eigenes))
                    .isSameAs(info);
        }

        @Test
        void mailOhneZuordnungIstHauptpostfachMail() {
            assertThat(service.antwortPostfachFuer(eingang("info@example.com", null), nurInfo, eigenes)).isSameAs(info);

            Email ohneListe = new Email();
            ohneListe.setDirection(EmailDirection.IN);
            ohneListe.setPostfachZuordnungen(null);
            assertThat(service.antwortPostfachFuer(ohneListe, nurInfo, eigenes)).isSameAs(info);
            assertThat(service.antwortPostfachFuer(null, nurInfo, eigenes)).isSameAs(info);
        }

        @Test
        void alteGesendeteMailAusFremdemPostfachGehtUeberEigenes() {
            when(postfachRepository.findAllByOrderBySortierungAscIdAsc()).thenReturn(java.util.List.of(info, max));
            Email gesendet = new Email();
            gesendet.setDirection(EmailDirection.OUT);
            gesendet.setFromAddress("max@example.com");

            assertThat(service.antwortPostfachFuer(gesendet, nurInfo, eigenes)).isSameAs(eigenes);
            assertThat(service.antwortPostfachFuer(gesendet, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(max);
        }

        @Test
        void hauptpostfachNichtImSichtbarenTeilFaelltAufEigenesZurueck() {
            assertThat(service.antwortPostfachFuer(eingang("info@example.com", null),
                    new PostfachSichtbarkeit(false, java.util.Set.of()), eigenes)).isSameAs(eigenes);
        }

        @Test
        void ohneHauptpostfachUndOhneZuordnungNull() {
            when(postfachRepository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());

            assertThat(service.antwortPostfachFuer(eingang("info@example.com", null), nurInfo, null)).isNull();
            assertThat(service.antwortPostfach(eingang("info@example.com", null))).isNull();
            assertThat(service.antwortPostfachFuer(eingang("max@example.com", null, max), nurInfo, null)).isNull();
        }
    }

    /** rechnungen@ ist ein reines Ausgangspostfach: darüber wird nie geantwortet oder weitergeleitet. */
    @Nested
    class RechnungsPostfachIstNurAusgang {

        private final PostfachSichtbarkeit nurInfoUndRechnungen = new PostfachSichtbarkeit(false, java.util.Set.of(1L, 2L));
        private final EmailAbsender eigenes = postfach(9L, "erika@example.com", false);

        @BeforeEach
        void rechnungsPostfach() {
            rechnungen.setFuerGeschaeftsdokumente(true);
        }

        @Test
        void mailNurInRechnungenGehtUeberEigenesSonstHauptpostfach() {
            Email mail = eingang("rechnungen@example.com", null, rechnungen);

            assertThat(service.antwortPostfachFuer(mail, nurInfoUndRechnungen, eigenes)).isSameAs(eigenes);
            assertThat(service.antwortPostfachFuer(mail, nurInfoUndRechnungen, null)).isSameAs(info);
        }

        @Test
        void adminGenauso() {
            Email mail = eingang("rechnungen@example.com", null, rechnungen);

            assertThat(service.antwortPostfachFuer(mail, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(eigenes);
            assertThat(service.antwortPostfach(mail)).isSameAs(info);
        }

        @Test
        void mailInRechnungenUndInfoGehtUeberInfo() {
            Email mail = eingang("rechnungen@example.com", "info@example.com", rechnungen, info);

            assertThat(service.antwortPostfachFuer(mail, nurInfoUndRechnungen, eigenes)).isSameAs(info);
            assertThat(service.antwortPostfachFuer(mail, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(info);
        }

        @Test
        void eigeneGesendeteMailAusRechnungenGehtUeberEigenes() {
            Email gesendet = new Email();
            gesendet.setDirection(EmailDirection.OUT);
            gesendet.ordnePostfachZu(rechnungen, "INBOX.Sent", 1L);

            assertThat(service.antwortPostfachFuer(gesendet, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(eigenes);
            assertThat(service.antwortPostfachFuer(gesendet, nurInfoUndRechnungen, null)).isSameAs(info);
        }

        @Test
        void alteGesendeteMailVonRechnungenOhneZuordnung() {
            when(postfachRepository.findAllByOrderBySortierungAscIdAsc()).thenReturn(java.util.List.of(info, rechnungen));
            Email gesendet = new Email();
            gesendet.setDirection(EmailDirection.OUT);
            gesendet.setFromAddress("rechnungen@example.com");

            assertThat(service.antwortPostfachFuer(gesendet, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(eigenes);
        }

        @Test
        void rechnungsPostfachIstZugleichHauptpostfachWieBisher() {
            info.setFuerGeschaeftsdokumente(true);
            Email mail = eingang("info@example.com", null, info);

            assertThat(service.antwortPostfachFuer(mail, nurInfoUndRechnungen, eigenes)).isSameAs(info);
            assertThat(service.antwortPostfach(mail)).isSameAs(info);
        }

        @Test
        void antwortTauglich() {
            assertThat(PostfachVersandService.istAntwortPostfach(rechnungen)).isFalse();
            assertThat(PostfachVersandService.istAntwortPostfach(max)).isTrue();
            info.setFuerGeschaeftsdokumente(true);
            assertThat(PostfachVersandService.istAntwortPostfach(info)).isTrue();
        }
    }

    /** Auslaufendes Postfach (alte T-Online-Adresse): Antworten über info@, für neue Mails nicht wählbar. */
    @Nested
    class AuslaufendesPostfach {

        private final EmailAbsender eigenes = postfach(9L, "erika@example.com", false);
        private EmailAbsender tonline;

        @BeforeEach
        void tonline() {
            tonline = postfach(6L, "betrieb@t-online.de", false);
            tonline.setLaeuftAus(true);
        }

        @Test
        void mailNurInTonlineGehtUeberHauptpostfachAuchMitEigenem() {
            Email mail = eingang("betrieb@t-online.de", null, tonline);

            assertThat(service.antwortPostfachFuer(mail, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(info);
            assertThat(service.antwortPostfachFuer(mail, new PostfachSichtbarkeit(false, java.util.Set.of(1L, 6L)), eigenes))
                    .isSameAs(info);
            assertThat(service.antwortPostfach(mail)).isSameAs(info);
        }

        @Test
        void mailInTonlineUndInfoGehtUeberInfo() {
            Email mail = eingang("betrieb@t-online.de", "info@example.com", tonline, info);

            assertThat(service.antwortPostfachFuer(mail, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(info);
        }

        @Test
        void mailInTonlineUndMaxGehtUeberMax() {
            Email mail = eingang("betrieb@t-online.de", "max@example.com", tonline, max);

            assertThat(service.antwortPostfachFuer(mail, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(max);
        }

        @Test
        void eigeneGesendeteMailAusTonlineGehtUeberHauptpostfach() {
            Email gesendet = new Email();
            gesendet.setDirection(EmailDirection.OUT);
            gesendet.ordnePostfachZu(tonline, "Sent", 1L);

            assertThat(service.antwortPostfachFuer(gesendet, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(info);
        }

        @Test
        void alteGesendeteMailVonTonlineOhneZuordnung() {
            when(postfachRepository.findAllByOrderBySortierungAscIdAsc()).thenReturn(java.util.List.of(info, tonline));
            Email gesendet = new Email();
            gesendet.setDirection(EmailDirection.OUT);
            gesendet.setFromAddress("betrieb@t-online.de");

            assertThat(service.antwortPostfachFuer(gesendet, PostfachSichtbarkeit.ALLES, eigenes)).isSameAs(info);
        }

        @Test
        void fremdesAuslaufendesPostfachZaehltWieFremd() {
            // Sieht der Benutzer das auslaufende Postfach nicht, gilt die Regel für fremde Postfächer.
            Email mail = eingang("betrieb@t-online.de", null, tonline);

            assertThat(service.antwortPostfachFuer(mail, new PostfachSichtbarkeit(false, java.util.Set.of(1L)), eigenes))
                    .isSameAs(eigenes);
        }

        @Test
        void auslaufendesEigenesPostfachZaehltNicht() {
            Email mail = eingang("max@example.com", null, max);

            assertThat(service.antwortPostfachFuer(mail, new PostfachSichtbarkeit(false, java.util.Set.of(1L)), tonline))
                    .isSameAs(info);
        }

        @Test
        void fuerNeueMailNichtWaehlbar() {
            when(postfachRepository.findById(6L)).thenReturn(Optional.of(tonline));

            assertThatThrownBy(() -> service.postfachFuerNeueMail(6L, 7L, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Dieses Postfach läuft aus. Bitte ein anderes Postfach wählen.");
        }

        @Test
        void auslaufendesEigenesFaelltAufsHauptpostfach() {
            FrontendUserProfile erika = new FrontendUserProfile();
            erika.setId(7L);
            erika.setEmailAbsender(tonline);
            when(frontendUserProfileRepository.findById(7L)).thenReturn(Optional.of(erika));

            assertThat(service.eigenesPostfach(7L)).isEmpty();
            assertThat(service.postfachFuerNeueMail(null, 7L, false)).isSameAs(info);
        }

        @Test
        void antwortTauglich() {
            assertThat(PostfachVersandService.istAntwortPostfach(tonline)).isFalse();
        }
    }
}
