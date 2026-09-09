# Kontext-Log für loese-problem

Eine Datei pro Vorhaben: `docs/superpowers/plans/<datum>-<thema>-log.md`,
Pfad steht im Kopf der Plan-Datei. **Append-only** — niemand ändert oder
löscht bestehenden Text, jeder hängt nur unten einen neuen Block an.

## Welche Datei — der Haupt-Checkout, nicht dein Worktree

**Der Pfad ist absolut anzugeben, auf das Haupt-Checkout.** Nicht relativ zu
deinem Arbeitsverzeichnis:

```bash
LOG="C:/Users/<du>/dev/<projekt>/docs/superpowers/plans/<datum>-<thema>-log.md"
```

Das ist keine Formsache, sondern der Sinn des Locks. Jedes Worktree hat eine
**eigene Kopie** der Datei. Hängst du relativ an, schreibst du in deine private
Kopie — dort kann dich kein anderer Agent stören, das Lock schützt nichts, und
der Block ist für alle anderen unsichtbar.

Schlimmer: Dein Commit passiert **vor** dem Log-Block (so steht es in der
Reihenfolge unten). Der Block bleibt also als uncommittete Änderung im Worktree
liegen, der Orchestrator merged nur deinen Branch — und der Block ist weg.
Am 09.09.2026 zweimal passiert; beide Blöcke mussten von Hand aus den
Worktrees nachgeholt werden, nachdem der Merge schon durch war.

Der Orchestrator committet das Log getrennt, nachdem alle Agenten eines
Abschnitts fertig sind. Du committest es **nicht** — täte es jeder in seinem
Branch, kollidierte jeder Merge am Dateiende mit dem nächsten.

## Lock-Protokoll (Bash)

Ein Sperr-**Ordner** ist auf allen gängigen Dateisystemen atomar erstellbar
(`mkdir` schlägt fehl, wenn er schon existiert — genau das macht ihn als
Lock brauchbar, eine einfache Datei wäre das nicht).

```bash
LOG="docs/superpowers/plans/<datum>-<thema>-log.md"
LOCK="$LOG.lock"

got_lock=0
for i in $(seq 1 20); do
  if mkdir "$LOCK" 2>/dev/null; then
    got_lock=1
    break
  fi
  sleep 1
done

if [ "$got_lock" != "1" ]; then
  echo "Konnte Kontext-Log nach 20 Versuchen nicht sperren — im Report melden, nicht stillschweigend weitermachen."
else
  cat >> "$LOG" <<'EOF'
<Block hier einsetzen, siehe Vorlage unten>
EOF
  rmdir "$LOCK"
fi
```

**Wichtig:** Den `cat >> ... <<'EOF'`-Block erst bauen, NACHDEM `mkdir`
erfolgreich war — sonst ist die Zeit zwischen Lesen und Schreiben zu lang und
das Lock bringt nichts.

## Block-Vorlage

```markdown
## Abschnitt <N> — Task <M> (<Rolle: Coding-Agent | Review-Agent>)

Zeit: <`date -u +%Y-%m-%dT%H:%M:%SZ`>
Branch: feature/<slug>/task-<M>
Commit(s): <hash1>, <hash2>
Status: fertig | blockiert

Was gemacht wurde:
- <kurz, stichpunktartig>

Bedenken / Abweichungen vom Plan:
- <oder "keine">
```

Coding-Agenten hängen ihren Block an, sobald ihr Task fertig ist. Der
Review-Agent hängt zusätzlich einen Abschnitts-Block mit der Ampel an, wenn
er fertig ist — dieselbe Vorlage, `<M>` entfällt, dafür ein Feld `Ampel:`.
