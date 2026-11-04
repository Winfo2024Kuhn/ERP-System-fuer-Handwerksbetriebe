package org.example.kalkulationsprogramm.service.mail.suche;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.repository.EmailSuchQuellen;
import org.example.kalkulationsprogramm.repository.EmailSucheRepository;
import org.example.kalkulationsprogramm.util.suche.EmailSuchtext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Füllt den Suchtext ({@code email.suchtext}) für Mails, die noch keinen haben: den Altbestand nach
 * der Migration V447 und einzelne Mails, deren Text sich geändert hat, ohne dass die Anhänge
 * geladen waren ({@code Email#onUpdate}).
 *
 * <p>Läuft eine Minute nach dem Start im Hintergrund und danach alle 15 Minuten, in Blöcken mit je
 * eigener Transaktion, mit derselben Normalisierung wie beim Speichern ({@link EmailSuchtext}).
 * Idempotent: Es fasst nur Mails mit {@code suchtext IS NULL} an und überschreibt nie einen
 * inzwischen beim Speichern gesetzten Suchtext. Ins Log kommen nur Zahlen, keine Mail-Inhalte.
 * Kein Kontakt nach außen. Beim Herunterfahren (Thread unterbrochen) endet der Lauf nach dem
 * laufenden Block.</p>
 */
@Slf4j
@Service
public class EmailSuchtextNachpflege {

    /** Schutz gegen eine Endlosschleife, falls ein Block nicht kleiner wird. */
    private static final int MAX_BLOECKE_JE_LAUF = 10_000;

    private final EmailSucheRepository repository;
    private final TransactionTemplate transaktion;
    private final boolean aktiv;
    private final int blockGroesse;

    public EmailSuchtextNachpflege(EmailSucheRepository repository, PlatformTransactionManager transaktionen,
            @Value("${email.suchtext.nachpflege-aktiv:true}") boolean aktiv,
            @Value("${email.suchtext.nachpflege-block:200}") int blockGroesse) {
        this.repository = repository;
        this.transaktion = new TransactionTemplate(transaktionen);
        this.aktiv = aktiv;
        this.blockGroesse = Math.max(1, blockGroesse);
    }

    /** Hintergrund-Lauf; Fehler werden geloggt und beim nächsten Lauf neu versucht. */
    @Scheduled(initialDelayString = "${email.suchtext.nachpflege-start-ms:60000}",
            fixedDelayString = "${email.suchtext.nachpflege-abstand-ms:900000}")
    public void geplant() {
        if (!aktiv) {
            return;
        }
        try {
            nachpflegen();
        } catch (RuntimeException e) {
            log.warn("[Suchtext] Nachpflege abgebrochen, nächster Versuch beim nächsten Lauf: {}",
                    e.getClass().getSimpleName());
        }
    }

    /** Füllt alle Mails ohne Suchtext; Anzahl der gefüllten Mails. */
    public int nachpflegen() {
        int gesamt = 0;
        for (int block = 0; block < MAX_BLOECKE_JE_LAUF; block++) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("[Suchtext] Nachpflege unterbrochen (Herunterfahren), {} Mails bis hierher", gesamt);
                break;
            }
            Block ergebnis = transaktion.execute(status -> naechsterBlock());
            if (ergebnis == null) {
                break;
            }
            gesamt += ergebnis.gefuellt();
            if (ergebnis.gelesen() < blockGroesse) {
                break;
            }
            log.info("[Suchtext] {} Mails mit Suchtext nachgepflegt, weiter …", gesamt);
        }
        if (gesamt > 0) {
            log.info("[Suchtext] Nachpflege fertig: {} Mails durchsuchbar gemacht", gesamt);
        }
        return gesamt;
    }

    /** Gelesene und tatsächlich gefüllte Mails eines Blocks. */
    private record Block(int gelesen, int gefuellt) {
    }

    private Block naechsterBlock() {
        List<EmailSuchQuellen> quellen = repository.findOhneSuchtext(PageRequest.of(0, blockGroesse));
        if (quellen.isEmpty()) {
            return new Block(0, 0);
        }
        Map<Long, List<String>> dateinamen = dateinamen(quellen.stream().map(EmailSuchQuellen::id).toList());
        int gefuellt = 0;
        for (EmailSuchQuellen q : quellen) {
            String suchtext = EmailSuchtext.aus(new EmailSuchtext.Quellen(q.fromAddress(), q.replyToAddress(),
                    q.recipient(), q.cc(), q.subject(), q.body(), q.htmlBody(),
                    dateinamen.getOrDefault(q.id(), List.of())));
            gefuellt += repository.setzeSuchtext(q.id(), suchtext);
        }
        return new Block(quellen.size(), gefuellt);
    }

    private Map<Long, List<String>> dateinamen(List<Long> ids) {
        Map<Long, List<String>> namen = new HashMap<>();
        for (Object[] zeile : repository.findDateinamen(ids)) {
            if (zeile[1] != null) {
                namen.computeIfAbsent((Long) zeile[0], id -> new ArrayList<>()).add((String) zeile[1]);
            }
        }
        return namen;
    }
}
