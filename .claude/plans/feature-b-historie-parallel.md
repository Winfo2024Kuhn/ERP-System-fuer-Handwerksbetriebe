# Feature B — Teil 2: Preis-Historie + zentraler Hook-Service (PARALLEL-AGENT)

**An den nächsten Agenten:** Dieser Plan wird **parallel** zu einem anderen
Agenten abgearbeitet. Der andere Agent kümmert sich um den Agent-Tool-Pfad
(`ArtikelMatchingToolService` + `SYSTEM_PROMPT` + Tool-Parameter `mengeKg`)
und das Admin-Endpoint für den Durchschnittspreis-Backfill. **Du darfst die
dort genannten Dateien NICHT anfassen.** Siehe Abschnitt "Absolute
Abgrenzung — wer macht was" unten.

**Branch:** `feature/en1090-echeck` (beide arbeiten auf demselben Branch,
daher ist saubere Datei-Abgrenzung kritisch — sonst Merge-Konflikt beim
`git pull` vor dem Commit).

**Voraussetzung:** Etappe B1 ist fertig (Schema V228, Entity-Felder,
`ArtikelDurchschnittspreisService` mit 14 grünen Tests). Die entsprechende
Trackerdatei ist [feature-b-durchschnittspreis.md](feature-b-durchschnittspreis.md)
— lies die zur Einordnung, aber ändere sie NICHT (der andere Agent pflegt
die Etappen B2+B3 dort).

---

## Mission in einem Satz

Baue eine **Preis-Historie-Tabelle**, die bei **jeder** Preisänderung einen
Eintrag erhält, und einen **zentralen Hook-Service**, der a) die Historie
schreibt und b) bei Rechnungs-Quellen zusätzlich den gewichteten
Durchschnittspreis aktualisiert. Verdrahte den Hook an **7 von 8**
Preis-Setz-Stellen (die 8. macht der andere Agent).

---

## Warum das wichtig ist (Kontext, der in Code nicht steht)

1. **Die Historie kann nicht rückwirkend befüllt werden.** Jeder Tag ohne
   Historie-Schreiben ist verlorene Information für die spätere
   Trend-Chart-Anzeige. Deshalb JETZT, auch wenn die UI dafür erst
   später kommt.
2. **Durchschnittspreis ≠ Historie.** Der Durchschnitt akkumuliert nur
   bezahlte Rechnungen (= faktische Preise). Die Historie loggt
   **alles** (Katalog, Angebot, Rechnung, manuell, Vorschlag) — mit einer
   `quelle`-Spalte, damit spätere Auswertungen filtern können.
3. **DRY:** Bisher ist `setPreis()`-Logik an 8 Stellen dupliziert. Statt
   dort überall Durchschnitts- und Historie-Code einzubauen, kapseln wir
   das in **einem** Hook-Service. Alle Stellen rufen denselben Einzeiler.
   CLAUDE.md fordert explizit DRY und "strategisch auslagern".

---

## Absolute Abgrenzung — wer macht was

### DEINE Dateien (PARALLEL-AGENT, du) — exklusiv dein Bereich

**Neu anzulegen:**
- `src/main/resources/db/migration/V229__artikel_preis_historie.sql`
- `src/main/java/org/example/kalkulationsprogramm/domain/ArtikelPreisHistorie.java`
- `src/main/java/org/example/kalkulationsprogramm/domain/PreisQuelle.java` (Enum)
- `src/main/java/org/example/kalkulationsprogramm/repository/ArtikelPreisHistorieRepository.java`
- `src/main/java/org/example/kalkulationsprogramm/service/ArtikelPreisHookService.java`
- `src/test/java/org/example/kalkulationsprogramm/service/ArtikelPreisHookServiceTest.java`

**Anzufassen (Hook einbauen, siehe Mapping unten):**
- `src/main/java/org/example/kalkulationsprogramm/service/GeminiDokumentAnalyseService.java` (Zeile ~2774 und ~2918)
- `src/main/java/org/example/kalkulationsprogramm/service/OfferPriceService.java` (Zeile ~74)
- `src/main/java/org/example/kalkulationsprogramm/service/ArtikelImportService.java` (Zeile ~222)
- `src/main/java/org/example/kalkulationsprogramm/service/LieferantArtikelpreisService.java` (Zeile ~52 und ~79)
- `src/main/java/org/example/kalkulationsprogramm/service/ArtikelService.java` (Zeile ~55)
- `src/main/java/org/example/kalkulationsprogramm/controller/ArtikelVorschlagController.java` (Zeile ~151)

### TABU für dich (macht der andere Agent)

- `src/main/java/org/example/kalkulationsprogramm/service/ArtikelMatchingToolService.java`
  → der andere Agent erweitert dort das Tool `update_artikel_preis` um den Parameter `mengeKg` und ruft danach selbst deinen `ArtikelPreisHookService` auf. **Du baust den Hook-Service so, dass er bereit dafür ist, aber ruf ihn dort NICHT selbst ein.**
- `src/main/java/org/example/kalkulationsprogramm/service/ArtikelMatchingAgentService.java` (Prompt-Änderung)
- `src/main/java/org/example/kalkulationsprogramm/controller/AdminArtikelController.java` (wird vom anderen Agent neu angelegt)
- `src/main/java/org/example/kalkulationsprogramm/domain/Artikel.java` (fertig, Finger weg)
- `src/main/java/org/example/kalkulationsprogramm/service/ArtikelDurchschnittspreisService.java` (fertig, Finger weg — du nutzt ihn nur via Injection)
- `docs/ADMIN_ENDPOINTS.md` (anderer Agent)
- `.claude/plans/feature-b-durchschnittspreis.md` (anderer Agent haken dort B2+B3 ab)

### Gemeinsamer Berührungspunkt — Absprache nötig

Keiner. Der andere Agent injiziert `ArtikelPreisHookService` in
`ArtikelMatchingToolService` und ruft `hook.registriere(...)` dort selbst
auf. Du lieferst nur den Service, er verdrahtet seine eine Stelle.

---

## Schritt 1 — V229 Migration

Datei: `src/main/resources/db/migration/V229__artikel_preis_historie.sql`

```sql
-- Preis-Historie: loggt jede Preisaenderung an einem Lieferanten-Artikel.
-- Rechnungs-Eintraege (quelle='RECHNUNG') sind zusaetzlich Datenpunkte fuer
-- den gewichteten Durchschnittspreis in artikel.durchschnittspreis_netto.
CREATE TABLE IF NOT EXISTS artikel_preis_historie (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    artikel_id          BIGINT       NOT NULL,
    lieferant_id        BIGINT       NULL,
    preis_pro_kg        DECIMAL(12,4) NOT NULL,
    menge_kg            DECIMAL(18,3) NULL,
    quelle              VARCHAR(32)  NOT NULL,
    externe_nummer      VARCHAR(255) NULL,
    beleg_referenz      VARCHAR(255) NULL,
    erfasst_am          DATETIME(6)  NOT NULL,
    bemerkung           VARCHAR(500) NULL,
    CONSTRAINT fk_aph_artikel    FOREIGN KEY (artikel_id)   REFERENCES artikel(id)     ON DELETE CASCADE,
    CONSTRAINT fk_aph_lieferant  FOREIGN KEY (lieferant_id) REFERENCES lieferanten(id) ON DELETE SET NULL,
    INDEX idx_aph_artikel_erfasst (artikel_id, erfasst_am),
    INDEX idx_aph_quelle          (quelle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Begründungen zu Feldern** (für dich, nicht in die SQL-Kommentare):
- `lieferant_id` nullable: es gibt Stellen (z.B. `ArtikelService.erstelleArtikel`), an denen der Lieferant optional ist.
- `menge_kg` nullable: nur bei Rechnungs-Quellen verfügbar; bei Katalog/Angebot/Manuell = `null`.
- `externe_nummer` + `beleg_referenz`: optional, aber wertvoll fürs spätere Debugging ("welche Rechnung hat diesen Ausreißer verursacht?").
- `quelle` VARCHAR statt ENUM auf DB-Ebene: MySQL-ENUM ist schmerzhaft zu erweitern. JPA-Enum auf Java-Seite reicht.

---

## Schritt 2 — Enum `PreisQuelle`

Datei: `src/main/java/org/example/kalkulationsprogramm/domain/PreisQuelle.java`

```java
package org.example.kalkulationsprogramm.domain;

public enum PreisQuelle {
    RECHNUNG,    // tatsaechlich bezahlt — triggert Durchschnittspreis-Update
    ANGEBOT,     // E-Mail-Angebot vom Lieferanten
    KATALOG,     // CSV-Preisliste / Preiskatalog-Import
    MANUELL,     // manuelle UI-Eingabe / API-CRUD
    VORSCHLAG    // aus Artikel-Vorschlag (Agent-Match unsicher, vom User bestaetigt)
}
```

---

## Schritt 3 — Entity `ArtikelPreisHistorie`

Datei: `src/main/java/org/example/kalkulationsprogramm/domain/ArtikelPreisHistorie.java`

```java
package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "artikel_preis_historie")
public class ArtikelPreisHistorie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artikel_id", nullable = false)
    private Artikel artikel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieferant_id")
    private Lieferanten lieferant;

    @Column(name = "preis_pro_kg", nullable = false, precision = 12, scale = 4)
    private BigDecimal preisProKg;

    @Column(name = "menge_kg", precision = 18, scale = 3)
    private BigDecimal mengeKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "quelle", nullable = false, length = 32)
    private PreisQuelle quelle;

    @Column(name = "externe_nummer", length = 255)
    private String externeNummer;

    @Column(name = "beleg_referenz", length = 255)
    private String belegReferenz;

    @Column(name = "erfasst_am", nullable = false)
    private LocalDateTime erfasstAm;

    @Column(name = "bemerkung", length = 500)
    private String bemerkung;
}
```

---

## Schritt 4 — Repository

Datei: `src/main/java/org/example/kalkulationsprogramm/repository/ArtikelPreisHistorieRepository.java`

```java
package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ArtikelPreisHistorie;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtikelPreisHistorieRepository extends JpaRepository<ArtikelPreisHistorie, Long> {

    List<ArtikelPreisHistorie> findByArtikel_IdOrderByErfasstAmDesc(Long artikelId);

    List<ArtikelPreisHistorie> findByArtikel_IdAndQuelleOrderByErfasstAmDesc(Long artikelId, PreisQuelle quelle);
}
```

---

## Schritt 5 — Der Hook-Service (Herzstück)

Datei: `src/main/java/org/example/kalkulationsprogramm/service/ArtikelPreisHookService.java`

**Design-Ziel:** Ein einziger Methoden-Aufruf an den 8 Stellen. Alles andere
versteckt sich dahinter. Der Service ist "best-effort" — er wirft NIE eine
Exception nach außen (Log-Warning bei Fehlern), weil sonst ein einzelner
Historie-Fehler einen ganzen Rechnungs-Import kippen könnte.

```java
package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelPreisHistorie;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.repository.ArtikelPreisHistorieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Zentraler Einstiegspunkt fuer alle Preis-Aenderungen an
 * {@link org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise}.
 *
 * <p>Wird an jeder {@code setPreis(...)}-Stelle aufgerufen und
 * <ul>
 *   <li>schreibt immer einen {@link ArtikelPreisHistorie}-Eintrag,</li>
 *   <li>triggert bei {@code quelle == RECHNUNG} zusaetzlich das
 *       gewichtete Update in {@link ArtikelDurchschnittspreisService}.</li>
 * </ul>
 *
 * <p>Best-effort: Fehler werden geloggt, nicht geworfen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtikelPreisHookService {

    private final ArtikelPreisHistorieRepository historieRepository;
    private final ArtikelDurchschnittspreisService durchschnittspreisService;

    /**
     * Registriert eine Preisaenderung.
     *
     * @param artikel      Pflicht. Wird keine Aktion ausgefuehrt wenn null.
     * @param lieferant    optional (z.B. bei Erst-Anlage ohne Lieferant)
     * @param preisProKg   Pflicht in €/kg, bereits normalisiert. Muss > 0 sein.
     * @param mengeKg      optional, nur bei RECHNUNG gesetzt. Muss > 0 sein.
     * @param quelle       Pflicht. Steuert ob Durchschnitt aktualisiert wird.
     * @param externeNummer optional, fuer Nachvollziehbarkeit
     * @param belegReferenz optional (z.B. Rechnungsnummer)
     * @param bemerkung    optional, frei
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void registriere(
            Artikel artikel,
            Lieferanten lieferant,
            BigDecimal preisProKg,
            BigDecimal mengeKg,
            PreisQuelle quelle,
            String externeNummer,
            String belegReferenz,
            String bemerkung) {
        if (artikel == null || preisProKg == null || quelle == null) {
            log.warn("Preis-Hook uebersprungen: artikel={}, preis={}, quelle={}",
                    artikel != null ? artikel.getId() : null, preisProKg, quelle);
            return;
        }
        if (preisProKg.signum() <= 0) {
            log.warn("Preis-Hook uebersprungen fuer Artikel {}: preis={} (<= 0)",
                    artikel.getId(), preisProKg);
            return;
        }

        try {
            ArtikelPreisHistorie eintrag = new ArtikelPreisHistorie();
            eintrag.setArtikel(artikel);
            eintrag.setLieferant(lieferant);
            eintrag.setPreisProKg(preisProKg);
            eintrag.setMengeKg(mengeKg);
            eintrag.setQuelle(quelle);
            eintrag.setExterneNummer(externeNummer);
            eintrag.setBelegReferenz(belegReferenz);
            eintrag.setBemerkung(bemerkung);
            eintrag.setErfasstAm(LocalDateTime.now());
            historieRepository.save(eintrag);
        } catch (Exception e) {
            log.warn("Preis-Historie-Eintrag fehlgeschlagen fuer Artikel {}: {}",
                    artikel.getId(), e.getMessage());
        }

        if (quelle == PreisQuelle.RECHNUNG && mengeKg != null && mengeKg.signum() > 0) {
            try {
                durchschnittspreisService.aktualisiere(artikel, mengeKg, preisProKg);
            } catch (Exception e) {
                log.warn("Durchschnittspreis-Update fehlgeschlagen fuer Artikel {}: {}",
                        artikel.getId(), e.getMessage());
            }
        }
    }

    /** Convenience fuer Nicht-Rechnungs-Quellen ohne Menge/Beleg. */
    public void registriere(Artikel artikel, Lieferanten lieferant, BigDecimal preisProKg,
                            PreisQuelle quelle, String externeNummer) {
        registriere(artikel, lieferant, preisProKg, null, quelle, externeNummer, null, null);
    }
}
```

**Designentscheidungen und WARUM:**
- `@Transactional(Propagation.REQUIRED)`: läuft in der bestehenden Transaktion der Caller-Stelle mit. Das ist wichtig, weil der Caller meistens im selben Flow schon speichert.
- `try/catch` um beide Aktionen einzeln: ein Historie-Fehler darf NIE ein Rechnungs-Update kippen. Daten-Integrität > Historie-Vollständigkeit.
- Convenience-Overload: 80% der Call-Sites brauchen nur 4 Parameter. Lange Signatur nur, wo Menge/Beleg vorhanden sind.
- KEINE Validierung auf "Preis plausibel" (Range-Check): das passiert schon in den Callern (`normalizePreis`, `normalizePreisZuKg`). DRY.

---

## Schritt 6 — Unit-Tests für den Hook-Service

Datei: `src/test/java/org/example/kalkulationsprogramm/service/ArtikelPreisHookServiceTest.java`

Minimum-Testabdeckung (**alle müssen grün sein**, `@ExtendWith(MockitoExtension.class)`, Repo + Durchschnittspreis-Service mocken):

1. `rechnungMitMenge_schreibtHistorieUndAktualisiertDurchschnitt` — erwarte `historieRepository.save(...)` UND `durchschnittspreisService.aktualisiere(...)`.
2. `rechnungOhneMenge_schreibtHistorie_aberKeinDurchschnittsUpdate` — mengeKg=null, Durchschnitts-Mock wird `never()` gerufen.
3. `angebot_schreibtNurHistorie` — quelle=ANGEBOT, Durchschnitts-Mock `never()`.
4. `katalog_schreibtNurHistorie` — quelle=KATALOG.
5. `manuell_schreibtNurHistorie` — quelle=MANUELL.
6. `vorschlag_schreibtNurHistorie` — quelle=VORSCHLAG.
7. `nullArtikel_wirdIgnoriert` — weder Historie noch Durchschnitt.
8. `nullPreis_wirdIgnoriert` — dito.
9. `nullQuelle_wirdIgnoriert` — dito.
10. `negativePreis_wirdIgnoriert` — dito.
11. `historieSaveWirftException_durchschnittLaeuftTrotzdem` — verify: Durchschnitts-Service wird bei RECHNUNG mit gültiger Menge trotzdem gerufen.
12. `durchschnittWirftException_fliegtNichtNachAussen` — Aufruf wirft nicht; Historie wurde gespeichert.
13. `conveniencOverload_delegiertMitNullMengeUndBeleg` — prüft, dass die 4-Parameter-Variante korrekt an die Hauptmethode delegiert.

Beispiel-Gerüst für Test #1:
```java
@Test
void rechnungMitMenge_schreibtHistorieUndAktualisiertDurchschnitt() {
    Artikel a = artikel(1L);
    Lieferanten l = lieferant(2L);

    hook.registriere(a, l, new BigDecimal("1.50"), new BigDecimal("100"),
            PreisQuelle.RECHNUNG, "EXT-123", "RE-2026-0001", null);

    ArgumentCaptor<ArtikelPreisHistorie> captor = ArgumentCaptor.forClass(ArtikelPreisHistorie.class);
    verify(historieRepository).save(captor.capture());
    ArtikelPreisHistorie saved = captor.getValue();
    assertThat(saved.getArtikel()).isEqualTo(a);
    assertThat(saved.getLieferant()).isEqualTo(l);
    assertThat(saved.getPreisProKg()).isEqualByComparingTo("1.50");
    assertThat(saved.getMengeKg()).isEqualByComparingTo("100");
    assertThat(saved.getQuelle()).isEqualTo(PreisQuelle.RECHNUNG);
    assertThat(saved.getExterneNummer()).isEqualTo("EXT-123");
    assertThat(saved.getBelegReferenz()).isEqualTo("RE-2026-0001");
    assertThat(saved.getErfasstAm()).isNotNull();

    verify(durchschnittspreisService).aktualisiere(a, new BigDecimal("100"), new BigDecimal("1.50"));
}
```

---

## Schritt 7 — Verdrahtung an den 7 Stellen

**Prinzip:** Nach jedem `lap.setPreis(...)` (oder `preis.setPreis(...)` /
`entity.setPreis(...)`) folgt genau **eine** Zeile Hook-Aufruf. Keine
Logik-Änderung sonst. Die Preis-Setzung und der Hook dürfen in
unterschiedlicher Reihenfolge stehen (Hook sieht den Artikel, nicht
LieferantenArtikelPreise).

### 7.1 — GeminiDokumentAnalyseService.java — Stelle 1 (~Z. 2774, JSON-Pfad)

Sichtbarer Kontext (siehe Original ~2753–2781):

```java
BigDecimal preisProKg = normalizePreisZuKg(einzelpreis, preiseinheit);
// ...
LieferantenArtikelPreise lap = lapOpt.get();
lap.setPreis(preisProKg);
lap.setPreisAenderungsdatum(new Date());
artikelPreiseRepository.save(lap);
updated++;
```

**Ergänzung danach** (vor dem `log.info(...)` am Ende):

```java
BigDecimal mengeKg = extrahiereMengeKg(pos);   // siehe Hilfsmethode unten
hookService.registriere(
        lap.getArtikel(),
        lieferant,
        preisProKg,
        mengeKg,
        PreisQuelle.RECHNUNG,
        externeNr,
        null,                                   // beleg-ref weiter oben greifbar? wenn ja hier setzen
        null);
```

**Hilfsmethode in derselben Klasse**, parallel zu `normalizePreisZuKg` (private static, einmal):

```java
/** Liefert die Menge der Rechnungsposition normalisiert auf kg oder null. */
private BigDecimal extrahiereMengeKg(JsonNode pos) {
    if (pos == null) return null;
    JsonNode mengeNode = pos.get("menge");
    if (mengeNode == null || mengeNode.isNull()) return null;
    try {
        BigDecimal menge = new BigDecimal(mengeNode.asText().replace(',', '.'));
        String einheit = pos.has("mengeneinheit") ? pos.get("mengeneinheit").asText("kg") : "kg";
        return normalizeMengeZuKg(menge, einheit);
    } catch (NumberFormatException e) {
        return null;
    }
}
```

**Zusätzlich brauchst du** eine `normalizeMengeZuKg`-Methode. Spiegel die
bestehende `normalizePreisZuKg` — gleiche Einheiten-Faktoren (kg=1, t=1000,
g=0.001), aber **KEINE** Division (bei Menge multiplizieren statt
dividieren — Preis normalisiert divisiv, Menge multiplikativ). Wenn dir die
Herleitung unklar ist: schau in `normalizePreisZuKg` (ca. Z. 2789+),
invertiere die Rechnung.

Wichtig: Wenn dort unklar ist, welche Einheiten auftauchen können —
**lieber konservativ: bei unbekannter Einheit `null` zurückgeben**.
Dann fehlt dieser Datenpunkt im Durchschnitt, aber die Historie bekommt den
Eintrag ohne Menge (harmlos).

### 7.2 — GeminiDokumentAnalyseService.java — Stelle 2 (~Z. 2918, ZUGFeRD-Pfad)

Analog zu 7.1, aber mit `pos.getMenge()` und `pos.getMengeneinheit()` (POJO
statt JsonNode). Beleg-Referenz könnte dort als Rechnungsnummer greifbar sein —
such im Umfeld nach `rechnungsnummer` o.ä. Variablen.

```java
BigDecimal mengeKg = normalizeMengeZuKg(pos.getMenge(), pos.getMengeneinheit());
hookService.registriere(
        lap.getArtikel(),
        lieferant,
        preisProKg,
        mengeKg,
        PreisQuelle.RECHNUNG,
        externeNr,
        /* belegRef wenn greifbar */ null,
        null);
```

### 7.3 — OfferPriceService.java — Stelle 3 (~Z. 74)

Quelle `ANGEBOT`. Keine Menge. Nach `preis.setPreis(finalPrice)`:

```java
hookService.registriere(artikel, lieferant, finalPrice, PreisQuelle.ANGEBOT, code);
```

`code` ist die externe Nummer (steht oben in der Schleife, `item.code()`). `artikel` ist der Lambda-Parameter.

### 7.4 — ArtikelImportService.java — Stelle 4 (~Z. 222)

Quelle `KATALOG`. Keine Menge. Nach `lap.setPreis(preis)`:

```java
hookService.registriere(currentArtikel, lieferant, preis, PreisQuelle.KATALOG, externeNr);
```

### 7.5 + 7.6 — LieferantArtikelpreisService.java — Stelle 5 + 6 (~Z. 52 und 79)

Quelle `MANUELL`. Nach `entity.setPreis(preis)` in beiden Methoden
(`aktualisiere(...)` und `anlegen(...)`):

```java
hookService.registriere(entity.getArtikel(), entity.getLieferant(), preis,
        PreisQuelle.MANUELL, normalizeExterneArtikelnummer(externeArtikelnummer));
```

### 7.7 — ArtikelService.java — Stelle 7 (~Z. 55)

Quelle `MANUELL`. Neuer Artikel. Nach `preis.setPreis(dto.getPreis())` —
ABER: `preis.setLieferant(...)` wird erst danach gesetzt, also den Hook
erst NACH `ifPresent(preis::setLieferant)` aufrufen:

```java
// am Ende des Blocks, nach lieferant-Zuweisung und save
hookService.registriere(saved, preis.getLieferant(), dto.getPreis(),
        PreisQuelle.MANUELL, dto.getExterneArtikelnummer());
```

Aber nur, wenn `dto.getPreis() != null` (sonst sinnloser Historie-Eintrag).

### 7.8 — ArtikelVorschlagController.java — Stelle 8 (~Z. 151)

Quelle `VORSCHLAG`. Nach `lap.setPreis(v.getEinzelpreis())`:

```java
if (v.getEinzelpreis() != null) {
    hookService.registriere(gespeichert, v.getLieferant(), v.getEinzelpreis(),
            PreisQuelle.VORSCHLAG, v.getExterneArtikelnummer());
}
```

Controller braucht `ArtikelPreisHookService` injiziert (`@RequiredArgsConstructor` prüfen, sonst Field-Injection mit `@Autowired`, aber bevorzugt Constructor-Injection wenn der Controller das schon macht).

---

## Schritt 8 — Tests grün halten

**Bestehende Tests** könnten brechen, weil sie Mock-Instanzen der betroffenen
Services ohne Hook bauen. Gegenmaßnahme:

- In Integration-Tests, die Spring-Context laden: läuft automatisch, Hook wird injiziert.
- In Unit-Tests mit `@Mock` / `new Service(...)`-Konstruktor: dort musst du `ArtikelPreisHookService` als `@Mock` ergänzen und dem Service-Konstruktor mitgeben. Die Mock-Verhalten-Default (no-op) passt — keine weiteren `when(...)` nötig.

Betroffene Test-Klassen (nicht abschließend, prüfe per Grep `new ArtikelImportService(`, `new OfferPriceService(`, etc.):
- `ArtikelImportServiceTest`
- `OfferPriceServiceTest`
- `LieferantArtikelpreisServiceTest`
- `ArtikelServiceTest`
- `ArtikelVorschlagControllerTest`

Wenn ein Test ohne Hook-Mock den Service instanziiert und dadurch NPE wirft:
Mock hinzufügen und in den Konstruktor durchreichen. **Nicht** den Hook
optional machen — das wäre Fake-Kompatibilität.

---

## Schritt 9 — Abschluss-Checkliste (strikt abarbeiten)

- [ ] V229 Migration angelegt
- [ ] Enum + Entity + Repo angelegt, kompiliert
- [ ] Hook-Service angelegt + 13 Unit-Tests (oder mehr) grün
- [ ] 7 Stellen verdrahtet (GeminiDokument ×2, OfferPrice, ArtikelImport, LieferantArtikelpreis ×2, ArtikelService, ArtikelVorschlagController — macht zusammen 8 Aufrufe in 7 Dateien)
- [ ] Ggf. bestehende Unit-Tests um Hook-Mock erweitert
- [ ] `./mvnw.cmd test 2>&1 | tail -40` — **keine** roten Tests
- [ ] `/review-and-ship` laufen lassen (CLAUDE.md Regel 4)
- [ ] NICHT committen ohne User-Freigabe — melde Fertigstellung und warte

**Commit-Ziel (zum Vorschlag an den User):**
`feat(artikel): Preis-Historie + zentraler Hook-Service (V229)`

---

## Wenn du ein Problem findest

- **`mengeKg` in GeminiDokumentAnalyseService nicht extrahierbar:** Historie trotzdem schreiben (ohne Menge). Durchschnitt bekommt dann in dem Fall keinen Datenpunkt — das ist weniger schlimm als einen falsch geschätzten Datenpunkt einzukippen.
- **ZUGFeRD-Pfad hat andere Feld-Namen:** der ZUGFeRD-Parser liefert ein anderes POJO; Preis + Menge sind dort Methoden (`.getEinzelpreis()`, `.getMenge()`, `.getMengeneinheit()`). Such dort selbst nach den Gettern. Wenn Menge dort nicht verfügbar ist: `null` übergeben und Historie ohne Menge schreiben.
- **Cross-Agent-Konflikt (git pull bringt ToolService-Änderungen):** der andere Agent hat dann den Hook bereits dort verdrahtet. Nicht nochmal machen — seine Zeilen stehen lassen.
- **Ein bestehender Test bricht und die Ursache ist unklar:** NICHT den Test deaktivieren (`@Disabled`) oder die Assertion abschwächen. Lies den Fehler, verstehe, und fixe Testdaten / Mocks.

---

## Was du NICHT tust

- ❌ `ArtikelMatchingToolService.java` anfassen
- ❌ `ArtikelMatchingAgentService.java` anfassen
- ❌ `AdminArtikelController.java` anlegen
- ❌ `Artikel.java` erweitern
- ❌ `ArtikelDurchschnittspreisService.java` anfassen (nur via DI nutzen)
- ❌ `docs/ADMIN_ENDPOINTS.md` editieren
- ❌ `.claude/plans/feature-b-durchschnittspreis.md` editieren (das macht der andere Agent)
- ❌ Frontend-Code editieren (kommt ggf. in Etappe B4 separat)

---

## Session-Log

| Datum | Agent | Was passiert |
|---|---|---|
| 2026-04-21 | Opus 4.7 (Planning) | Plan für Parallel-Agent angelegt. Abgrenzung zum anderen Agenten (ArtikelMatchingToolService + Admin-Endpoint) klar dokumentiert. |
