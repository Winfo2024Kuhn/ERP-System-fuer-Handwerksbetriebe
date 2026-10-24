# Verrechnungslohn-Rechner – Plan (umgesetzt)

Stand: Oktober 2026 (ursprünglicher Plan vom 2026-05-08)
Branch: main
Status: **Umgesetzt.** Bauabschnitte 1–3 inklusive „Übernehmen“ mit Aufschlag pro Abteilung sind gebaut und getestet. Dieses Dokument beschreibt den Ist-Stand; Abweichungen vom ursprünglichen Plan stehen am Ende.

## Ziel

Auf der Seite **Arbeitsgänge** (Menü **Vorlagen & Stammdaten → Administration → Arbeitsgänge**, Seitenkopf „Arbeitsplanung“) gibt es oben rechts – links neben „Aktualisieren“ – den Knopf:

> "Was muss meine Stunde kosten?"

Der öffnet ein Fenster, in dem der Chef sieht:

- was er an Löhnen zahlt
- was die Firma sonst noch kostet (Gemeinkosten)
- wie viele Stunden er im Jahr wirklich verkaufen kann
- daraus ergibt sich automatisch der Mindest-Verrechnungslohn (Selbstkosten)
- per Schieberegler kann er einen Gewinn-Aufschlag setzen
- pro Abteilung kann er den Wert nach oben/unten korrigieren
- ein Knopf überträgt den Satz auf alle Arbeitsgänge des gewählten Jahres

Sprache: Handwerker-freundlich, kein Buchhalter-Jargon.

## Daten, die wir brauchen – und woher sie kommen

| Was | Quelle |
|-----|--------|
| Bruttolöhne (abgeschlossenes Jahr) | `Lohnabrechnung.bruttolohn` (Summe des Jahres) |
| Stundenlöhne (zeitlich gestaffelt, für Schätzung und fehlende Abrechnungen) | `MitarbeiterStundenlohn` (gueltigAb), sonst `Mitarbeiter.stundenlohn` |
| AG-Anteile SV (KV/PV/RV/AV, U1, U2, Insolvenzgeldumlage, Minijob-Sätze) | `SvSatz` (gültig am 1. Januar des Jahres) + `Mitarbeiter.beschaeftigungsart` |
| Zusatzbeitrag Krankenkasse | `Krankenkasse.zusatzbeitragProzent` (AG-Hälfte) |
| BG-Beitrag (Unfallversicherung) | `Firmeninformation.bgSatzOverride`, sonst `Gewerk.bgSatzProzent` des Betriebs |
| Kalkulatorischer Unternehmerlohn (GF) | `Mitarbeiter.kalkulatorischerLohnMonat` |
| Geldwerte Vorteile (Auto/Telefon GF) | `Mitarbeiter.geldwertVorteilMonat` |
| Soll-Stunden | `ZeitkontoVersion` (je Tag die gültige Version) |
| Urlaub | `Abwesenheit` (typ=URLAUB), sonst `Mitarbeiter.jahresUrlaub` als Default |
| Krankheit | `Abwesenheit` (typ=KRANKHEIT, ohne Krankengeld-/Wiedereingliederungsphasen), sonst Default |
| Langzeitkrank (Krankengeld, Wiedereingliederung) | Phasen der `Langzeitkrankmeldung` – diese Tage werden herausgerechnet |
| Feiertage | `Feiertag` (Bundesland fest „BY“) |
| Produktive / interne Stunden | `Zeitbuchung` (typ=ARBEIT) × `Projekt.projektArt` (PAUSCHAL/REGIE produktiv, INTERN/GARANTIE intern) |
| Gemeinkosten | `LieferantDokumentProjektAnteil`, validierte Belege und Beleg-Aufteilungen auf Kostenstellen mit `istFixkosten = true` |

## Wie der Rechner rechnet

Berücksichtigt werden alle **heute aktiven** Mitarbeiter vom Typ Mensch (auch beim Rechnen eines Vorjahres).

### Lohn-Block (Jahres-Kosten)

Für jeden aktiven Mitarbeiter:

- **Normal** (kein GF): Jahresbrutto (siehe Modi) + AG-Anteile SV + BG-Beitrag
  - Minijob: Minijob-AG-Sätze (KV, RV, Pauschalsteuer) + U1 + U2 + Insolvenzgeldumlage
  - Sonst: halbe KV/PV/RV/AV-Sätze + halber KK-Zusatzbeitrag + U1 + U2 + Insolvenzgeldumlage
- **Geschäftsführer** (`istGeschaeftsfuehrer = true`): `kalkulatorischerLohnMonat × 12 + geldwertVorteilMonat × 12`
  - Beschäftigungsart `GF_SV_FREI`: KEINE Sozialversicherung, KEINE BG
  - Beschäftigungsart `GF_SV_PFLICHTIG`: AG-SV und BG werden auf Wunschlohn + Vorteil aufgeschlagen
- Tage mit Krankengeld oder Wiedereingliederung (Langzeitkrankmeldung) senken die Kosten anteilig (Kalendertage-Faktor) – außer das Brutto stammt aus echten Lohnabrechnungen, dort steckt der Ausfall schon drin.

→ Summe = "Was kosten mich meine Leute pro Jahr?"

### Verkäufliche-Stunden-Block

Angezeigt werden pro Mitarbeiter (egal ob GF oder nicht): Soll, Urlaub, Krank, Krankengeld/Wiedereingliederung (Tage), Intern, Verkäuflich.

- **Soll:** je Tag die Stunden der gültigen Zeitkonto-Version; ganze Feiertage an Werktagen zählen nicht. Tage ohne Zeitkonto-Version werden mit 8 h pro Werktag gerechnet (Hinweis „Keine Arbeitszeiten hinterlegt …“).
- **Urlaub:** gebuchte Urlaubsstunden; sind keine gebucht, `jahresUrlaub × Stunden pro Tag` (als Default markiert).
- **Krank:** gebuchte Krankheitsstunden; sind keine gebucht (und keine Langzeitkrank-Tage), 8 Tage × Stunden pro Tag (Default).
- **Verkäuflich:** hängt vom Modus ab (siehe unten).

Geschäftsführer: Bucht er produktiv auf reguläre Projekte, zählt das 1:1. Seine Chef-Arbeit bucht er auf interne Projekte – die fällt automatisch raus.

Mitarbeiter ohne Zeitbuchungen (z.B. reine Bürokraft): im abgeschlossenen Jahr 0 verkäufliche Stunden – sein Lohn erhöht den Verrechnungssatz korrekt.

→ Summe = "Wie viele Stunden kann ich im Jahr verkaufen?"

### Gemeinkosten-Block

Summe pro Kostenstelle (nur Kostenstellen mit `istFixkosten = true`) aus drei Quellen:

1. `LieferantDokumentProjektAnteil` mit `kostenstelle != null`, `isStreckungAktivFuerJahr(zielJahr)` → Summe der `getJahresanteil()`
2. Validierte Belege auf Fixkosten-Kostenstellen (ohne Umbuchungen, Privateinlagen/-entnahmen und bereits verknüpfte Lieferanten-Dokumente) – nur der betriebliche Netto-Anteil, ohne schon aufgeteilte Beträge
3. Aufteilungen von Belegen auf Kostenstellen (mit Streckung über mehrere Jahre)

Gestreckte Beträge werden im Fenster mit „gestreckt“ markiert. Es wird nichts hochgerechnet – es zählen die im Jahr gebuchten Werte.

→ Summe = "Was kostet die Firma sonst noch im Jahr?"

### Verrechnungslohn

```
Selbstkosten   = (Lohn + Gemeinkosten) ÷ Verkäufliche Stunden
Verkaufspreis  = Selbstkosten × (1 + Gewinn-%)
```

Pro Abteilung kann der User dann noch +/− € draufschlagen (z.B. Schweißerei teurer, Schlosserei günstiger).

## Zwei Modi: Rückwirkend (genau) vs. Laufendes Jahr (Schätzung)

Im Fenster wählt der User unter „Welches Jahr?“ das Vorjahr, das laufende oder das nächste Jahr. Daraus ergibt sich der Modus (Jahr vor dem aktuellen = rückwirkend, sonst Schätzung):

### Modus A – Rückwirkend (abgeschlossenes Jahr, z.B. 2025) – Anzeige „Echte Zahlen vom Vorjahr“

Hier wird mit **echten Ist-Daten** gerechnet:

- **Lohnsumme**: aus den Lohnabrechnungen des Jahres. Bei 1–11 Abrechnungen wird auf 12 Monate hochgerechnet, ohne Abrechnungen mit Stammstundenlohn × Jahres-Soll. GF mit kalkulatorischem Wert × 12.
- **Verkäufliche Stunden** pro Mitarbeiter:
  - SUMME aller Arbeits-Zeitbuchungen, deren Projekt **produktiv** ist (`projektArt = PAUSCHAL` oder `REGIE`).
  - Soll, Urlaub und Krank werden angezeigt, gehen aber NICHT in die Rechnung ein – es zählt, was wirklich gearbeitet wurde.
- **Interne / unproduktive Stunden**: SUMME der Zeitbuchungen auf `INTERN` / `GARANTIE` (nur zur Anzeige).
- **Gemeinkosten**: siehe oben, für genau dieses Jahr.

**Daten-Check:** Fehlen Daten, zeigt der Rechner oben einen Hinweis „Für 2025 fehlen Daten:“ mit einer Liste und dem Satz „Die Berechnung ist deshalb nur bedingt aussagekräftig.“ Geprüft wird:
- Keine Lohnabrechnungen im Jahr (dann Stammstundenlohn als Default)
- Nur X von 12 Lohnabrechnungen vorhanden (Brutto wird hochgerechnet)
- Keine Zeitbuchungen im Jahr (0 verkäufliche Stunden)
- Keine Arbeitszeiten hinterlegt für Teile des Jahres (8 h pro Werktag)

### Modus B – Laufendes Jahr (Hochrechnung, z.B. 2026) – Anzeige „Schätzung fürs laufende Jahr“

Hier rechnen wir das Jahr **hoch**:

- **Lohnsumme**: Stundenlohn (letzter bis 31.12. des Jahres gültiger Stand) × Jahres-Soll (aus den Zeitkonto-Versionen, sonst 40 h/Woche) + AG-SV + BG.
- **Verkäufliche Stunden** pro Mitarbeiter: `Soll − Urlaub − Krank − Interne Stunden` (nie unter 0).
  - Interne Stunden = Soll × Prozentwert aus dem Feld „Interne Stunden?“ (Default 5 %, Knopf „Neu rechnen“). Der Wert gilt für alle Mitarbeiter, auch für GF.
- **Gemeinkosten**: die bisher gebuchten Werte des Jahres (keine Hochrechnung auf 12 Monate).

## Bauabschnitte

### 1. Mitarbeiter-Editor (Geschäftsführer-Felder) ✅

**Backend:**
- Spalten `ist_geschaeftsfuehrer`, `kalkulatorischer_lohn_monat`, `geldwert_vorteil_monat` in `mitarbeiter`. Ursprünglich Migration `V300__mitarbeiter_geschaeftsfuehrer.sql` (MySQL); seit der Umstellung auf PostgreSQL im Basis-Schema `db/postgresql/basis/V1__basis_schema.sql` enthalten.
- `Mitarbeiter`-Domain, `MitarbeiterDto` + `MitarbeiterErstellenDto` erweitert
- `MitarbeiterService`: Ist GF angehakt, ist der Wunschlohn Pflicht (≥ 0), der Vorteil optional (≥ 0); ohne Haken werden beide Werte geleert.

**Frontend** (`MitarbeiterEditor`):
- Abschnitt „Geschäftsführer“ mit Checkbox „Ist diese Person Geschäftsführer/in?“ – Hinweis: „Dann nehmen wir statt einem echten Lohn den Wunschlohn unten für die Kalkulation des Stundensatzes.“
- Wenn angehakt: zwei Eingabefelder
  - "Was möchtest du dir pro Monat als Lohn rechnen? (€)"
  - "Auto/Telefon/Privatanteile pro Monat (€) – optional"
- Mehrere GF möglich, einfach mehrere Mitarbeiter mit Haken
- Geschäftsführer führen kein Arbeitszeitkonto und haben keinen Monatsabschluss; Arbeitszeit kann optional hinterlegt werden.

### 2. Backend Rechner-Service ✅

- `service/VerrechnungslohnService.java`
  - `berechne(int jahr, Integer interneQuoteProzent)` → liefert `VerrechnungslohnErgebnisDto`
  - intern: `berechneLohnZeile()`, `berechneStundenZeile()`, `berechneGemeinkosten()`, `uebernehmen()`
- `controller/VerrechnungslohnController.java`
  - `GET /api/verrechnungslohn?jahr=2026&internProzent=5` → Vorschau-Daten (`jahr` 2000–2100, `internProzent` optional 0–100)
  - `POST /api/verrechnungslohn/uebernehmen` (nur Admins) – Body `{ jahr, basisSatz, abteilungAufschlaege: [{ abteilungId, aufschlagEuro }] }` → setzt `ArbeitsgangStundensatz` für **alle** Arbeitsgänge des Jahres (Basissatz + Aufschlag der Abteilung). Ergibt ein Satz ≤ 0 €, wird alles abgelehnt (HTTP 400). Antwort: `{ aktualisierteArbeitsgaenge, jahr }`.
- `dto/Verrechnungslohn/VerrechnungslohnErgebnisDto.java` + `VerrechnungslohnUebernehmenRequest.java`
  - Modus (`RUECKWIRKEND` / `HOCHRECHNUNG`), Lohnzeilen und Stundenzeilen pro MA, Kostenstellen, Abteilungen (Aufschlag-Vorschlag immer 0), Datenlücken
  - Lohnsumme, Verkäufliche Stunden, Gemeinkosten gesamt, Selbstkosten pro Stunde, interne Quote
  - Default-Flags pro Wert (`…IstDefault`, für UI-Anzeige „≈“) und Quelle des Bruttos (`LOHNABRECHNUNG`, `KALKULATORISCH`, `STUNDENLOHN_HOCHRECHNUNG`, `STAMMSTUNDENLOHN`)

### 3. Frontend Rechner-Dialog ✅

`components/VerrechnungslohnRechnerDialog.tsx`, geöffnet über den Knopf „Was muss meine Stunde kosten?“ auf der Seite Arbeitsgänge. Untertitel: „Lohnkosten + Gemeinkosten ÷ verkaufbare Stunden = dein Mindest-Stundensatz.“

- **Oben:** „Welches Jahr?“, Modus-Anzeige, bei Schätzung das Feld „Interne Stunden?“ mit „Neu rechnen“, ggf. Hinweis auf fehlende Daten
- **Block 1 – Meine Leute (Lohnkosten pro Jahr)** (aufklappbar, default zugeklappt): Tabelle pro MA mit Brutto/Jahr, AG-SV, BG, Sachbezug, Gesamtkosten → Gesamtkosten überschreibbar
- **Block 2 – Was kostet die Firma sonst noch?** (aufklappbar): Tabelle pro Kostenstelle mit Jahresanteil → überschreibbar
- **Block 3 – Wie viele Stunden kann ich verkaufen?** (aufklappbar): Tabelle pro MA mit Soll/Urlaub/Krank/Krankengeld-Wiedereingliederung/Intern/Verkäuflich → Verkäuflich überschreibbar, Default-Werte mit „≈“ markiert
- **Block 4 – Was muss meine Stunde kosten?** („Selbstkosten + Gewinn = Verkaufspreis“): Lohn, Gemeinkosten, Verkäufliche Stunden, Selbstkosten; Schieberegler „Gewinn-Aufschlag“ (0–100 %, Start 20 %); „Dein Verkaufspreis … pro Stunde“
- **Aufschlag/Abschlag pro Abteilung**: ein €-Feld je Abteilung (auch negativ) mit Endpreis
- **Knopf** „Auf alle Arbeitsgänge für 2026 übernehmen“ → Rückfrage „Es werden alle Arbeitsgang-Stundensätze für 2026 überschrieben. Fortfahren?“ mit „Ja, überschreiben“

Überschriebene Werte gelten nur im offenen Fenster und werden nicht gespeichert.

## Status-Checkliste

- [x] Konzept festgehalten (dieses Dokument)
- [x] Bauabschnitt 1 Backend (Spalten + Domain + DTO + Service)
- [x] Bauabschnitt 1 Frontend (Mitarbeiter-Editor: Checkbox + 2 Felder)
- [x] Bauabschnitt 2 Backend (VerrechnungslohnService + Controller + DTO)
- [x] Bauabschnitt 3 Frontend (Rechner-Dialog auf der Seite Arbeitsgänge)
- [x] Globaler Übernehmen-Endpoint mit Per-Abteilung-Override
- [x] Tests: `VerrechnungslohnServiceTest`, `VerrechnungslohnControllerTest`, `MitarbeiterServiceGeschaeftsfuehrerTest`, `VerrechnungslohnRechnerDialog.test.tsx`, E2E `e2e/verrechnungslohn-langzeitfall.spec.ts`
- [x] Später ergänzt: Belege als Gemeinkosten, Langzeitkrankmeldung herausrechnen, historische Zeitkonto-Versionen

## Entscheidungen (früher „Offene Punkte“)

- Krankheitstage-Default: **8 Tage** – nur wenn keine Krankheitsstunden gebucht sind.
- Interne-Stunden-Default: **5 %**, im Fenster änderbar (wirkt nur bei der Schätzung).
- Bundesland Feiertage: fest **Bayern (BY)**; `Firmeninformation` hat kein Bundesland-Feld.
- Pro-Abteilung-Aufschläge werden **nicht** gespeichert, sondern jedes Mal frisch eingegeben (kein Feld `Abteilung.verrechnungslohnAufschlag`).
- „Übernehmen“ überschreibt bestehende Sätze für das Jahr – mit Bestätigungs-Dialog, nur für Admins, in einer Transaktion. Ein Backup/Audit-Eintrag wird **nicht** geschrieben.

## Abweichungen vom ursprünglichen Plan

- Nicht gebaut: Prüfung „Zeitbuchungen über mind. 90 % der Soll-Tage“, Jahresauswahl nur mit Jahren mit Zeitbuchungen, Eingabe „% verkauft“ und eigene GF-Prozente, Hochrechnung der Gemeinkosten (Year-To-Date × 12 ÷ Monate), Überschreiben einzelner Soll/Urlaub/Krank/Intern-Werte.
- Anders gelöst: Lohnsumme bei Schätzung aus Stundenlohn × Jahres-Soll statt Brutto/Monat × 12; GF mit SV-Pflicht bekommen AG-SV und BG; BG-Satz kommt aus dem Gewerk des Betriebs.
