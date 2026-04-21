# Feature B: Gleitender Durchschnittspreis pro Artikel

**Zweck:** Pro `Artikel` wird mitlaufend ein **gewichteter Durchschnittspreis**
gefuehrt, der auf tatsaechlich bezahlten Rechnungspreisen basiert. Wird in
Kalkulation/Angebot spaeter als Preisvorschlag genutzt — weniger Rate-Arbeit
fuer den Handwerker, realistische Margen.

**Branch:** `feature/en1090-echeck` (aktuell, wird mit EN-1090 + Preisanfrage
gemeinsam nach `main` gemergt).
**Voraussetzungen:** Feature A (Preisanfrage) abgeschlossen. Gesamt-Testsuite
gruen (Stand 2026-04-21: 1268/0/0).

---

## Regeln fuer den naechsten Agent

1. Erst diesen Tracker lesen, dann Original-Kontext in
   [preisanfrage-fortschritt.md](preisanfrage-fortschritt.md) Abschnitt
   "Folge-Features".
2. Jede erledigte Aufgabe abhaken `[x]` mit Commit-Hash.
3. Neue Erkenntnisse / Abweichungen unten dokumentieren.
4. Nach jeder Etappe `./mvnw.cmd test 2>&1 | tail -20` — muss gruen sein.
5. Nach jeder Etappe `/review-and-ship` laufen lassen.

---

## Geklaerte Entscheidungen (nicht nochmal fragen)

| Entscheidung | Wahl | Wann |
|---|---|---|
| Trigger | **`ArtikelMatchingToolService.updateArtikelPreis(...)`** — Hook direkt nach erfolgreichem Preis-Update aus Rechnungen. KEIN Trigger bei `ArtikelInProjekt.bestellt=true`, weil das nur geplante Bestellungen sind, nicht bezahlte Rechnungen. | 2026-04-21 |
| Rechnungsart | **Gewichteter Durchschnitt** ueber Menge (kg) | 2026-04-21 |
| Gewichtungsgroesse | **kg**, weil der Agent den Preis bereits auf €/kg normalisiert (konsistent). Der Agent bekommt einen neuen Tool-Parameter `mengeKg` und normalisiert die Menge genauso wie er den Preis normalisiert. | 2026-04-21 |
| Backfill | **Ja, via Admin-Endpoint.** `POST /api/admin/artikel/durchschnittspreis/backfill`. Iteriert ueber alle `LieferantenArtikelPreise` mit Preis und setzt fuer jeden Artikel einen initialen Durchschnitt. Ohne historische Mengen faellt der Backfill auf einen ungewichteten Durchschnitt der aktuellen Lieferantenpreise zurueck (pragmatisch, Historie fehlt). | 2026-04-21 |
| REST-Verb Admin-Backfill | **POST** (Aktion/Batch-Job ausloesen), nicht PUT (= Ressource ersetzen). | 2026-04-21 |
| Admin-Endpoints-Doku | Neue zentrale Datei `docs/ADMIN_ENDPOINTS.md` — sammelt alle Admin-only-Endpoints. | 2026-04-21 |
| Flyway-Version | V228 (letzte ist V227) | 2026-04-21 |
| Schema-Felder | `durchschnittspreis_netto DECIMAL(12,4)`, `durchschnittspreis_menge DECIMAL(18,3)`, `durchschnittspreis_aktualisiert_am DATETIME(6)` | 2026-04-21 |

---

## Mathematik des gewichteten Durchschnitts

Gegeben:
- alter Schnitt `p_alt` (€/kg), alte kumulierte Menge `m_alt` (kg)
- neuer Datenpunkt: `p_neu` (€/kg), `m_neu` (kg)

Neuer Schnitt:
```
p_neu_ges = (p_alt * m_alt + p_neu * m_neu) / (m_alt + m_neu)
m_neu_ges = m_alt + m_neu
```

Erst-Befuellung (`m_alt = 0` oder `null`): `p_neu_ges = p_neu`, `m_neu_ges = m_neu`.

Schutz: `m_neu <= 0` oder `p_neu <= 0` → Update ueberspringen, Log-Warning.

---

## Etappen

Legende: `[ ]` offen · `[~]` in Arbeit · `[x]` erledigt · `[!]` blockiert

### Etappe B1: Schema + Entity + Kern-Service ✅
**Commit-Ziel:** `feat(artikel): Durchschnittspreis-Schema + Service (V228)`

- [x] V228 Migration mit 3 neuen Spalten an `artikel` (`durchschnittspreis_netto`, `durchschnittspreis_menge`, `durchschnittspreis_aktualisiert_am` + Index)
- [x] `Artikel` Entity um 3 Felder erweitert
- [x] `ArtikelDurchschnittspreisService.aktualisiere(artikel, mengeKg, preisProKg)` — gewichtet, null-safe, Schutz gegen negative/null Werte
- [x] `ArtikelDurchschnittspreisService.backfillAlle()` — einfacher Mittelwert als Start-Befuellung (Historie/Mengen fehlen)
- [x] Unit-Tests: 14 Tests (9 Aktualisiere + 5 Backfill), alle Rand- und Fehlerfaelle
- [x] Compile gruen, Tests gruen (14/14)
- [ ] Commit — ausstehend, User-Freigabe

### Etappe B2: Integration in Matching-Agent
**Commit-Ziel:** `feat(artikel-matching): Menge erfassen + Durchschnittspreis-Hook`

- [ ] Tool `update_artikel_preis` um Parameter `mengeKg` (NUMBER, required)
- [ ] `SYSTEM_PROMPT` erweitert: Agent normalisiert Menge auf kg wie den Preis
- [ ] Nach erfolgreichem `updateArtikelPreis(...)` den Durchschnittspreis-Service rufen
- [ ] Tool-Test `ArtikelMatchingToolServiceTest` erweitert (Durchschnitt wird gesetzt)
- [ ] Agent-Test `ArtikelMatchingAgentServiceTest` — mockt Tool, prueft dass mengeKg durchgereicht wird
- [ ] `./mvnw.cmd test` gruen
- [ ] Commit

### Etappe B3: Backfill + Admin-Endpoint + Doku
**Commit-Ziel:** `feat(admin): Durchschnittspreis-Backfill Endpoint + Doku`

- [ ] `ArtikelDurchschnittspreisService.backfillAlle()` — einfacher Durchschnitt aller Lieferantenpreise pro Artikel (Historie fehlt, Menge unbekannt → unwuchted). Gibt `BackfillErgebnis(anzahlArtikel, uebersprungen)` zurueck.
- [ ] `AdminArtikelController` mit `POST /api/admin/artikel/durchschnittspreis/backfill`
- [ ] Zugriffsschutz via bestehender `SecurityConfig` — gleicher Schutz wie alle `/api/**`
- [ ] `docs/ADMIN_ENDPOINTS.md` angelegt und den neuen Endpoint dokumentiert
- [ ] Controller-Test (Happy-Path, 200 mit Ergebnis)
- [ ] Service-Test Backfill (Artikel ohne Preise, Artikel mit 1 Lieferanten, Artikel mit N Lieferanten)
- [ ] `./mvnw.cmd test` gruen
- [ ] Commit

### Parallel-Etappe P1: Preis-Historie + zentraler Hook-Service ✅
**Commit-Ziel:** `feat(artikel): Preis-Historie + zentraler Hook-Service (V229)`
**Quelle:** Ausführung gemäß [feature-b-historie-parallel.md](feature-b-historie-parallel.md).

Ergänzend zu B1 wurde parallel eine **Preis-Historie** und ein **zentraler Hook-Service** gebaut, der an allen 7 eigenverantwortlichen Preis-Setz-Stellen (die 8. macht B2 im Matching-Agent) aufgerufen wird und sowohl die Historie schreibt als auch — bei Rechnungs-Quellen mit KG-Einheit und Menge > 0 — den Durchschnittspreis-Service triggert.

**Neu angelegt:**
- [x] `src/main/resources/db/migration/V229__artikel_preis_historie.sql` — Tabelle `artikel_preis_historie` mit `preis`, `menge`, `einheit`, `quelle`, FKs auf `artikel` (CASCADE) und `lieferanten` (SET NULL), 3 Indizes (`artikel_id+erfasst_am`, `quelle`, `einheit`).
- [x] `domain/PreisQuelle.java` — Enum (`RECHNUNG`, `ANGEBOT`, `KATALOG`, `MANUELL`, `VORSCHLAG`).
- [x] `domain/ArtikelPreisHistorie.java` — JPA-Entity (Lombok `@Getter/@Setter`) mit `preis` (12,4), `menge` (18,3 nullable), `einheit` (Verrechnungseinheit, NOT NULL), `quelle`, `erfasstAm`, optionalem `externeNummer`/`belegReferenz`/`bemerkung`.
- [x] `repository/ArtikelPreisHistorieRepository.java` — `findByArtikel_IdOrderByErfasstAmDesc`, `findByArtikel_IdAndQuelleOrderByErfasstAmDesc`.
- [x] `service/ArtikelPreisHookService.java` — `registriere(artikel, lieferant, preis, menge, einheit, quelle, externeNummer, belegReferenz, bemerkung)` plus Convenience-Overload (ohne menge/beleg/bemerkung). `@Transactional(Propagation.REQUIRED)`, best-effort (try/catch um Historie- und Durchschnitts-Update getrennt). Durchschnitts-Trigger nur wenn `quelle == RECHNUNG && einheit == KILOGRAMM && menge > 0`.
- [x] `ArtikelPreisHookServiceTest.java` — **16 Tests grün**, inkl. Einheiten-Varianten (KG/STUECK/METER), Null-/Negativ-Guards, Fehler-Isolation zwischen Historie und Durchschnitt, Convenience-Overload.

**Hook verdrahtet an 7 Stellen (in dieser Session umgesetzt):**
- [x] `OfferPriceService` — Quelle `ANGEBOT` nach erfolgreichem Preis-Update (setzt `savedFlag`).
- [x] `ArtikelImportService` — Quelle `KATALOG` nach `artikelRepository.save(currentArtikel)`.
- [x] `LieferantArtikelpreisService` — Quelle `MANUELL` in `aktualisiere(...)` UND `anlegen(...)`.
- [x] `ArtikelService.erstelleArtikel` — Quelle `MANUELL`, nur wenn `dto.getPreis() != null`.
- [x] `ArtikelVorschlagController.approve` — Quelle `VORSCHLAG`, nur wenn `v.getEinzelpreis() != null`.
- [x] `GeminiDokumentAnalyseService` JSON-Pfad — Quelle `RECHNUNG` mit Mengen-Extraktion via neuer Helper `extrahiereMengeKgAusJson(pos, artikel)`.
- [x] `GeminiDokumentAnalyseService` ZUGFeRD-Pfad — Quelle `RECHNUNG` mit `normalizeMengeZuKg(menge, einheit)`.

**Neue Hilfsmethoden in `GeminiDokumentAnalyseService`:**
- `einheitVonArtikel(Artikel)` — liest `artikel.getVerrechnungseinheit()`, Fallback KILOGRAMM.
- `normalizeMengeZuKg(menge, einheitRaw)` — multiplikatives Gegenstück zu `normalizePreisZuKg` (t → ×1000, 100kg → ×100, kg → ×1, g → ×0.001, unbekannt → `null`).
- `extrahiereMengeKgAusJson(JsonNode, Artikel)` — nur für KG-Artikel aktiv, parst `menge`/`mengeneinheit` aus der Rechnungsposition.

**Bestehende Tests angepasst (neue Hook-Dependency):**
- [x] `ArtikelImportServiceTest` — `@Mock ArtikelPreisHookService` in Setup + Konstruktor.
- [x] `OfferPriceServiceTest` — innere `@TestConfiguration HookMockConfig` für `@DataJpaTest`-Autowiring + 5 direkte `new OfferPriceService(...)`-Aufrufe auf 3-Parameter-Konstruktor umgestellt.
- [x] `GeminiDokumentAnalyseServiceTest` — `@Mock ArtikelPreisHookService` im Setup + Konstruktor.
- [x] `ArtikelVorschlagControllerTest` — `@MockBean ArtikelPreisHookService` ergänzt.

**Schema-Refactor gegenüber ursprünglichem Parallel-Plan:**
Der ursprüngliche Entwurf hatte `preis_pro_kg` + `menge_kg` fest vorgegeben. User-Feedback war aber: _„manchmal ist bei Stahlrohren Preis pro Meter, bei Trägern Preis pro kg, bei Schrauben Preis pro VE usw."_ — deshalb wurde das Schema auf generische `preis`/`menge` + **Pflichtspalte `einheit`** (VARCHAR(32), JPA-Enum `Verrechnungseinheit`) umgestellt. Der Durchschnittspreis-Service wird weiterhin nur bei KG-Artikeln gefüttert, damit €/kg-Semantik nicht durch andere Einheiten „vergiftet" wird. Historie bleibt für alle Einheiten vollständig.

**Validierung:**
- [x] `./mvnw.cmd test 2>&1 | tail -40` — **1298/0/0** (BUILD SUCCESS, 1:10 min).
- [ ] Commit — ausstehend, User-Freigabe.

### Etappe B4 (optional, separat entscheiden): UI-Anzeige
**Commit-Ziel:** `feat(artikel-editor): Durchschnittspreis anzeigen`

- [ ] `ArtikelDto` um Durchschnittspreis-Felder erweitern
- [ ] `ArtikelEditor.tsx` Info-Zeile "Durchschnittspreis: X,XX €/kg (aus Y kg Historie)"
- [ ] Vitest
- [ ] `npm run lint` + `npm run build` gruen
- [ ] Commit

**Hinweis:** B4 ist optional. User-seitig entscheiden, ob jetzt oder spaeter.

---

## Abweichungen vom urspruenglichen Gedanken

### Trigger nicht bei `ArtikelInProjekt.bestellt=true`
Der urspruengliche Tracker-Eintrag schlug vor, beim Setzen von `bestellt=true`
+ `preisProStueck` den Durchschnitt zu aktualisieren. **User-Entscheidung
2026-04-21:** Besserer Trigger ist `updateArtikelPreis` im Matching-Agent,
weil das **tatsaechlich bezahlte Rechnungspreise** sind, nicht nur geplante
Bestellungen. Der Agent filtert zudem schon Dienstleistungen/Fracht/Verpackung
(SKIPPED-Regel im `SYSTEM_PROMPT`), sodass keine Nebenkosten in den
Artikelstamm durchrutschen.

---

## Session-Log

| Datum | Agent | Was passiert |
|---|---|---|
| 2026-04-21 | Opus 4.7 | Plan-Datei + Admin-Doku angelegt. Trigger-Entscheidung (Matching-Agent statt Bestellung). Etappe B1 wird als naechstes umgesetzt. |
| 2026-04-21 | Opus 4.7 (Parallel-Agent) | Parallel-Etappe P1 abgeschlossen: V229-Migration (generisch `preis`/`menge`/`einheit` statt ursprünglich geplantem `preis_pro_kg`), `PreisQuelle`-Enum, `ArtikelPreisHistorie`-Entity + Repo, `ArtikelPreisHookService` (16 Unit-Tests grün), Hook an 7 Stellen verdrahtet (OfferPrice / ArtikelImport / LieferantArtikelpreis ×2 / ArtikelService / ArtikelVorschlagController / GeminiDokumentAnalyse ×2). Schema-Refactor nach User-Feedback zu Multi-Einheiten. 4 Tests angepasst, gesamte Suite 1298/0/0. Commit steht aus. |
