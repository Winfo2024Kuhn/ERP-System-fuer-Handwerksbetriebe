package org.example.kalkulationsprogramm.service.telefon;

import java.util.List;

/** Ergebnis des Verbindungstests: was die Telefonanlage über sich meldet. */
public record AnlagenInfo(List<String> eigeneNummern,
                          List<Anrufbeantworter> anrufbeantworter,
                          String landesvorwahl,
                          String ortsvorwahl) {

    public record Anrufbeantworter(int index, String name, boolean aktiv) {
    }
}
