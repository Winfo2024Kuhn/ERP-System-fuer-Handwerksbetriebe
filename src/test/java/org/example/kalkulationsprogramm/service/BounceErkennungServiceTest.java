package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.ZustellStatus;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Tests fuer die Erkennung von Unzustellbarkeits-Meldungen.
 *
 * <p>Der Anlassfall: ein Angebot ging an eine nicht existierende Adresse, der
 * Rueckläufer von T-Online kam als reine Text-Mail zurueck und wurde nirgends
 * ausgewertet — im ERP stand weiter "versendet". Der erste Test bildet genau
 * dieses Format nach.</p>
 *
 * <p>Alle Adressen und Namen sind Dummy-Daten (DSGVO).</p>
 */
@ExtendWith(MockitoExtension.class)
class BounceErkennungServiceTest
{
    private static final String ORIGINAL_MESSAGE_ID = "<1836490240.8.1784268135381@localhost>";

    @Mock EmailRepository emailRepository;
    @InjectMocks BounceErkennungService service;

    private static MimeMessage baueMail(String von, String betreff, String body) throws Exception
    {
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        msg.setFrom(von);
        msg.setSubject(betreff);
        msg.setText(body, "UTF-8");
        msg.saveChanges();
        return msg;
    }

    private static Email ausgangsmail()
    {
        Email mail = new Email();
        mail.setMessageId(ORIGINAL_MESSAGE_ID);
        mail.setDirection(EmailDirection.OUT);
        mail.setRecipient("max.mustermann@example.com");
        mail.setSubject("Angebot AG-2026/07/00005");
        return mail;
    }

    /**
     * Das Format, an dem der Fehler ueberhaupt erst auffiel: T-Online schickt
     * keinen strukturierten DSN, sondern Fliesstext mit zitierten Headern.
     */
    private static String tOnlineKlartextBounce()
    {
        return """
                Mail delivery failed: returning message to sender
                ------------------------- Failed addresses follow: ---------------------|
                 <max.mustermann@example.com>
                   unknown user / Teilnehmer existiert nicht

                |------------------------- Message header follows: ----------------------|
                Received: from erp.example. ([203.0.113.10]) by fwd90.example.com
                Date: Fri, 17 Jul 2026 08:02:15 +0200 (CEST)
                From: firma@example.com
                To: max.mustermann@example.com
                Message-ID: %s
                Subject: Angebot AG-2026/07/00005 - Balkongelaender
                """.formatted(ORIGINAL_MESSAGE_ID);
    }

    @Test
    void markiertAusgangsmailBeiTOnlineKlartextBounce() throws Exception
    {
        Email original = ausgangsmail();
        when(emailRepository.findByMessageId(ORIGINAL_MESSAGE_ID)).thenReturn(Optional.of(original));

        MimeMessage bounce = baueMail("mailer-daemon@example.com",
                "Mail delivery failed: returning message to sender",
                tOnlineKlartextBounce());

        assertThat(service.verarbeiteRuecklaeufer(bounce)).isTrue();

        ArgumentCaptor<Email> gespeichert = ArgumentCaptor.forClass(Email.class);
        verify(emailRepository).save(gespeichert.capture());
        assertThat(gespeichert.getValue().getZustellStatus()).isEqualTo(ZustellStatus.UNZUSTELLBAR);
        assertThat(gespeichert.getValue().getZustellFehler()).contains("existiert nicht");
        assertThat(gespeichert.getValue().getZustellGeprueftAm()).isNotNull();
    }

    @Test
    void markiertNichtsWennMessageIdUnbekanntIst() throws Exception
    {
        when(emailRepository.findByMessageId(ORIGINAL_MESSAGE_ID)).thenReturn(Optional.empty());

        MimeMessage bounce = baueMail("mailer-daemon@example.com",
                "Mail delivery failed", tOnlineKlartextBounce());

        assertThat(service.verarbeiteRuecklaeufer(bounce)).isFalse();
        verify(emailRepository, never()).save(any());
    }

    /**
     * Kernabsicherung gegen Fehlalarme: eine gewoehnliche Kundenantwort zitiert
     * unsere Mail samt Header. Ohne die Bounce-Erkennung als Vorbedingung
     * wuerde ein gerade beantwortetes Angebot als unzustellbar markiert.
     */
    @Test
    void markiertNichtsBeiNormalerKundenantwortMitZitiertemHeader() throws Exception
    {
        MimeMessage antwort = baueMail("max.mustermann@example.com",
                "Re: Angebot AG-2026/07/00005",
                "Vielen Dank, wir melden uns.\n> Message-ID: " + ORIGINAL_MESSAGE_ID);

        assertThat(service.verarbeiteRuecklaeufer(antwort)).isFalse();
        verify(emailRepository, never()).save(any());
    }

    /**
     * Eine bereits als unzustellbar markierte Mail darf nicht erneut
     * geschrieben werden — sonst wandert das Pruefdatum bei jedem
     * Wiederimport nach vorn.
     */
    @Test
    void schreibtNichtErneutWennBereitsMarkiert() throws Exception
    {
        Email original = ausgangsmail();
        original.markiereUnzustellbar("unknown user");
        when(emailRepository.findByMessageId(ORIGINAL_MESSAGE_ID)).thenReturn(Optional.of(original));

        MimeMessage bounce = baueMail("mailer-daemon@example.com",
                "Mail delivery failed", tOnlineKlartextBounce());

        assertThat(service.verarbeiteRuecklaeufer(bounce)).isTrue();
        verify(emailRepository, never()).save(any());
    }

    /** Eingehende Mails duerfen nie als unzustellbar markiert werden. */
    @Test
    void ignoriertTrefferDerKeineAusgangsmailIst() throws Exception
    {
        Email eingehend = ausgangsmail();
        eingehend.setDirection(EmailDirection.IN);
        when(emailRepository.findByMessageId(ORIGINAL_MESSAGE_ID)).thenReturn(Optional.of(eingehend));

        MimeMessage bounce = baueMail("mailer-daemon@example.com",
                "Mail delivery failed", tOnlineKlartextBounce());

        assertThat(service.verarbeiteRuecklaeufer(bounce)).isFalse();
        verify(emailRepository, never()).save(any());
    }

    // ======================= Verzoegerung vs. endgueltiger Fehler =======================

    /**
     * Der wichtigste Negativfall: Greylisting und kurzzeitig nicht erreichbare
     * Zielserver erzeugen einen DSN im <em>gleichen</em> Format wie ein echter
     * Fehler — nur mit {@code Action: delayed}. Die Mail ist weiter unterwegs.
     * Wuerde sie hier als unzustellbar markiert, staende im ERP dauerhaft
     * "Nicht angekommen", obwohl sie 20 Minuten spaeter ankommt.
     */
    @Test
    void markiertNichtsBeiVerzoegerungsMeldung() throws Exception
    {
        String delayDsn = """
                This message was created automatically by mail delivery software.
                A message that you sent has not yet been delivered to all recipients.

                Final-Recipient: rfc822; max.mustermann@example.com
                Action: delayed
                Status: 4.4.1
                Diagnostic-Code: smtp; 451 4.4.1 connection timed out

                Message-ID: %s
                """.formatted(ORIGINAL_MESSAGE_ID);

        MimeMessage bounce = baueMail("mailer-daemon@example.com",
                "Warning: message delayed", delayDsn);

        assertThat(service.verarbeiteRuecklaeufer(bounce)).isFalse();
        verify(emailRepository, never()).save(any());
    }

    @Test
    void erkenntEndgueltigenFehlerAnActionFailed()
    {
        assertThat(BounceErkennungService.istDauerhafterFehler(
                "Final-Recipient: rfc822; max.mustermann@example.com\nAction: failed\n")).isTrue();
    }

    @Test
    void erkenntEndgueltigenFehlerAnStatus5xx()
    {
        assertThat(BounceErkennungService.istDauerhafterFehler("Status: 5.1.1\n")).isTrue();
    }

    @Test
    void wertetVerzoegerungHoeherAlsFehlerMerkmale()
    {
        // Enthaelt beides — im Zweifel gilt "noch unterwegs".
        assertThat(BounceErkennungService.istDauerhafterFehler(
                "Action: delayed\nStatus: 4.4.1\nDiagnostic-Code: smtp; 451 mailbox unavailable\n"))
                .isFalse();
    }

    @Test
    void behandeltTemporaerenStatus4xxNichtAlsFehler()
    {
        assertThat(BounceErkennungService.istDauerhafterFehler("Status: 4.7.1\n")).isFalse();
    }

    /**
     * Ein volles Postfach ist voruebergehend (SMTP 4.2.2) — der Kunde raeumt
     * auf und die naechste Mail kommt an. Die Adresse dauerhaft als
     * unzustellbar zu markieren waere falsch.
     */
    @Test
    void behandeltVollesPostfachNichtAlsDauerhaftenFehler()
    {
        assertThat(BounceErkennungService.istDauerhafterFehler(
                "The recipient's mailbox is full and cannot accept messages now.")).isFalse();
        assertThat(BounceErkennungService.istDauerhafterFehler(
                "552 quota exceeded")).isFalse();
    }

    @Test
    void erkenntKlartextBounceAlsEndgueltigenFehler()
    {
        assertThat(BounceErkennungService.istDauerhafterFehler(tOnlineKlartextBounce())).isTrue();
    }

    /**
     * Der Bounce haengt unsere eigene Mail an. Steht darin zufaellig ein Wort
     * wie "verzoegert", darf das die Bewertung des Melde-Teils nicht kippen —
     * sonst verschluckt ausgerechnet unser eigener Angebotstext den Fehler.
     */
    @Test
    void laesstZitiertenOriginaltextDieBewertungNichtKippen()
    {
        String bounce = """
                Mail delivery failed: returning message to sender
                 <max.mustermann@example.com>
                   unknown user / Teilnehmer existiert nicht

                |------------------------- Message header follows: ----------------------|
                Subject: Angebot - Lieferung verzoegert sich, wir melden uns
                Message-ID: <abc@example.com>
                """;

        assertThat(BounceErkennungService.istDauerhafterFehler(bounce)).isTrue();
    }

    /** Umgekehrt: Der Melde-Teil endet an der Original-Markierung. */
    @Test
    void schneidetDenZitiertenOriginalteilAb()
    {
        String text = "Action: failed\n----- Original Message -----\nAction: delayed\n";

        assertThat(BounceErkennungService.berichtsTeil(text)).doesNotContain("delayed");
        assertThat(BounceErkennungService.istDauerhafterFehler(text)).isTrue();
    }

    // ======================= Parser-Einzelteile =======================

    @Test
    void extrahiertMessageIdsOhneDuplikateUndInReihenfolge()
    {
        String text = """
                Message-ID: <a@example.com>
                Message-Id: <b@example.com>
                message-id: <a@example.com>
                In-Reply-To: <ignoriert@example.com>
                References: <auch-ignoriert@example.com>
                """;
        assertThat(BounceErkennungService.extrahiereMessageIds(text))
                .containsExactly("<a@example.com>", "<b@example.com>");
    }

    @Test
    void extrahiertGrundAusStrukturiertemDsn()
    {
        String dsn = """
                Final-Recipient: rfc822; max.mustermann@example.com
                Action: failed
                Status: 5.1.1
                Diagnostic-Code: smtp; 550 5.1.1 <max.mustermann@example.com> User unknown
                """;
        assertThat(BounceErkennungService.extrahiereGrund(dsn))
                .contains("550 5.1.1")
                .contains("User unknown");
    }

    @Test
    void extrahiertGrundAusKlartextBlock()
    {
        assertThat(BounceErkennungService.extrahiereGrund(tOnlineKlartextBounce()))
                .contains("existiert nicht");
    }

    @Test
    void liefertAllgemeinenGrundWennNichtsErkennbarIst()
    {
        assertThat(BounceErkennungService.extrahiereGrund("Irgendein Text ohne Hinweise"))
                .isEqualTo("Zustellung fehlgeschlagen");
        assertThat(BounceErkennungService.extrahiereGrund(null))
                .isEqualTo("Zustellung fehlgeschlagen");
    }

    @Test
    void extrahiertKeineMessageIdsAusLeeremText()
    {
        assertThat(BounceErkennungService.extrahiereMessageIds(null)).isEqualTo(List.of());
        assertThat(BounceErkennungService.extrahiereMessageIds("")).isEmpty();
    }
}
