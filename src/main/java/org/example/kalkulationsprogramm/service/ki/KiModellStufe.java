package org.example.kalkulationsprogramm.service.ki;

/**
 * Anbieterneutrale Modellwahl. Jeder Anbieter ordnet jeder Stufe ein Modell
 * zu; die Dienste wählen nur die Stufe, nie einen Modellnamen.
 */
public enum KiModellStufe {
    /** Kurze, häufige Aufgaben wie das Zuordnen von E-Mails. */
    SCHNELL,
    /** Dokumente auslesen, Texte schreiben. */
    STANDARD,
    /** Zweiter Versuch bei schwer lesbaren Dokumenten und anspruchsvolle Texte. */
    GRUENDLICH
}
