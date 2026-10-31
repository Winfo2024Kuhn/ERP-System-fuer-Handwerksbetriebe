package org.example.kalkulationsprogramm.service.ki;

import java.util.List;

/**
 * Ein Modell, wie es der Anbieter in seiner Modellliste meldet.
 *
 * @param modell Name, mit dem das Programm das Modell anspricht
 * @param name   Anzeigename für die Auswahl
 * @param bilder kann das Modell Bilder lesen? {@code null}, wenn der Anbieter es nicht sagt
 * @param ton    versteht das Modell Ton (Aufnahmen)? {@code null}, wenn der Anbieter es nicht sagt
 * @param chat   taugt es für Gespräche/Auswertungen (und nicht nur für Vektoren, Texterkennung, Sprache)?
 * @param aliase weitere Namen, unter denen der Anbieter dasselbe Modell kennt
 */
public record KiModellInfo(String modell, String name, Boolean bilder, Boolean ton, boolean chat,
                           List<String> aliase) {

    public KiModellInfo {
        name = name == null || name.isBlank() ? modell : name;
        aliase = aliase == null ? List.of() : List.copyOf(aliase);
    }
}
