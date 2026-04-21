# Übergabe A2 — `ArtikelInProjekt` als reine Kalkulationsposition

## 🎯 Auftrag für die nächste KI-Session

Du übernimmst einen laufenden Refactor auf Branch **`feature/en1090-echeck`**. Stufe A1
ist fertig committed-ready (Tests grün), jetzt steht **Stufe A2** an: `ArtikelInProjekt`
(AiP) wird auf seine eigentliche Rolle reduziert = **nur noch Kalkulationsposition im Projekt**.
Alles, was mit dem Bestellvorgang, der Fertigung oder der EN-1090-Zeugnisauswahl zu tun hat,
wandert auf die frisch erzeugten Aggregate **`Bestellung`** / **`Bestellposition`** (siehe A1).

Lies zuerst `.claude/CLAUDE.md` — die Regeln dort gelten unverändert (echte Umlaute, Handwerker-Sprache,
DSGVO, keine Secrets, DRY, Review-and-Ship am Ende). Lies auch den Commit
`feat(en1090): Einheitlicher EN-1090-Check statt EXC-adaptiv (#51)` — der gibt dir den
aktuellen Status der EN-1090-Umgebung, in die wir uns einfügen.

## 📦 Ausgangspunkt (Stand A1, schon im Baum, nicht committet)

- Migration **V236** legt `bestellung` + `bestellposition` an und backfillt aus `artikel_in_projekt WHERE bestellt=TRUE`.
- Entities: [Bestellung.java](src/main/java/org/example/kalkulationsprogramm/domain/Bestellung.java),
  [Bestellposition.java](src/main/java/org/example/kalkulationsprogramm/domain/Bestellposition.java),
  [BestellStatus.java](src/main/java/org/example/kalkulationsprogramm/domain/BestellStatus.java).
- Repos: [BestellungRepository.java](src/main/java/org/example/kalkulationsprogramm/repository/BestellungRepository.java),
  [BestellpositionRepository.java](src/main/java/org/example/kalkulationsprogramm/repository/BestellpositionRepository.java).
- Service: [BestellauftragService.java](src/main/java/org/example/kalkulationsprogramm/service/BestellauftragService.java)
  (erzeugt beim Export eine Bestellung pro (Lieferant, Projekt)-Gruppe, Status `VERSENDET`).
- Aufgerufen in [BestellungService.java:markiereLieferantAlsExportiert](src/main/java/org/example/kalkulationsprogramm/service/BestellungService.java)
  parallel zum Alt-Flow.
- 1386/1386 Backend-Tests grün (inkl. 18 neuer Tests für `BestellauftragService`).

**Wichtig:** Der Ist-Zustand ist **parallel**. Das Frontend und `findeOffeneBestellungen`
lesen weiterhin aus AiP. Dein Job ist, diese Parallelität aufzulösen.

## 🧭 Zielbild A2

### Backend

`ArtikelInProjekt` behält genau diese Felder:

| Bleibt | Begründung |
|---|---|
| `projekt`, `artikel` (nullable), `kategorie` | Kalkulation |
| `stueckzahl`, `meter`, `kilogramm`, `preisProStueck` | Kalkulation |
| `hinzugefuegtAm`, `kommentar` | Historie / Notiz |
| `freitextProduktname`, `freitextProdukttext`, `freitextEinheit`, `freitextMenge` | manuelle Positionen |

Diese Felder **entfallen** (ziehen auf Bestellung/Bestellposition um, sind dort bereits
vorhanden):

| Feld auf AiP | neuer Ort |
|---|---|
| `bestellt` (boolean) | aus `Bestellung.status` ableiten |
| `bestelltAm` | `Bestellung.bestelltAm` |
| `exportiertAm` | `Bestellung.exportiertAm` |
| `lieferant` (ManyToOne) | `Bestellung.lieferant` |
| `lieferantenArtikelPreis` (ManyToOne) | nur noch beim Bestellen gelesen, nicht persistiert |
| `schnittForm`, `anschnittWinkelLinks/Rechts`, `fixmassMm` | `Bestellposition.*` |
| `zeugnisAnforderung` (ZeugnisTyp) | `Bestellposition.zeugnisAnforderung` |

Die Kalkulations-Felder `schnittForm/anschnittWinkel/fixmass` sind in der Praxis **erst
beim Bestellen** relevant — deshalb wandern sie mit. Beim Anlegen einer Bestellposition aus
einer AiP werden sie initial aus der Kalkulation übernommen (das tut `BestellauftragService`
heute schon). Im Kalkulationsdialog werden sie nach A2 **nicht mehr angezeigt**.

### Flow-Umbau

`findeOffeneBestellungen()` liest nach A2 **aus `Bestellung` mit `status = ENTWURF`**, nicht
mehr aus AiP. Neuer Flow:

1. User klickt im Kalkulationsdialog "Bestellen" → Backend erzeugt eine `Bestellung` mit
   `status = ENTWURF` und eine `Bestellposition` pro ausgewählter AiP. Die AiP selbst bleibt
   unverändert (keine Flag-Mutation mehr).
2. Bestellübersicht listet ENTWURF-Bestellungen, gruppiert nach Lieferant.
3. Export (PDF/Mail) setzt `status = VERSENDET` + `exportiertAm` auf der existierenden
   Bestellung (nicht mehr "neu anlegen beim Export" wie in A1 — A1 war die Brücke).
4. `setBestellt(id, true)` fällt weg. Die Bestellübersicht zeigt stattdessen ENTWURF/VERSENDET.

### Migration V237

- `ALTER TABLE artikel_in_projekt DROP COLUMN bestellt, bestellt_am, exportiert_am, lieferant_id, schnitt_form, anschnitt_winkel_links, anschnitt_winkel_rechts, fixmass_mm, zeugnis_anforderung;`
- Die `LieferantenArtikelPreise`-Join-Konstruktion auf AiP fällt mit weg (`@JoinColumns` mit `insertable=false, updatable=false` ist ohne `lieferant_id` leer).

### Frontend

Betroffene Dateien (vorab mit `grep` ermittelt):
- [BestellungEditor.tsx](react-pc-frontend/src/pages/BestellungEditor.tsx) — ~25+ Stellen, Bestellübersicht komplett umbauen
- [ProjektEditor.tsx](react-pc-frontend/src/pages/ProjektEditor.tsx) — Kalkulationsdialog, Zeugnis-/Schnittform-Felder entfernen
- [MaterialbestellungModal.tsx](react-pc-frontend/src/components/MaterialbestellungModal.tsx)
- [ArtikelSearchModal.tsx](react-pc-frontend/src/components/ArtikelSearchModal.tsx)

Die DTO-Typen `bestellt`, `exportiertAm`, `schnittForm`, etc. verschwinden aus
`ArtikelInProjektResponseDto`; neue Bestell-DTOs werden aus `Bestellung`/`Bestellposition`
abgeleitet. Folge der bestehenden DTO-Namenskonvention (`*ResponseDto`, Mapper unter
`src/main/java/.../mapper/`).

## 📋 Konkrete Schritte

1. **Explorer-Runde** (Agent: Explore): Liste alle Stellen, an denen die zu entfernenden
   AiP-Felder gelesen/geschrieben werden. Erwartete Hotspots:
   - Services: `BestellungService`, `OfferPriceService`, `ProjektManagementService`,
     `HicadImportService`, `PreisanfrageService`.
   - Controller: `BestellungController`, `BestellungsUebersichtController`.
   - Mapper: `ProjektMapper` (ArtikelInProjekt-Mapping), `PreisanfrageMapper`.
   - Repository-Methoden: `ArtikelInProjektRepository.findByBestelltFalseOrder…`.
   - Tests: `BestellungServiceTest`, `BestellungServiceMappingTest`, `ProjektMapperTest`,
     `PreisanfrageServiceTest`, `OfferPriceServiceTest`, `HicadImportServiceTest` (falls vorhanden).

2. **Backend umbauen** in dieser Reihenfolge:
   1. Neue DTOs: `BestellungUebersichtDto` / `BestellpositionUebersichtDto` — bilden die
      künftige Bestellübersicht ab.
   2. `BestellungService.findeOffeneBestellungen()` → liest `BestellungRepository
      .findByStatusOrderByErstelltAmDesc(BestellStatus.ENTWURF)`, mappt nach DTO.
   3. Neuer Endpoint `POST /api/bestellungen/aus-kalkulation` (bzw. passender Name) →
      erzeugt ENTWURF-Bestellung aus ausgewählten AiP-IDs; nutzt `BestellauftragService`,
      aber setzt Status `ENTWURF` (Parameter einbauen).
   4. `BestellungService.setBestellt()` und `markiereLieferantAlsExportiert()` entfallen in
      der AiP-Variante; Export arbeitet auf bestehender Bestellung.
   5. Entities: zu entfernende Felder aus `ArtikelInProjekt` raus — inklusive der
      `@JoinColumns`-Verknüpfung für `lieferantenArtikelPreis`.
   6. Migration V237 schreiben (Flyway).

3. **Frontend umbauen**:
   - Bestellübersicht liest neuen Endpoint, zeigt Bestellungen pro Lieferant.
   - Kalkulationsdialog: Felder Schnittform / Anschnittwinkel / Fixmaß / Zeugnis-Anforderung
     entfernen — diese werden nach A2 erst beim Bestellen erfasst (oder später in einem
     Bestellposition-Dialog; in A2 reicht die Übernahme der Kalkulationswerte, wo vorhanden).
   - Neue Bestell-Erstellung: statt AiP per Checkbox "bestellt" → Button "Bestellung anlegen"
     mit Mehrfachauswahl, ruft den neuen Endpoint.

4. **Tests anpassen / ergänzen**:
   - Bestehende Tests grün halten (aktuell 1386).
   - Neue Tests:
     - `BestellungServiceTest.findeOffeneBestellungen_liestEntwurfBestellungen`
     - `BestellungControllerTest.postAusKalkulation_erzeugtEntwurf`
     - Security-Tests für neuen Endpoint (siehe `.claude/skills/feature` Punkt 6).
   - Alte Tests, die `setBestellt` aufrufen, entweder umschreiben oder löschen.

5. **Qualität**:
   - `./mvnw.cmd test 2>&1 | tail -60` → 100% grün.
   - `cd react-pc-frontend && npm run build` → grün.
   - `git diff --staged` → keine Secrets, keine echten Personendaten, keine `application-local.properties`.

## 🚧 Randbedingungen & Fallen

- **Umlaute in UI-Texten sind Pflicht.** `Stück`, `Zurückgesandt`, `Überschuss`, nie `Stueck` etc.
- **Handwerker-Sprache.** In Frontend-Texten nie "Bestellposition", sondern "Bestellzeile" oder "Position". Nie "Lieferant-Datensatz", sondern "Händler" / "Lieferant" direkt.
- **Keine vorzeitige Abstraktion.** Wenn eine Komponente dreimal ähnlich aussieht, darf sie extrahiert werden — aber vorher `CLAUDE.md` Regel 1 beachten: **Frag den User, bevor du neue Shared-Komponenten/Hooks/Services anlegst.**
- **DSGVO.** In neuen Tests nur `Max Mustermann`, `test@example.com`.
- **Feature-Branch.** Alle Commits auf `feature/en1090-echeck`. Kein Force-Push, keine
  Rebases auf main ohne Rückfrage.
- **EN-1090-Fragen** nur über den NotebookLM-MCP (`917b7857-0d32-43bf-9dec-b5ca1d362800`)
  stellen, falls normative Rückfragen auftauchen (siehe CLAUDE.md Abschnitt NotebookLM).

## ⛔ Explizit **nicht** anfassen

- `Werkstoffzeugnis`-Modell und `En1090AnforderungenService`. Die bleiben, wie sie sind.
- `WpsFreigabe` und das WPS-Matching (aus #48/#49). Unberührt.
- **Keine** Umbenennung von `ArtikelInProjekt` in A2. Der User hatte das initial erwogen,
  dann aber entschieden: der Name bleibt. Nur die Semantik wird scharf gezogen.
- **Keine** Mobile-Anpassung (`react-zeiterfassung`) — AiP wird dort nicht verwendet.
- **Keine** Wareneingangskontrolle (#29) — das ist Commit B und kommt erst nach A2.

## ✅ Abnahmekriterien

- [ ] `ArtikelInProjekt` besitzt nur noch Kalkulations-/Freitextfelder (siehe Tabelle oben).
- [ ] Flyway-Migration V237 droppt alte Columns und läuft idempotent.
- [ ] `BestellungService.findeOffeneBestellungen()` liest aus `Bestellung` mit Status `ENTWURF`.
- [ ] Neuer Flow: AiP auswählen → ENTWURF-Bestellung → Export → VERSENDET.
- [ ] Frontend zeigt den neuen Flow, keine Tote-Felder-Rückstände im UI.
- [ ] 100% der Backend-Tests grün, `npm run build` des PC-Frontends grün.
- [ ] Commit-Message im Stil der letzten EN-1090-Commits:
  `feat(en1090): AiP als reine Kalkulationsposition, Bestellwesen eigenständig (M<nr>, #<issue-falls-vorhanden>)`
- [ ] Am Ende: `.claude/commands/review-and-ship.md` ausführen.

## ➡️ Danach: Commit B (#29 Wareneingangskontrolle)

Nach erfolgreichem A2 folgt **Commit B** = Issue #29 (Wareneingangspruefung / 2000-PP).
Der Plan dafür steht bereits in der Chat-Historie, setzt aber auf das aufgeräumte
Datenmodell von A2 auf. **Nicht vorziehen.**
