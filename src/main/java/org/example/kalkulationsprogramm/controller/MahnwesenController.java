package org.example.kalkulationsprogramm.controller;

import java.util.Map;

import org.example.kalkulationsprogramm.service.AutoMahnVersandService;
import org.example.kalkulationsprogramm.service.AutoMahnVersandService.MahnlaufErgebnis;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Manueller Einstieg ins automatische Mahnverfahren. Der eigentliche Versand
 * laeuft taeglich per Cron ({@link AutoMahnVersandService}); dieser Endpoint
 * stoesst denselben Lauf sofort an — zum Testen nach Konfigurationsaenderungen
 * und fuer den Fall "ich will nicht bis morgen frueh warten".
 *
 * <p>Nur fuer Admins freigeschaltet (SecurityConfig), weil der Lauf echte
 * E-Mails an Kunden verschickt.</p>
 */
@RestController
@RequestMapping("/api/mahnwesen")
@RequiredArgsConstructor
public class MahnwesenController
{
    private final AutoMahnVersandService autoMahnVersandService;

    /**
     * Startet den Mahn-Lauf sofort und meldet zurueck, wie viele
     * Zahlungserinnerungen/Mahnungen verschickt wurden. Laeuft gerade schon
     * ein Lauf (Cron oder Doppel-Klick), antwortet der Endpoint mit 409 —
     * es wird kein zweiter Lauf gestartet (Schutz vor Doppel-Mahnungen).
     */
    @PostMapping("/lauf")
    public ResponseEntity<Map<String, Object>> starteMahnlauf()
    {
        MahnlaufErgebnis ergebnis = autoMahnVersandService.fuehreMahnlaufAus();

        return switch (ergebnis.status())
        {
            case LAEUFT_BEREITS -> ResponseEntity.status(HttpStatus.CONFLICT).body(antwort(
                    false, ergebnis,
                    "Der Mahn-Lauf läuft gerade schon. Bitte einen Moment warten "
                            + "und dann erneut versuchen."));
            case VERFAHREN_INAKTIV -> ResponseEntity.ok(antwort(
                    true, ergebnis,
                    "Das automatische Mahnverfahren ist ausgeschaltet. "
                            + "Bitte zuerst in den Einstellungen aktivieren."));
            case AUSGEFUEHRT -> ResponseEntity.ok(antwort(
                    ergebnis.fehlgeschlagen() == 0, ergebnis, ausgefuehrtMessage(ergebnis)));
        };
    }

    /**
     * Unterscheidet bewusst "nichts war faellig" von "es gab Fehler" — sonst
     * wuerde ein Lauf, bei dem jede faellige Rechnung mit einer Exception
     * abbricht, dem Admin faelschlich Entwarnung melden.
     */
    private static String ausgefuehrtMessage(MahnlaufErgebnis ergebnis)
    {
        StringBuilder sb = new StringBuilder();
        if (ergebnis.versendet() == 0 && ergebnis.fehlgeschlagen() == 0)
        {
            return "Lauf abgeschlossen — aktuell war keine Rechnung für eine "
                    + "Zahlungserinnerung oder Mahnung fällig.";
        }
        if (ergebnis.versendet() > 0)
        {
            sb.append(ergebnis.versendet())
              .append(" Zahlungserinnerung(en)/Mahnung(en) wurden per E-Mail an die Kunden verschickt.");
        }
        if (ergebnis.fehlgeschlagen() > 0)
        {
            if (sb.length() > 0) sb.append(" ");
            sb.append("Achtung: Bei ")
              .append(ergebnis.fehlgeschlagen())
              .append(" Rechnung(en) hat es nicht geklappt — Details stehen im Server-Protokoll.");
        }
        return sb.toString();
    }

    private static Map<String, Object> antwort(boolean success, MahnlaufErgebnis ergebnis, String message)
    {
        return Map.of(
                "success", success,
                "status", ergebnis.status().name(),
                "versendet", ergebnis.versendet(),
                "fehlgeschlagen", ergebnis.fehlgeschlagen(),
                "message", message);
    }
}
