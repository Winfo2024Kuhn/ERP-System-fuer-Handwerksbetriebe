# E-Mail-Center: Funktions- und Designprüfung

Nutzervorgaben: Das Ribbon bleibt unverändert. E-Mail-Aktionen werden kompakter,
Entwürfe speichern Anhänge und verschwinden nach erfolgreichem Versand. Das Öffnen
einer Nachricht erhält Ordner und Suche. Alle PC-Toasts schweben oben rechts, ohne
Anwendung oder Dialoge zu verschieben.

## Geprüfte Abläufe

- `email-center-compact.spec.ts`: Aktionenmenü inklusive Tastatur und Fokus-Rückgabe,
  eingeklappte Ordner mit vollständigen Symbolen, begrenzte gespeicherte
  Spaltenbreiten und niedriges Fenster (1440 × 800).
- `email-center-context.spec.ts`: Markiert/Nicht zugeordnet mit lokaler Suche,
  ordnerübergreifende Suche und Direktlink bei leerer Ansicht. Die ersten beiden
  Abläufe und der leere Direktlink reproduzierten den Fehler vor der Korrektur.
- `email-draft-attachments.spec.ts`: Anhänge speichern, wieder öffnen und versenden;
  Verlassen über Ordner, Ribbon und Browsernavigation; fehlgeschlagene Speicherung;
  erfolgreicher Versand ohne zusätzliche Löschanfrage und ohne späteres Wiederanlegen.
- `email-thread-cleanup.spec.ts`: zitierte Verläufe einklappen und Original wieder
  öffnen; neue Antworten und aktuelle Signatur erhalten.
- `toast-bei-dialog.spec.ts`: Meldungen über offenen Dialogen, unveränderte
  Anwendungs-/Dialogpositionen, Tastaturbedienung, Schließen ohne Entwurfsverlust
  sowie ein begrenzter Meldungsstapel bei 540 Pixel Fensterhöhe.

Alle Desktop-Specs laufen bei 1440 × 900, 1536 × 960 und 1920 × 1080.
Screenshots der geänderten Abläufe wurden angesehen. Tests verwenden Dummy-Daten
und blockieren externe Netzwerkzugriffe.

## Sechs Design-/UX-Fragen

| Frage | 1440 × 900 | 1536 × 960 und 1920 × 1080 |
| --- | --- | --- |
| Farben klar unterscheidbar? | Aktiver Ordner in Rose, inaktive Navigation in Slate; Fehlermeldungen mit Symbol und Text. | Gleiche Zustände und Farbgebung, keine zusätzliche Akzentpalette. |
| Design-System eingehalten? | Gemeinsame Buttons/Selects, Lucide-Symbole, bestehende Schrift und Rose/Slate. Aktionenmenü aus shadcn/Radix angepasst. | Identische Bausteine; Ribbon unverändert. |
| Ruhiges Look-and-Feel? | Kurzer Nachrichtenkopf, weniger doppelte Metadaten und kompakte Ordnerabstände. | Leserbereich nimmt zusätzlichen Platz auf; Aktionen bleiben rechts am Kopf. |
| Gute UX? | Antworten direkt sichtbar; seltenere Aktionen im benannten Menü. Speicherstatus im Entwurf und konkrete Fehler als Toast. | Gleiche Bedienung ohne wechselnde Menüstruktur. |
| Aktionen auffindbar? | Antworten, Menü und Senden im sichtbaren Bereich. Ordnerliste scrollbar, Einstellungen am unteren Rand. | Gleiche Anordnung; ausreichend Platz zum Lesen und Schreiben. |
| Überschneidungen/Abschneiden? | Begrenzte Spaltenbreiten und gemessene Aktionsgrenzen; kein horizontaler Überlauf des E-Mail-Hauptbereichs. | Gleiche automatischen Grenzen geprüft. Toasts überlagern auf ausdrücklichen Nutzerwunsch kurz einen kleinen Bereich oben rechts und lassen sich schließen. |

Das Einordnen seltener Aktionen ins Menü und die schwebenden Toasts folgen den
ausdrücklichen Nutzervorgaben, auch wenn ältere allgemeine Designregeln alle Aktionen
direkt sichtbar bzw. Toasts mit reservierter Fläche vorsahen.

## Grenzen

Zitaterkennung bleibt bewusst konservativ: unklare Nachrichten werden nicht gekürzt;
das Original bleibt zugänglich. Die Browserprüfungen stubben SMTP/API. Persistenz,
Anhangvalidierung und das Löschen nach SMTP-Erfolg werden zusätzlich serverseitig
mit Controller-, Sicherheits- und H2-Transaktionstests geprüft.
