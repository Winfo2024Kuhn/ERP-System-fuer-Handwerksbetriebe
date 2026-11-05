import type { InfoText } from './berichtTypen';

/** Erklärtexte der Info-Knöpfe im Report Centre (passend zum Backend-Verhalten). */

export const INFO_STUNDENKONTO: InfoText = {
    titel: 'Stundenkonto',
    hintergrund: 'Alle Plus- und Minusstunden seit Eintritt bis zum Monatsende, inklusive Korrekturen.',
    bewirkt: 'Der Steuerberater sieht den Stand, ändert am Lohn aber nichts. Abgeschlossene Monate bleiben unverändert.',
};

export const INFO_RESTURLAUB: InfoText = {
    titel: 'Resturlaub',
    hintergrund: 'Jahresurlaub plus Übertrag aus dem Vorjahr, minus genommene und schon geplante Tage.',
    bewirkt: 'Zeigt die Tage, die noch frei sind. Verfallene Tage aus dem Vorjahr sind schon abgezogen.',
};

export const INFO_NICHT_ABGESCHLOSSEN: InfoText = {
    titel: 'Monat noch nicht abgeschlossen',
    hintergrund: 'Solange der Monat offen ist, rechnet das Programm bei jeder neuen Buchung neu.',
    bewirkt: 'Schließe den Monat unter Monatsabschluss ab, dann ändern sich die Zahlen im Bericht nicht mehr.',
};

export const INFO_UNTERSCHRIFT: InfoText = {
    titel: 'Unterschriftszeile',
    hintergrund: 'Am Ende jedes Mitarbeiter-Berichts kommt eine Zeile für Datum und Unterschrift von Mitarbeiter und Betrieb.',
    bewirkt: 'So kann der Mitarbeiter seine Stunden schriftlich bestätigen.',
};
