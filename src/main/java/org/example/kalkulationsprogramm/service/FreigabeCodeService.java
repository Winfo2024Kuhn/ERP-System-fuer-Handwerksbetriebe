package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * Bestätigungscode per E-Mail für die digitale Freigabe.
 *
 * <p>Ablauf: Der Kunde klickt auf der Website auf "Freigeben" → {@link #fordereCodeAn}
 * erzeugt einen 6-stelligen Code und schickt ihn an die Adresse, an die das Dokument ging.
 * Bei der Annahme prüft {@link #pruefeUndVerbrauche} den Code in derselben Transaktion,
 * in der die Annahme gespeichert wird.</p>
 *
 * <p>Schutzregeln: Code 15 Minuten gültig. Nach 5 Fehlversuchen geht automatisch ein neuer
 * Code raus. Neuer Code frühestens nach 30 Sekunden und höchstens 5 Codes pro Stunde –
 * sonst ließe sich über den Link unser Mailkonto als Spamschleuder missbrauchen. Nach
 * insgesamt 15 Fehlversuchen ist die Online-Freigabe gesperrt (wer nur den Link hat,
 * kommt so auf höchstens 15 von einer Million Möglichkeiten); das Büro bekommt eine
 * Push-Nachricht und verschickt das Dokument bei Bedarf neu. Gespeichert wird nur ein
 * HMAC des Codes, nie der Code selbst.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FreigabeCodeService
{
    static final int CODE_STELLEN = 6;
    static final Duration GUELTIGKEIT = Duration.ofMinutes(15);
    /** Mindestabstand zwischen zwei Codes für denselben Link. */
    public static final long WARTEZEIT_SEKUNDEN = 30;
    static final Duration WARTEZEIT = Duration.ofSeconds(WARTEZEIT_SEKUNDEN);
    static final Duration LIMIT_FENSTER = Duration.ofHours(1);
    static final int MAX_CODES_PRO_FENSTER = 5;
    static final int MAX_FEHLVERSUCHE = 5;
    static final int MAX_FEHLVERSUCHE_GESAMT = 15;

    private static final String ANRUFEN = "Bitte rufen Sie uns an, dann bestätigen wir die Freigabe telefonisch.";

    private static final SecureRandom ZUFALL = new SecureRandom();

    private final DokumentFreigabeRepository repository;
    private final FreigabeCodeMailService mailService;
    private final WebPushService webPushService;
    private final org.springframework.core.task.TaskExecutor taskExecutor;
    private final Clock clock;

    @Value("${freigabe.hash.salt:CHANGE_ME_LOCAL_ONLY}")
    private String hashSalt;

    /** Antwort an die Website nach erfolgreichem Versand. */
    public record CodeVersendet(String emailMaskiert, LocalDateTime gueltigBis, long erneutAnfordernInSekunden)
    {
    }

    /**
     * Erzeugt einen neuen Code, speichert seinen HMAC und verschickt ihn – alles in einer
     * Transaktion auf der gesperrten Zeile. Scheitert der Versand, wird zurückgerollt:
     * Es bleibt weder ein Code stehen, den der Kunde nie bekam, noch zählt der Versuch
     * gegen Wartezeit oder Stundenlimit. Ein neuer Code macht den alten ungültig.
     *
     * @throws IllegalArgumentException mit {@link DokumentFreigabeService#UNBEKANNTE_UUID_MESSAGE}
     *         bei unbekannter UUID
     * @throws FreigabeCodeAnforderungException wenn gerade kein Code verschickt werden kann
     * @throws org.springframework.dao.PessimisticLockingFailureException wenn für denselben Link
     *         gerade ein anderer Versand läuft
     */
    @Transactional
    public CodeVersendet fordereCodeAn(String uuid)
    {
        // Ohne Warten: Läuft für diesen Link gerade ein Versand, scheitert der zweite Klick
        // sofort (PessimisticLockingFailureException → 429), statt eine Verbindung zu blockieren.
        DokumentFreigabe freigabe = repository.findByUuidZumSperrenOhneWarten(uuid)
                .orElseThrow(() -> new IllegalArgumentException(DokumentFreigabeService.UNBEKANNTE_UUID_MESSAGE));

        if (freigabe.getStatus() != FreigabeStatus.PENDING || freigabe.istAbgelaufen())
        {
            throw new FreigabeCodeAnforderungException(FreigabeCodeAnforderungException.Grund.NICHT_OFFEN,
                    "Dieser Link ist nicht mehr gültig. Bitte rufen Sie uns an, wir schicken Ihnen gern einen neuen.",
                    null);
        }
        if (istOnlineGesperrt(freigabe))
        {
            throw new FreigabeCodeAnforderungException(FreigabeCodeAnforderungException.Grund.ONLINE_GESPERRT,
                    "Die Online-Freigabe ist aus Sicherheitsgründen gesperrt. " + ANRUFEN, null);
        }
        if (freigabe.getKundeEmail() == null || freigabe.getKundeEmail().isBlank())
        {
            throw new FreigabeCodeAnforderungException(FreigabeCodeAnforderungException.Grund.KEINE_EMAIL,
                    "Für dieses Dokument ist keine E-Mail-Adresse hinterlegt. " + ANRUFEN, null);
        }

        return verschickeNeuenCode(freigabe, LocalDateTime.now(clock));
    }

    /**
     * Prüft den Code gegen die (bereits gesperrte) Freigabe und verbraucht ihn bei Erfolg.
     * Muss innerhalb der Annahme-Transaktion laufen. Bei falschem Code werden die Zähler
     * am Entity hochgesetzt, ggf. automatisch ein neuer Code verschickt, und dann
     * {@link FreigabeCodeException} geworfen – die Annahme-Transaktion rollt darauf nicht
     * zurück (noRollbackFor), damit all das gespeichert bleibt.
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = FreigabeCodeException.class)
    public void pruefeUndVerbrauche(DokumentFreigabe freigabe, String code)
    {
        if (istOnlineGesperrt(freigabe))
        {
            throw onlineGesperrt();
        }
        if (freigabe.getCodeHash() == null)
        {
            throw new FreigabeCodeException(FreigabeCodeException.Grund.NICHT_ANGEFORDERT,
                    "Bitte fordern Sie zuerst einen Code an.", null);
        }
        LocalDateTime jetzt = LocalDateTime.now(clock);
        if (freigabe.getCodeGueltigBis() == null || jetzt.isAfter(freigabe.getCodeGueltigBis()))
        {
            freigabe.setCodeHash(null);
            throw new FreigabeCodeException(FreigabeCodeException.Grund.ABGELAUFEN,
                    "Der Code ist abgelaufen. Bitte fordern Sie einen neuen Code an.", null);
        }

        String bereinigt = code == null ? "" : code.replaceAll("[^0-9]", "");
        boolean passt = bereinigt.length() == CODE_STELLEN
                && MessageDigest.isEqual(
                        hmac(freigabe.getUuid(), bereinigt).getBytes(StandardCharsets.US_ASCII),
                        freigabe.getCodeHash().getBytes(StandardCharsets.US_ASCII));
        if (!passt)
        {
            throw fehlversuch(freigabe, jetzt);
        }

        freigabe.setCodeHash(null);
        freigabe.setCodeGueltigBis(null);
        freigabe.setCodeFehlversuche(0);
        // Auf Mikrosekunden gekürzt, wie TIMESTAMP(6) speichert – der Wert fließt in den
        // Acceptance-Hash ein und muss sich aus der Datenbank nachrechnen lassen.
        freigabe.setCodeBestaetigtAm(jetzt.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        freigabe.setCodeBestaetigtEmail(freigabe.getKundeEmail());
    }

    /** {@code true}, sobald die Gesamtgrenze der Fehleingaben erreicht ist. */
    public static boolean istOnlineGesperrt(DokumentFreigabe freigabe)
    {
        return freigabe.getCodeFehlversucheGesamt() >= MAX_FEHLVERSUCHE_GESAMT;
    }

    /**
     * Zählt den Fehlversuch und liefert die passende Ausnahme: FALSCH, nach dem fünften
     * Fehler NEUER_CODE_VERSCHICKT (bzw. GESPERRT, wenn gerade keiner rausgehen darf),
     * an der Gesamtgrenze ONLINE_GESPERRT.
     */
    private FreigabeCodeException fehlversuch(DokumentFreigabe freigabe, LocalDateTime jetzt)
    {
        int fehlversuche = freigabe.getCodeFehlversuche() + 1;
        freigabe.setCodeFehlversuche(fehlversuche);
        freigabe.setCodeFehlversucheGesamt(freigabe.getCodeFehlversucheGesamt() + 1);

        if (istOnlineGesperrt(freigabe))
        {
            freigabe.setCodeHash(null);
            meldeSperreAnsBuero(freigabe);
            return onlineGesperrt();
        }
        // Was zuerst erreicht wird, zählt: fünf Fehler für diesen Code oder die Gesamtgrenze.
        int verbleibend = Math.min(MAX_FEHLVERSUCHE - fehlversuche,
                MAX_FEHLVERSUCHE_GESAMT - freigabe.getCodeFehlversucheGesamt());
        if (verbleibend > 0)
        {
            return new FreigabeCodeException(FreigabeCodeException.Grund.FALSCH,
                    "Der Code stimmt nicht. Bitte prüfen Sie die Ziffern aus der E-Mail.", verbleibend);
        }

        freigabe.setCodeHash(null);
        try
        {
            CodeVersendet neu = verschickeNeuenCode(freigabe, jetzt);
            return new FreigabeCodeException(FreigabeCodeException.Grund.NEUER_CODE_VERSCHICKT,
                    "Der Code wurde zu oft falsch eingegeben. Wir haben Ihnen einen neuen Code geschickt.",
                    null, neu.erneutAnfordernInSekunden(), neu.emailMaskiert());
        }
        catch (FreigabeCodeAnforderungException e)
        {
            return new FreigabeCodeException(FreigabeCodeException.Grund.GESPERRT,
                    "Der Code wurde zu oft falsch eingegeben. Bitte fordern Sie einen neuen Code an.",
                    null, e.getErneutAnfordernInSekunden(), null);
        }
    }

    /**
     * Prüft die Versandgrenzen, setzt den neuen Code und verschickt ihn. Scheitert der
     * Versand, werden alle Code-Felder auf den Stand davor zurückgesetzt – auch wenn die
     * umgebende Transaktion (Annahme, noRollbackFor) trotzdem committet.
     */
    private CodeVersendet verschickeNeuenCode(DokumentFreigabe freigabe, LocalDateTime jetzt)
    {
        CodeStand vorher = CodeStand.von(freigabe);
        pruefeVersandgrenzen(freigabe, jetzt);

        String code = erzeugeCode();
        LocalDateTime gueltigBis = jetzt.plus(GUELTIGKEIT);
        freigabe.setCodeHash(hmac(freigabe.getUuid(), code));
        freigabe.setCodeGueltigBis(gueltigBis);
        freigabe.setCodeFehlversuche(0);
        freigabe.setCodeAngefordertAm(jetzt);
        freigabe.setCodeFensterAnzahl(freigabe.getCodeFensterAnzahl() + 1);
        repository.saveAndFlush(freigabe);

        try
        {
            mailService.sendeCode(freigabe, code, gueltigBis);
        }
        catch (Exception e)
        {
            vorher.zurueckAuf(freigabe);
            // Ohne Empfänger-Adresse geloggt (DSGVO) – die UUID reicht zum Wiederfinden.
            log.error("Bestätigungscode für Freigabe {} konnte nicht verschickt werden: {}",
                    freigabe.getUuid(), e.getMessage(), e);
            throw new FreigabeCodeAnforderungException(FreigabeCodeAnforderungException.Grund.VERSAND_FEHLGESCHLAGEN,
                    "Der Code konnte gerade nicht verschickt werden. Bitte versuchen Sie es gleich noch einmal "
                            + "oder rufen Sie uns an.",
                    null);
        }

        return new CodeVersendet(maskiereEmail(freigabe.getKundeEmail()), gueltigBis, WARTEZEIT_SEKUNDEN);
    }

    /** Die Code-Felder vor einem Versand – für das Zurücksetzen, wenn der Versand scheitert. */
    private record CodeStand(String hash, LocalDateTime gueltigBis, int fehlversuche, LocalDateTime angefordertAm,
                             LocalDateTime fensterStart, int fensterAnzahl)
    {
        static CodeStand von(DokumentFreigabe f)
        {
            return new CodeStand(f.getCodeHash(), f.getCodeGueltigBis(), f.getCodeFehlversuche(),
                    f.getCodeAngefordertAm(), f.getCodeFensterStart(), f.getCodeFensterAnzahl());
        }

        void zurueckAuf(DokumentFreigabe f)
        {
            f.setCodeHash(hash);
            f.setCodeGueltigBis(gueltigBis);
            f.setCodeFehlversuche(fehlversuche);
            f.setCodeAngefordertAm(angefordertAm);
            f.setCodeFensterStart(fensterStart);
            f.setCodeFensterAnzahl(fensterAnzahl);
        }
    }

    private void meldeSperreAnsBuero(DokumentFreigabe freigabe)
    {
        String art = freigabe.getDokumentArt() == null ? "Dokument" : freigabe.getDokumentArt();
        String kunde = freigabe.getKundeName() == null || freigabe.getKundeName().isBlank()
                ? "Ein Kunde" : freigabe.getKundeName();
        String text = kunde + ": " + art + " " + freigabe.getDokumentNummer()
                + " – Bestätigungscode zu oft falsch eingegeben. Bitte Kunden anrufen "
                + "oder das Dokument neu verschicken.";
        String uuid = freigabe.getUuid();
        // Erst nach dem Commit und im Hintergrund: Das Büro hört nur von Sperren, die wirklich
        // gespeichert sind; der Push bekommt eine eigene Transaktion (abgelaufene Abos löschen)
        // und hält die Antwort an den Kunden nicht auf.
        nachDemCommit(() -> {
            try
            {
                taskExecutor.execute(() -> sendePush(text, uuid));
            }
            catch (Exception e)
            {
                log.warn("Push zur gesperrten Freigabe {} nicht gestartet: {}", uuid, e.getMessage());
            }
        });
    }

    private void sendePush(String text, String uuid)
    {
        try
        {
            webPushService.notifyFreigabeAnnahme("Online-Freigabe gesperrt", text, "/zeiterfassung/projekte");
        }
        catch (Exception e)
        {
            log.warn("Push zur gesperrten Freigabe {} fehlgeschlagen: {}", uuid, e.getMessage());
        }
    }

    /** Führt die Aktion nach erfolgreichem Commit aus; ohne laufende Transaktion sofort. */
    private static void nachDemCommit(Runnable aktion)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            aktion.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                aktion.run();
            }
        });
    }

    private static FreigabeCodeException onlineGesperrt()
    {
        return new FreigabeCodeException(FreigabeCodeException.Grund.ONLINE_GESPERRT,
                "Der Code wurde zu oft falsch eingegeben. Die Online-Freigabe ist deshalb gesperrt. " + ANRUFEN,
                null);
    }

    private void pruefeVersandgrenzen(DokumentFreigabe freigabe, LocalDateTime jetzt)
    {
        LocalDateTime letzter = freigabe.getCodeAngefordertAm();
        if (letzter != null)
        {
            long restSekunden = Duration.between(jetzt, letzter.plus(WARTEZEIT)).toSeconds();
            if (restSekunden > 0)
            {
                throw zuFrueh("Bitte warten Sie noch einen Moment, bevor Sie einen neuen Code anfordern.",
                        restSekunden);
            }
        }

        LocalDateTime fensterStart = freigabe.getCodeFensterStart();
        if (fensterStart == null || !jetzt.isBefore(fensterStart.plus(LIMIT_FENSTER)))
        {
            freigabe.setCodeFensterStart(jetzt);
            freigabe.setCodeFensterAnzahl(0);
            return;
        }
        if (freigabe.getCodeFensterAnzahl() >= MAX_CODES_PRO_FENSTER)
        {
            long restSekunden = Math.max(1, Duration.between(jetzt, fensterStart.plus(LIMIT_FENSTER)).toSeconds());
            throw zuFrueh("Es wurden schon mehrere Codes verschickt. Bitte versuchen Sie es später noch einmal "
                    + "oder rufen Sie uns an.", restSekunden);
        }
    }

    private static FreigabeCodeAnforderungException zuFrueh(String meldung, long sekunden)
    {
        return new FreigabeCodeAnforderungException(FreigabeCodeAnforderungException.Grund.ZU_FRUEH, meldung, sekunden);
    }

    static String erzeugeCode()
    {
        // 000000–999999, führende Nullen bleiben erhalten.
        return String.format("%0" + CODE_STELLEN + "d", ZUFALL.nextInt(1_000_000));
    }

    String hmac(String uuid, String code)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSalt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] roh = mac.doFinal((uuid + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(roh);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("HMAC-SHA256 nicht verfügbar", e);
        }
    }

    /**
     * "max.mustermann@example.de" → "m•••@example.de". Mehrere Adressen (Komma oder
     * Semikolon getrennt) werden einzeln maskiert. Die Domain bleibt sichtbar, damit der
     * Kunde weiß, in welchem Postfach er nachsehen muss.
     */
    static String maskiereEmail(String adressen)
    {
        if (adressen == null) return "";
        return Arrays.stream(adressen.split("[,;]"))
                .map(String::trim)
                .filter(a -> !a.isEmpty())
                .map(FreigabeCodeService::maskiereEinzelneAdresse)
                .collect(Collectors.joining(", "));
    }

    private static String maskiereEinzelneAdresse(String adresse)
    {
        int at = adresse.lastIndexOf('@');
        if (at <= 0) return "•••";
        return adresse.charAt(0) + "•••" + adresse.substring(at);
    }
}
