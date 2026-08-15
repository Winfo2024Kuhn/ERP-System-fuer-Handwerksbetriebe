# Artikel-Dokumenttexte nachtragen

Fuellt Kurzbeschreibung (Innensicht fuer den Angebots-Editor) und
Beschreibung (Kundentext auf Angebot, Rechnung und Freigabe-Seite) fuer
Bestandsartikel, bei denen beide Felder noch leer sind.

Es braucht nur Python 3 (Standardbibliothek, keine Pakete).

## Wann das laufen muss

**Vor der Freigabe des Material-Knopfs im Dokument-Editor.** Der Knopf fuegt
Artikel als Position in Angebote und Rechnungen ein; der Kunde liest dabei die
`beschreibung` des Artikels. Ist sie leer, hat der Editor nur noch die
Stammdaten, aus denen er einen Ersatzsatz baut
(`react-pc-frontend/src/components/artikel/kundentext.ts`) -- und wo selbst das
nicht reicht, geht die Position ganz ohne Kundentext raus und der Bediener muss
ihn von Hand schreiben.

Das gilt genauso beim Aufsetzen einer neuen Instanz wie heute: erst Artikel
importieren, dann diesen Backfill fahren, dann den Material-Knopf freigeben.

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
| `alte_kurzbeschreibung` / `alte_beschreibung` | Was aktuell am Artikel steht. Bei `schon gepflegt` sieht man so, OB da echter Text steht oder nur ein Rest wie `<p></p>`. Werte ueber 300 Zeichen werden erkennbar gekuerzt (`... [gekuerzt]`). |
| `neue_kurzbeschreibung` | Innensicht, z.B. `Rundrohr 42.4x2 1.4301` |
| `neue_beschreibung` | Kundentext als HTML, z.B. `<p>Rundrohr 42,4 x 2 mm aus Edelstahl 1.4301</p>` |

Diese Datei durchsehen, bevor es weitergeht. Anderer Ablageort:
`--protokoll pfad/zur/datei.csv`.

## Schreiben

Erst nach Durchsicht der CSV -- und der **erste** scharfe Lauf klein:

    python scripts/artikel_dokumenttexte_backfill.py --url http://localhost:8080 --benutzer meinname --apply --limit 5

`--limit N` hoert nach N tatsaechlich verarbeiteten Artikeln auf. Uebersprungene
zaehlen nicht mit -- sonst waere das Limit womoeglich nach fuenf laengst
gepflegten Artikeln aufgebraucht, ohne dass ein einziger Schreibvorgang
stattgefunden haette. Genau der ist aber der Punkt: Der Schreibweg (Login,
CSRF-Token, PATCH) laeuft beim ersten scharfen Aufruf zum allerersten Mal.
Faellt dabei etwas anders aus als erwartet, sind das fuenf Fehlermeldungen
statt knapp zehntausend.

Die fuenf Artikel danach im ERP anschauen. Passt es, den Rest ohne `--limit`
nachziehen:

    python scripts/artikel_dokumenttexte_backfill.py --url http://localhost:8080 --benutzer meinname --apply

Das ist gefahrlos wiederholbar: Artikel, die schon einen Text haben, werden
uebersprungen. Ein mit `--limit` abgebrochener Lauf sagt das am Ende auch
ausdruecklich, damit er nicht fuer einen vollstaendigen gehalten wird.

## Wie die Texte entstehen

- Kundentext-Regelfall (Halbzeug mit Profilform und Abmessung):
  `Rundrohr 42,4 x 2 mm aus Edelstahl 1.4301, nahtlos`. Werkstoffcodes
  werden in Handwerker-Sprache uebersetzt - angelehnt an die Anzeigenamen
  aus Migration V337, aber fuers Kundendokument vereinfacht (Zusaetze wie
  `(hochfest)` gekuerzt); unbekannte Codes behalten den technischen Namen.
  Normkuerzel wie `EN 10219-2` bleiben weg. `nahtlos`, `kaltgezogen` und
  `geschliffen` stehen dabei, weil sie sonst gleiche Positionen ununterscheidbar
  machen wuerden - alle uebrigen Verfahren sind der Normalfall und entfallen.
- Rueckfallebene (z.B. Beschlaege): Der Produktname traegt den Text, sofern er
  mit einem echten Wort beginnt (`Tuerschliesser TS 3000`), ergaenzt um
  Werkstoff und Abmessung, falls vorhanden - aber nur, wenn der Werkstoff nicht
  schon im Produktnamen steht (`Allzweckduebel Kunststoff` bekommt kein
  zweites "aus Kunststoff").
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
