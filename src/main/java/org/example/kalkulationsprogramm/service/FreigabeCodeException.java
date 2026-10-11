package org.example.kalkulationsprogramm.service;

import lombok.Getter;

/**
 * Der Bestätigungscode bei der digitalen Annahme passt nicht. Die Annahme-Transaktion
 * läuft mit {@code noRollbackFor} auf diese Ausnahme, damit Fehlversuche, Sperren und
 * ein automatisch nachgeschickter Code gespeichert bleiben.
 */
@Getter
public class FreigabeCodeException extends RuntimeException
{
    public enum Grund
    {
        /** Code stimmt nicht, es sind noch Versuche übrig. */
        FALSCH,
        /** Code war richtig formatiert, ist aber abgelaufen. */
        ABGELAUFEN,
        /** Zu viele Fehlversuche, ein neuer Code ging automatisch raus. */
        NEUER_CODE_VERSCHICKT,
        /** Zu viele Fehlversuche, automatischer Versand gerade nicht möglich. */
        GESPERRT,
        /** Gesamtgrenze erreicht – Online-Freigabe gesperrt, Kunde soll anrufen. */
        ONLINE_GESPERRT,
        /** Für diese Freigabe ist gerade kein Code offen. */
        NICHT_ANGEFORDERT
    }

    private final Grund grund;
    /** Nur bei {@link Grund#FALSCH} gesetzt, sonst {@code null}. */
    private final Integer verbleibendeVersuche;
    /** Bei {@link Grund#NEUER_CODE_VERSCHICKT} und ggf. {@link Grund#GESPERRT}: Wartezeit bis zum nächsten Code. */
    private final Long erneutAnfordernInSekunden;
    /** Nur bei {@link Grund#NEUER_CODE_VERSCHICKT}: wohin der neue Code ging. */
    private final String emailMaskiert;

    public FreigabeCodeException(Grund grund, String message, Integer verbleibendeVersuche)
    {
        this(grund, message, verbleibendeVersuche, null, null);
    }

    public FreigabeCodeException(Grund grund, String message, Integer verbleibendeVersuche,
                                 Long erneutAnfordernInSekunden, String emailMaskiert)
    {
        super(message);
        this.grund = grund;
        this.verbleibendeVersuche = verbleibendeVersuche;
        this.erneutAnfordernInSekunden = erneutAnfordernInSekunden;
        this.emailMaskiert = emailMaskiert;
    }
}
