package org.example.kalkulationsprogramm.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

/**
 * Schickt eine neue Überschneidungs-Meldung als Push ans Handy des Mitarbeiters – erst nach
 * dem Commit, damit kein Push für eine zurückgerollte Buchung rausgeht. Ein Push-Fehler
 * ändert an der Buchung nichts ({@link WebPushService#notifyMitarbeiter} fängt ihn ab).
 */
@Component
@RequiredArgsConstructor
public class UeberschneidungPushListener {

    /** Nachrichtenart für den Service Worker der Handy-App. */
    static final String PUSH_TYP = "ZEIT_UEBERSCHNEIDUNG";

    /** Tippen auf den Push öffnet die Tagesbuchungen in der Handy-App (nicht das PC-Programm unter "/"). */
    static final String ZIEL = "/zeiterfassung/tagesbuchungen";

    private final WebPushService webPushService;

    @TransactionalEventListener(fallbackExecution = true)
    public void beiNeuerMeldung(ZeitbuchungUeberschneidungService.UeberschneidungGemeldet meldung) {
        webPushService.notifyMitarbeiter(meldung.mitarbeiterId(), meldung.titel(), meldung.text(), ZIEL, PUSH_TYP);
    }
}
