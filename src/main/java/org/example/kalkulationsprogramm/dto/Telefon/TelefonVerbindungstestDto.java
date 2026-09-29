package org.example.kalkulationsprogramm.dto.Telefon;

import java.util.List;

/** Ergebnis von "Verbindung testen": was die FRITZ!Box meldet. */
public record TelefonVerbindungstestDto(boolean erfolgreich,
                                        String meldung,
                                        List<String> eigeneNummern,
                                        List<AnrufbeantworterDto> anrufbeantworter,
                                        String landesvorwahl,
                                        String ortsvorwahl) {

    public static TelefonVerbindungstestDto fehler(String meldung) {
        return new TelefonVerbindungstestDto(false, meldung, List.of(), List.of(), null, null);
    }
}
