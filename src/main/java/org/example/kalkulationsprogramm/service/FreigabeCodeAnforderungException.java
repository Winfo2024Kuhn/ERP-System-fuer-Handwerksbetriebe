package org.example.kalkulationsprogramm.service;

import lombok.Getter;

/**
 * Ein Bestätigungscode kann gerade nicht verschickt werden. Der Controller übersetzt
 * den {@link Grund} in den HTTP-Status; die Meldung ist für den Kunden formuliert.
 */
@Getter
public class FreigabeCodeAnforderungException extends RuntimeException
{
    public enum Grund
    {
        /** Die Freigabe hat keine E-Mail-Adresse (→ 409). */
        KEINE_EMAIL,
        /** Freigabe abgelaufen, zurückgezogen oder schon angenommen (→ 410). */
        NICHT_OFFEN,
        /** Gesamtgrenze der Fehleingaben erreicht, nur noch telefonisch (→ 423). */
        ONLINE_GESPERRT,
        /** Wartezeit seit dem letzten Code oder Stundenlimit nicht vorbei (→ 429). */
        ZU_FRUEH,
        /** SMTP-Versand gescheitert, nichts wurde gespeichert (→ 502). */
        VERSAND_FEHLGESCHLAGEN
    }

    private final Grund grund;
    /** Nur bei {@link Grund#ZU_FRUEH}: Sekunden bis zum nächsten möglichen Versand. */
    private final Long erneutAnfordernInSekunden;

    public FreigabeCodeAnforderungException(Grund grund, String message, Long erneutAnfordernInSekunden)
    {
        super(message);
        this.grund = grund;
        this.erneutAnfordernInSekunden = erneutAnfordernInSekunden;
    }
}
