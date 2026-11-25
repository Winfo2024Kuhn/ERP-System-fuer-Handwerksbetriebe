package org.example.kalkulationsprogramm.util;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Anzeigenamen von Personen (Ansprechpartner von Steuerberatern und Lieferanten). */
public final class PersonenName {

    private PersonenName() {
    }

    /** „Vorname Nachname“ ohne überzählige Leerzeichen; leer, wenn beides fehlt. */
    public static String vollerName(String vorname, String nachname) {
        return Stream.of(vorname, nachname)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(teil -> !teil.isEmpty())
                .collect(Collectors.joining(" "));
    }
}
