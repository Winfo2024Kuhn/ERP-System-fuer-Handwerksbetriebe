package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Urlaubskonto eines Mitarbeiters für ein Jahr – eine Zahl für alle Anzeigen
 * und für die Prüfung neuer Urlaubsanträge ({@link UrlaubskontoService}).
 *
 * @param jahr              Jahr des Kontos
 * @param jahresanspruch    Jahresurlaub laut Mitarbeiter (ohne Angabe 30), im Eintrittsjahr anteilig
 *                          ({@link UrlaubskontoService#anspruchImJahr})
 * @param uebertrag         Resturlaub aus dem Vorjahr, der in dieses Jahr übertragen wurde
 * @param uebertragManuell  {@code true}, wenn das Büro den Übertrag von Hand festgelegt hat
 * @param verfallsdatum     letzter Tag, an dem der Übertrag genommen werden kann;
 *                          {@code null} = Resturlaub verfällt nicht
 * @param verfallen         Tage aus dem Vorjahr, die zum Stichtag schon verfallen sind
 * @param ausVorjahrOffen   Tage aus dem Vorjahr, die noch offen sind (Teil von {@code verbleibend})
 * @param genommen          Urlaubstage vor heute (halbe Tage zählen 0,5)
 * @param geplant           Urlaubstage ab heute
 * @param korrektur         Summe der Urlaubskorrekturen aus dem Zeitkonto
 * @param verbleibend       Anspruch + Übertrag − verfallen − genommen − geplant + Korrektur, nie unter 0
 */
public record Urlaubskonto(
        int jahr,
        BigDecimal jahresanspruch,
        BigDecimal uebertrag,
        boolean uebertragManuell,
        LocalDate verfallsdatum,
        BigDecimal verfallen,
        BigDecimal ausVorjahrOffen,
        BigDecimal genommen,
        BigDecimal geplant,
        BigDecimal korrektur,
        BigDecimal verbleibend) {
}
