# GitHub ohne MCP-Connector: Issue und PR per Token

Der Skill braucht GitHub an zwei Stellen — Schritt 2 (Issue anlegen) und Schritt 6
(Pull Request). Der bequeme Weg ist der GitHub-MCP-Connector. Ist er nicht autorisiert
und `gh` nicht angemeldet, gibt es diesen Weg, statt die Pipeline abzubrechen.

## Erst prüfen, was da ist

```bash
gh auth status                 # angemeldet? Dann einfach gh pr create nutzen.
[ -n "$GH_TOKEN" ] && echo da  # Token in der Umgebung?
```

Beides leer, und kein MCP-Tool per `ToolSearch` auffindbar? Dann Token holen.

## Token holen — der Nutzer schreibt, du liest nur

**Niemals** den Nutzer bitten, ein Token in den Chat zu tippen; es landet sonst dauerhaft
im Transkript. Stattdessen:

1. Leere Datei im **Scratchpad** anlegen (nicht im Projekt, nicht in `/tmp`) und den Pfad
   nennen: `printf '%s' "" > "$SCRATCHPAD/github-token.txt"`
2. Dem Nutzer sagen, welche Rechte nötig sind: bei einem fein granularen Token
   **Pull requests: Read and write** (für Issues zusätzlich **Issues: Read and write**),
   beim klassischen Token der Haken bei **repo**.
3. Warten, bis er „gesetzt" meldet.

## Benutzen, ohne es je auszugeben

Token in eine Variable lesen und nur an `curl` weiterreichen. Kein `echo`, kein `set -x`,
den Wert nirgends in eine Datei schreiben, die im Projekt landet.

```bash
T=$(tr -d ' \t\r\n' < "$SCRATCHPAD/github-token.txt")

# Erst prüfen, dass es trägt (401 = falsch/abgelaufen, 403 = Rechte fehlen)
curl -s -o "$SCRATCHPAD/repo.json" -w "%{http_code}\n" \
  -H "Authorization: Bearer $T" -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/<owner>/<repo>
# im JSON: permissions.push muss true sein
```

**Pull Request anlegen.** Den Body nicht inline in `-d` basteln — Umlaute, Backticks und
Zeilenumbrüche zerlegen dir das JSON (real passiert: HTTP 400). Stattdessen die Nutzlast
mit Python aus der Markdown-Datei bauen und per `--data-binary @datei` schicken:

```python
import io, json
body = io.open('docs/superpowers/plans/<datum>-pr-<thema>.md', encoding='utf-8').read()
json.dump({"title": "...", "head": "<feature-branch>", "base": "main",
           "body": body, "maintainer_can_modify": True},
          io.open(f'{sp}/pr.json', 'w', encoding='utf-8'), ensure_ascii=False)
```

```bash
curl -s -o "$SCRATCHPAD/pr-antwort.json" -w "%{http_code}\n" -X POST \
  -H "Authorization: Bearer $T" -H "Accept: application/vnd.github+json" \
  -H "Content-Type: application/json" --data-binary "@$SCRATCHPAD/pr.json" \
  https://api.github.com/repos/<owner>/<repo>/pulls
# 201 = angelegt; html_url und number stehen in der Antwort
```

**Issue anlegen** (Schritt 2) geht genauso gegen `/issues` mit `{"title","body"}`.
**Kommentar** an PR oder Issue: `/issues/<nummer>/comments` — ein PR ist für diesen
Endpunkt ein Issue.

Verlinkung mit dem Issue: `Schließt #<nummer>` in den PR-Body. GitHub verlinkt es und
schließt das Issue beim Merge — ein Extra-Kommentar ist überflüssig.

## Danach aufräumen — nicht vergessen

```bash
rm -f "$SCRATCHPAD/github-token.txt" "$SCRATCHPAD/repo.json" "$SCRATCHPAD/pr.json" \
      "$SCRATCHPAD/pr-antwort.json"
```

Dem Nutzer sagen, dass die Datei weg ist. Wenn das Token nur für diesen einen Zweck
erzeugt wurde, darf er es danach auf GitHub widerrufen.

## Was nicht geht

- Das Token in eine Projektdatei schreiben, committen oder in einer Commit-Message
  erwähnen. `application-local.properties` und `.env` sind ohnehin Sperrzone.
- Das Token an einen Subagenten weitergeben. Der Orchestrator legt Issue und PR selbst an.
- Aus den Git-Zugangsdaten des Rechners (Credential Manager) heimlich ein Token ziehen.
  Fragen kostet einen Satz.
