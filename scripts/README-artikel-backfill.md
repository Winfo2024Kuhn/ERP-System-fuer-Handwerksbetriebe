# Artikel-Dokumenttexte nachtragen

Fuellt Kurzbeschreibung (Innensicht fuer den Angebots-Editor) und
Beschreibung (Kundentext auf Angebot, Rechnung und Freigabe-Seite) fuer
Bestandsartikel, bei denen beide Felder noch leer sind.

Es braucht nur Python 3 (Standardbibliothek, keine Pakete).

## Anmeldung

`/api/artikel` verlangt einen angemeldeten Benutzer. Benutzer angeben,
das Passwort wird unsichtbar abgefragt:

    python scripts/artikel_dokumenttexte_backfill.py --url http://localhost:8080 --benutzer meinname

Alternativ die Umgebungsvariablen `ERP_BENUTZER` und `ERP_PASSWORT` setzen.
Das Passwort nicht als `--passwort` in die Kommandozeile tippen, wenn es
nicht sein muss - es landet sonst im Shell-Verlauf.

## Trockenlauf (Standard)

    python scripts/artikel_dokumenttexte_backfill.py --url http://localhost:8080 --benutzer meinname

Schreibt nichts. Erzeugt `artikel_backfill.csv` (Semikolon-getrennt, laesst
sich direkt in Excel oeffnen) mit einer Zeile je Artikel:

| Spalte | Inhalt |
| --- | --- |
| `aktion` | `geplant`, `uebersprungen: schon gepflegt`, `uebersprungen: zu wenig Daten` oder `fehler: ...` |
| `neue_kurzbeschreibung` | Innensicht, z.B. `Rundrohr 42.4x2 1.4301` |
| `neue_beschreibung` | Kundentext als HTML, z.B. `<p>Rundrohr 42,4 x 2 mm aus Edelstahl 1.4301</p>` |

Diese Datei durchsehen, bevor es weitergeht. Anderer Ablageort:
`--protokoll pfad/zur/datei.csv`.

## Schreiben

Erst nach Durchsicht der CSV:

    python scripts/artikel_dokumenttexte_backfill.py --url http://localhost:8080 --benutzer meinname --apply

## Wie die Texte entstehen

- Kundentext-Regelfall (Halbzeug mit Profilform und Abmessung):
  `Rundrohr 42,4 x 2 mm aus Edelstahl 1.4301, nahtlos`. Werkstoffcodes
  werden in Handwerker-Sprache uebersetzt (Zuordnung wie in Migration V337),
  Normkuerzel wie `EN 10219-2` bleiben weg. `nahtlos`, `kaltgezogen` und
  `geschliffen` stehen dabei, weil sie sonst gleiche Positionen ununterscheidbar
  machen wuerden - alle uebrigen Verfahren sind der Normalfall und entfallen.
- Rueckfallebene (z.B. Beschlaege): Der Produktname traegt den Text, sofern er
  mit einem echten Wort beginnt (`Tuerschliesser TS 3000`), ergaenzt um
  Werkstoff und Abmessung, falls vorhanden.
- Fehlt eine Angabe, entfaellt sie ersatzlos - es wird nichts erfunden.
- Reicht es nicht fuer einen brauchbaren Text, wird der Artikel uebersprungen
  und in der CSV protokolliert.

## Was das Skript nicht anfasst

- Artikel, die bereits eine Kurzbeschreibung oder Beschreibung haben
- Artikel, bei denen zu wenige Angaben fuer einen sinnvollen Text vorliegen
- Den Verkaufsaufschlag - das ist eine kaufmaennische Entscheidung; das Feld
  wird gar nicht erst mitgesendet (der Endpunkt ist ein echtes Teil-Update)

## Exit-Codes

| Code | Bedeutung |
| --- | --- |
| 0 | Durchgelaufen |
| 1 | Durchgelaufen, aber einzelne Artikel liessen sich nicht schreiben |
| 2 | Gar nicht erst loslaufen koennen (Server nicht erreichbar, Login fehlgeschlagen) |
