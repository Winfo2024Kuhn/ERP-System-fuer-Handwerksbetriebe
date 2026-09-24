# Beschaffung mit den EN1090-Komponenten

Freigegebener Ablauf: Bedarf erfassen → Liste drucken → Werkstatt meldet vorhandene Mengen → Fehlmengen direkt bestellen. Preisanfragen bleiben optional. Nutzerergänzungen: fertige Komponenten aus `feature/en1090-echeck` verwenden; Bestellungen dürfen ohne bekannten Preis vorbereitet und verschickt werden.

## Oberfläche und Daten

Die Bedarfsübersicht und Projektbedarfstabelle werden aus dem genannten Branch übernommen und an `/api/einkauf` angeschlossen. Der Materialdialog dieses Branches bleibt die Grundlage der Eingabe. Der zusätzliche Bereich „Für Werkstatt / auf Vorrat“ nutzt denselben Ablauf ohne Projekt. Freie Texte sind ohne Artikelstamm möglich. Bestehende technische Angaben und Zeichnungsbedarfe bleiben erhalten.

Die Tabelle zeigt benötigte, vorhandene und noch zu bestellende Mengen. Vorhanden bezieht sich ausschließlich auf diesen Bedarf, ohne Lagerbestandssystem. Der Werkstatt-Abgleich wird auf dem Server gespeichert, einschließlich Versionierung und nachvollziehbarer Änderungen. Änderungen dürfen bestehende Bestellungen/Entwürfe nicht überschreiben. Bereits in einem Bestellentwurf enthaltene Mengen werden nicht erneut angeboten.

Drucklisten enthalten Material, technische Angaben, Mengen und leere Felder für die Werkstatt. Die Liste umfasst alle Positionen im gewählten Bereich, unabhängig von der Bildschirmfilterung. Lieferantenwahl erfolgt bei der Bestellung. Interne Bestellnummern werden mit dem vorhandenen Nummernservice erzeugt. Ein fehlender Preis ist `null`, niemals ein kostenloser Artikel oder eine erfundene Nullsumme.

## Prüfung

Backend-Tests für freie Positionen, Projekt/ohne Projekt, Werkstatt-Abgleich (inklusive Konflikten und Teilmengen), druckbare Liste sowie Bestellen/Versenden mit offenen Preisen. Frontend- und E2E-Tests für den vollständigen Ablauf sowie deutsche Dezimaleingaben. Sichtprüfung bei 1440×900, 1536×960 und 1920×1080. Abschließend Review & Ship gemäß Projektregeln; vorhandene Fremdänderungen bleiben erhalten.
