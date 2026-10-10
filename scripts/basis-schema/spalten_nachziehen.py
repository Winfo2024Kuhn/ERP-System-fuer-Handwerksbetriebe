#!/usr/bin/env python3
"""Spalten-Defaults aus den Migrationen auf die Alt-Tabellen nachziehen.

Problem: Tabellen aus der Zeit vor V208 legt beim Erzeugen der Basis
Hibernate an - ohne die DEFAULT-Werte, die spaetere Migrationen per
ALTER TABLE ... ADD/MODIFY gesetzt haben. Idempotente Migrationen sehen
die Spalte dann als "schon da" an und ueberspringen sie samt Default.
Native INSERTs, die sich auf diese Defaults verlassen, wuerden auf einer
Neuinstallation scheitern (z.B. mitarbeiter.fuehrt_zeitkonto).

Loesung: Je (Tabelle, Spalte) den DEFAULT aus der letzten ADD/MODIFY-
Definition aller Migrationen suchen und als ALTER COLUMN ... SET DEFAULT
ausgeben - bewusst NUR den Default, nie Typ oder NULL-Regel: Viele Migrationen
aendern Typen bedingt (CASE ueber information_schema), das laesst sich nicht
statisch nachbilden; diese Teile liefen beim Erzeugen ohnehin echt.
Nur fuer Alt-Tabellen (aus Hibernate) - Tabellen, die Migrationen selbst
anlegen, sind bereits exakt.  Statements, die nicht passen (Spalte spaeter
umbenannt/geloescht), schlagen beim Einspielen mit --force harmlos fehl.

Aufruf: spalten_nachziehen.py <ordner-mit-V*.sql> <datei-mit-tabellen-aus-migrationen>
        (SQL auf stdout)
"""
import re
import sys
from pathlib import Path

ALTER = re.compile(r"\s*ALTER\s+TABLE\s+`?(\w+)`?\s+(.*)$", re.S | re.I)
KLAUSEL = re.compile(
    r"(?:ADD|MODIFY)\s+(?:COLUMN\s+)?(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?\s+(.+)", re.S | re.I)
KEINE_SPALTE = {"CONSTRAINT", "INDEX", "KEY", "UNIQUE", "PRIMARY", "FOREIGN", "FULLTEXT", "SPATIAL"}
# Teile, die ein MODIFY nicht wiederholen darf (Schluessel/Indizes existieren schon)
WEG = re.compile(r"\s+(AFTER\s+`?\w+`?|FIRST)\s*$|\s+PRIMARY\s+KEY|\s+UNIQUE(\s+KEY)?", re.I)


def zerlegen(text: str, trenner: str):
    """Trennt an `trenner` - aber nur ausserhalb von '...', "...", `...`,
    Klammern und --/#-Kommentaren. Liefert zusaetzlich alle String-Literale
    (entpackt), damit ALTERs aus PREPARE-Strings auch gefunden werden."""
    teile, literale = [], []
    aktuell, literal = [], []
    quote, tiefe, i = None, 0, 0
    while i < len(text):
        z = text[i]
        if quote:
            if z == quote and i + 1 < len(text) and text[i + 1] == quote:
                aktuell.append(z + z)
                literal.append(z)
                i += 2
                continue
            if z == "\\" and quote != "`" and i + 1 < len(text):
                aktuell.append(text[i:i + 2])
                literal.append(text[i + 1])
                i += 2
                continue
            if z == quote:
                quote = None
                if z != "`":
                    literale.append("".join(literal))
            else:
                literal.append(z)
            aktuell.append(z)
        elif z in "'\"`":
            quote, literal = z, []
            aktuell.append(z)
        elif text.startswith("--", i) or z == "#":
            ende = text.find("\n", i)
            i = len(text) if ende < 0 else ende
            continue
        else:
            if z == "(":
                tiefe += 1
            elif z == ")":
                tiefe -= 1
            if z == trenner and tiefe == 0:
                teile.append("".join(aktuell))
                aktuell = []
            else:
                aktuell.append(z)
        i += 1
    teile.append("".join(aktuell))
    return [t.strip() for t in teile if t.strip()], literale


def main() -> None:
    dateien = sorted(Path(sys.argv[1]).glob("V*.sql"), key=lambda p: int(re.match(r"V(\d+)", p.name).group(1)))
    letzte = {}
    for datei in dateien:
        anweisungen, literale = zerlegen(datei.read_text(encoding="utf-8"), ";")
        # ALTERs stehen direkt im Skript oder als String (PREPARE-Muster)
        kandidaten = anweisungen + [l for l in literale if re.match(r"\s*ALTER\s+TABLE", l, re.I)]
        for anweisung in kandidaten:
            treffer = ALTER.match(anweisung)
            if not treffer:
                continue
            tabelle = treffer.group(1)
            klauseln, _ = zerlegen(treffer.group(2), ",")
            for klausel in klauseln:
                spalte = KLAUSEL.match(klausel)
                # "MODIFY COLUMN typ" ohne Definition: per CONCAT zusammengesetzte
                # ENUM-Umbauten - die liefen beim Einspielen schon echt mit
                if not spalte or spalte.group(1).upper() in KEINE_SPALTE | {"COLUMN"}:
                    continue
                definition = WEG.sub("", spalte.group(2).strip()).strip()
                if "GENERATED" in definition.upper() or not definition:
                    continue
                letzte[(tabelle.lower(), spalte.group(1).lower())] = (tabelle, spalte.group(1), definition)
    aus_migrationen = {
        zeile.strip().lower() for zeile in Path(sys.argv[2]).read_text(encoding="utf-8").splitlines() if zeile.strip()}
    for tabelle, spalte, definition in letzte.values():
        if tabelle.lower() in aus_migrationen:
            continue
        standard = re.search(r"\bDEFAULT\s+('(?:[^']|'')*'|b'[01]+'|\([^)]*\)|[\w.-]+)", definition, re.I)
        if not standard:
            continue
        wert = standard.group(1)
        # Literale direkt, alles andere (CURRENT_TIMESTAMP ...) als Ausdruck
        if not re.fullmatch(r"'(?:[^']|'')*'|b'[01]+'|-?\d+(\.\d+)?|TRUE|FALSE|NULL|\(.*\)", wert, re.I):
            wert = f"({wert})"
        print(f"ALTER TABLE `{tabelle}` ALTER COLUMN `{spalte}` SET DEFAULT {wert};")


if __name__ == "__main__":
    main()
