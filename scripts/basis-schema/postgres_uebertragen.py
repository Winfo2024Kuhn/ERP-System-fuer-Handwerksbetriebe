#!/usr/bin/env python3
"""Uebertraegt, was Hibernate beim PostgreSQL-Schema nicht mitliefert, aus der
MySQL-Basis: Indizes/Eindeutigkeiten, Spalten-Defaults, Stammdaten - und gleicht
die Regeln an MySQL an (Fremdschluessel mit Loeschregeln, CHECKs, NOT NULL,
Textlaengen). Massstab ist der eigene MySQL-Server: Dort ist der Code erprobt,
PostgreSQL darf sich nicht strenger oder anders verhalten.

Aufgerufen von postgres_erzeugen.sh. Voraussetzung:
  - MySQL mit eingespielter MySQL-Basis (db/basis/V1__basis_schema.sql)
  - PostgreSQL mit dem Hibernate-Schema der App (ddl-auto=create), Tabellen leer

Spalten und Typen kommen bewusst aus Hibernate: dann passen sie per Definition
zur App (ddl-auto=validate). Aus MySQL kommt nur, was Hibernate nicht kennt.

Aufruf: MYSQL_PW=... PG_PW=... postgres_uebertragen.py <mysql-port> <pg-port>
        (Passwoerter per Umgebung, nicht als Argument - sonst stuenden sie in der Prozessliste)
Ausgabe: Protokoll auf stdout; Fehler beenden mit Exit 1.
"""
import os
import re
import sys

import psycopg
import pymysql

DB = "kalkulationsprogramm_db"
# Nur in MySQL vorhanden und von der App nicht genutzt
UEBERSPRINGEN = {"flyway_schema_history", "artikel_bereinigung_backup", "lieferanten_artikel_preise_korrektur_v360"}


def log(text):
    print(f"[postgres-basis] {text}", flush=True)


def pg_index_name(tabelle, index):
    """PostgreSQL-Indexnamen gelten im ganzen Schema (MySQL: je Tabelle)."""
    return f"{tabelle}__{index}"[:63]


def indizes_uebertragen(my, pg):
    with my.cursor() as c:
        c.execute("""
            SELECT table_name, index_name, non_unique, index_type, sub_part, column_name
            FROM information_schema.statistics
            WHERE table_schema = %s AND index_name <> 'PRIMARY'
            ORDER BY table_name, index_name, seq_in_index""", (DB,))
        zeilen = c.fetchall()
    indizes = {}
    for tabelle, index, non_unique, typ, sub_part, spalte in zeilen:
        if tabelle in UEBERSPRINGEN:
            continue
        eintrag = indizes.setdefault((tabelle, index), {"unique": non_unique == 0, "typ": typ, "praefix": False, "spalten": []})
        eintrag["spalten"].append(spalte)
        eintrag["praefix"] |= sub_part is not None

    with pg.cursor() as c:
        c.execute("""
            SELECT t.relname, ix.indisunique, array_agg(a.attname ORDER BY k.ord)
            FROM pg_index ix
            JOIN pg_class t ON t.oid = ix.indrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace AND n.nspname = 'public'
            JOIN LATERAL unnest(ix.indkey) WITH ORDINALITY AS k(attnum, ord) ON true
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
            GROUP BY ix.indexrelid, t.relname, ix.indisunique""")
        vorhanden = {(t, tuple(sp)) : u for t, u, sp in c.fetchall()}
        c.execute("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'public'")
        pg_spalten = {}
        for t, sp in c.fetchall():
            pg_spalten.setdefault(t, set()).add(sp)

    angelegt = uebersprungen = 0
    for (tabelle, index), info in sorted(indizes.items()):
        spalten = tuple(info["spalten"])
        if info["typ"] != "BTREE" or info["praefix"]:
            warnung = "  WARNUNG: Eindeutigkeit fehlt in PostgreSQL!" if info["unique"] else ""
            log(f"  Index {tabelle}.{index} uebersprungen ({info['typ']}{', Praefix' if info['praefix'] else ''}){warnung}")
            uebersprungen += 1
            continue
        if tabelle not in pg_spalten or not set(spalten) <= pg_spalten[tabelle]:
            uebersprungen += 1
            continue
        schon_da = vorhanden.get((tabelle, spalten))
        if schon_da is not None and (schon_da or not info["unique"]):
            continue  # gleicher oder strengerer Index existiert schon
        spaltenliste = ", ".join(f'"{s}"' for s in spalten)
        with pg.cursor() as c:
            c.execute(f'CREATE {"UNIQUE " if info["unique"] else ""}INDEX IF NOT EXISTS "{pg_index_name(tabelle, index)}" '
                      f'ON "{tabelle}" ({spaltenliste})')
        angelegt += 1
    pg.commit()
    log(f"Indizes: {angelegt} angelegt, {uebersprungen} uebersprungen")


ZAHL = re.compile(r"-?\d+(\.\d+)?")


def default_pg(wert, extra, pg_typ):
    """MySQL-Default (information_schema) -> PostgreSQL-Ausdruck, sonst None."""
    if wert is None:
        return None
    if "DEFAULT_GENERATED" in (extra or "") or wert.upper().startswith("CURRENT_TIMESTAMP"):
        return "CURRENT_TIMESTAMP" if wert.upper().startswith("CURRENT_TIMESTAMP") else None
    if pg_typ == "boolean":
        return "TRUE" if wert.strip("b'") in ("1", "true", "TRUE") else "FALSE"
    if pg_typ in ("smallint", "integer", "bigint", "numeric", "real", "double precision"):
        return wert if ZAHL.fullmatch(wert) else None
    return "'" + wert.replace("'", "''") + "'"


def defaults_uebertragen(my, pg):
    with my.cursor() as c:
        c.execute("SELECT table_name, column_name, column_default, extra FROM information_schema.columns "
                  "WHERE table_schema = %s AND column_default IS NOT NULL", (DB,))
        mysql_defaults = c.fetchall()
    with pg.cursor() as c:
        c.execute("SELECT table_name, column_name, data_type, column_default FROM information_schema.columns "
                  "WHERE table_schema = 'public'")
        pg_spalten = {(t, s): (typ, d) for t, s, typ, d in c.fetchall()}
    gesetzt = 0
    for tabelle, spalte, wert, extra in mysql_defaults:
        if tabelle in UEBERSPRINGEN or (tabelle, spalte) not in pg_spalten:
            continue
        pg_typ, pg_default = pg_spalten[(tabelle, spalte)]
        if pg_default is not None:
            continue  # Hibernate/Identity hat schon einen
        ausdruck = default_pg(wert, extra, pg_typ)
        if ausdruck is None:
            continue
        with pg.cursor() as c:
            c.execute(f'ALTER TABLE "{tabelle}" ALTER COLUMN "{spalte}" SET DEFAULT {ausdruck}')
        gesetzt += 1
    pg.commit()
    log(f"Defaults: {gesetzt} gesetzt")


def wert_pg(wert, pg_typ):
    if wert is None:
        return None
    if pg_typ == "boolean":
        if isinstance(wert, (bytes, bytearray)):
            return any(wert)
        return bool(int(wert)) if not isinstance(wert, bool) else wert
    return wert


def stammdaten_uebertragen(my, pg):
    with my.cursor() as c:
        c.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = %s AND table_type = 'BASE TABLE'", (DB,))
        tabellen = [t for (t,) in c.fetchall() if t not in UEBERSPRINGEN and not t.endswith("_seq")]
    with pg.cursor() as c:
        c.execute("SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema = 'public'")
        pg_spalten = {}
        for t, s, typ in c.fetchall():
            pg_spalten.setdefault(t, {})[s] = typ
    gesamt = 0
    # Tabellen kommen alphabetisch, nicht in Fremdschluessel-Reihenfolge. Die
    # Daten stammen aus einer konsistenten MySQL-Datenbank - die Pruefung darf
    # fuers Kopieren pausieren (nur in dieser Sitzung; Docker-Nutzer ist Superuser).
    with pg.cursor() as c:
        c.execute("SET session_replication_role = replica")
    for tabelle in sorted(tabellen):
        if tabelle not in pg_spalten:
            continue
        with my.cursor() as c:
            c.execute(f"SELECT * FROM `{tabelle}`")
            zeilen = c.fetchall()
            spalten = [d[0] for d in c.description]
        if not zeilen:
            continue
        gemeinsam = [i for i, s in enumerate(spalten) if s in pg_spalten[tabelle]]
        fehlend = [s for s in spalten if s not in pg_spalten[tabelle]]
        if fehlend:
            log(f"  {tabelle}: Spalten nur in MySQL, nicht uebernommen: {', '.join(fehlend)}")
        namen = ", ".join(f'"{spalten[i]}"' for i in gemeinsam)
        platzhalter = ", ".join(["%s"] * len(gemeinsam))
        with pg.cursor() as c:
            for zeile in zeilen:
                c.execute(f'INSERT INTO "{tabelle}" ({namen}) VALUES ({platzhalter})',
                          [wert_pg(zeile[i], pg_spalten[tabelle][spalten[i]]) for i in gemeinsam])
        log(f"  {tabelle}: {len(zeilen)} Zeilen")
        gesamt += len(zeilen)
    with pg.cursor() as c:
        c.execute("SET session_replication_role = DEFAULT")
    pg.commit()
    log(f"Stammdaten: {gesamt} Zeilen")


# MySQL information_schema -> PostgreSQL pg_constraint.confdeltype
LOESCH_REGEL = {"CASCADE": "c", "SET NULL": "n", "SET DEFAULT": "d", "RESTRICT": "a", "NO ACTION": "a"}
REGEL_SQL = {"c": "CASCADE", "n": "SET NULL", "d": "SET DEFAULT", "a": "NO ACTION"}


def pg_fremdschluessel(pg):
    """(tabelle, spalten, ziel, zielspalten) -> (name, loeschregel, aenderungsregel)"""
    with pg.cursor() as c:
        c.execute("""
            SELECT con.conname, t.relname, ziel.relname, con.confdeltype, con.confupdtype,
                   ARRAY(SELECT a.attname FROM unnest(con.conkey) WITH ORDINALITY k(n, i)
                         JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.n ORDER BY k.i),
                   ARRAY(SELECT a.attname FROM unnest(con.confkey) WITH ORDINALITY k(n, i)
                         JOIN pg_attribute a ON a.attrelid = con.confrelid AND a.attnum = k.n ORDER BY k.i)
            FROM pg_constraint con
            JOIN pg_class t ON t.oid = con.conrelid
            JOIN pg_class ziel ON ziel.oid = con.confrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace AND n.nspname = 'public'
            WHERE con.contype = 'f'""")
        return {(t, tuple(sp), z, tuple(zsp)): (name, d.replace("r", "a"), u.replace("r", "a"))
                for name, t, z, d, u, sp, zsp in c.fetchall()}


def fremdschluessel_angleichen(my, pg):
    """Fremdschluessel und ihre Loeschregeln (CASCADE / SET NULL) wie in MySQL.

    Hibernate legt Fremdschluessel ohne Loeschregel an und kennt manche gar
    nicht. Der Code ist aber auf MySQL erprobt: Ein Loeschen, das dort
    kaskadiert, muss es hier auch - sonst scheitert es nur bei Kunden."""
    with my.cursor() as c:
        c.execute("""
            SELECT k.table_name, k.constraint_name, k.column_name, k.referenced_table_name,
                   k.referenced_column_name, r.delete_rule, r.update_rule
            FROM information_schema.key_column_usage k
            JOIN information_schema.referential_constraints r
              ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
             AND r.table_name = k.table_name
            WHERE k.table_schema = %s AND k.referenced_table_name IS NOT NULL
            ORDER BY k.table_name, k.constraint_name, k.ordinal_position""", (DB,))
        zeilen = c.fetchall()
    je_name = {}
    for tabelle, name, spalte, ziel, zielspalte, loeschen, aendern in zeilen:
        if tabelle in UEBERSPRINGEN or ziel in UEBERSPRINGEN:
            continue
        eintrag = je_name.setdefault((tabelle, name), {"name": name, "ziel": ziel, "spalten": [], "zielspalten": [],
                                                       "loeschen": LOESCH_REGEL[loeschen], "aendern": LOESCH_REGEL[aendern]})
        eintrag["spalten"].append(spalte)
        eintrag["zielspalten"].append(zielspalte)
    # Manche Spalten tragen in MySQL zwei Fremdschluessel aufs selbe Ziel (einen
    # von Hibernate ohne Regel, einen aus einer Migration mit SET NULL/CASCADE).
    # MySQL laesst dann der strengeren den Vortritt: Das Loeschen wird blockiert.
    # Genau dieses Verhalten bekommt PostgreSQL - ein einziger Fremdschluessel.
    mysql_fks = {}
    for (tabelle, _), fk in sorted(je_name.items()):
        schluessel = (tabelle, tuple(fk["spalten"]), fk["ziel"], tuple(fk["zielspalten"]))
        bisher = mysql_fks.get(schluessel)
        if bisher is None:
            mysql_fks[schluessel] = fk
        elif "a" in (bisher["loeschen"], fk["loeschen"]):
            log(f"  {tabelle}{schluessel[1]}: zwei Fremdschluessel in MySQL - der ohne Loeschregel blockiert")
            bisher["loeschen"] = "a"
        elif bisher["loeschen"] != fk["loeschen"]:
            # z. B. CASCADE und SET NULL zugleich - welche gilt, ist dann unklar
            raise SystemExit(f"{tabelle}{schluessel[1]}: zwei Fremdschluessel mit verschiedenen Loeschregeln "
                             f"({bisher['name']}, {fk['name']}) - erst in MySQL bereinigen")

    with pg.cursor() as c:
        c.execute("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'public'")
        pg_spalten = {}
        for t, sp in c.fetchall():
            pg_spalten.setdefault(t, set()).add(sp)
    vorhanden = pg_fremdschluessel(pg)
    gewollt = set()
    angelegt = geaendert = 0
    with pg.cursor() as c:
        for schluessel, fk in sorted(mysql_fks.items()):
            tabelle, name = schluessel[0], fk["name"]
            if not (set(fk["spalten"]) <= pg_spalten.get(tabelle, set())
                    and set(fk["zielspalten"]) <= pg_spalten.get(fk["ziel"], set())):
                log(f"  Fremdschluessel {tabelle}.{name} uebersprungen (Spalten fehlen in PostgreSQL)")
                continue
            gewollt.add(schluessel)
            alt = vorhanden.get(schluessel)
            if alt is not None and alt[1:] == (fk["loeschen"], fk["aendern"]):
                continue
            if alt is not None:
                c.execute(f'ALTER TABLE "{tabelle}" DROP CONSTRAINT "{alt[0]}"')
                geaendert += 1
            else:
                angelegt += 1
            spalten = ", ".join(f'"{s}"' for s in fk["spalten"])
            zielspalten = ", ".join(f'"{s}"' for s in fk["zielspalten"])
            c.execute(f'ALTER TABLE "{tabelle}" ADD CONSTRAINT "{name[:63]}" FOREIGN KEY ({spalten}) '
                      f'REFERENCES "{fk["ziel"]}" ({zielspalten}) '
                      f'ON DELETE {REGEL_SQL[fk["loeschen"]]} ON UPDATE {REGEL_SQL[fk["aendern"]]}')
        # Was MySQL nicht hat, wuerde nur bei Kunden Loeschungen blockieren
        entfernt = 0
        for schluessel, (name, _, _) in vorhanden.items():
            if schluessel not in gewollt:
                c.execute(f'ALTER TABLE "{schluessel[0]}" DROP CONSTRAINT "{name}"')
                entfernt += 1
    pg.commit()
    log(f"Fremdschluessel: {angelegt} angelegt, {geaendert} Loeschregel angepasst, {entfernt} ohne MySQL-Gegenstueck entfernt")


def checks_angleichen(my, pg):
    """CHECK-Regeln wie in MySQL.

    Hibernate legt fuer jedes Java-Enum einen CHECK mit der Werteliste an. Kommt
    spaeter ein Wert dazu, faellt ein vergessener CHECK erst beim Speichern auf -
    und nur bei Kunden. MySQL hat diese CHECKs nicht; die gueltigen Werte sichert
    das JPA-Mapping. Uebernommen werden nur die echten Regeln aus MySQL."""
    with pg.cursor() as c:
        c.execute("""
            SELECT t.relname, con.conname FROM pg_constraint con
            JOIN pg_class t ON t.oid = con.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace AND n.nspname = 'public'
            WHERE con.contype = 'c'""")
        for tabelle, name in c.fetchall():
            c.execute(f'ALTER TABLE "{tabelle}" DROP CONSTRAINT "{name}"')
        c.execute("SELECT table_name, column_name FROM information_schema.columns "
                  "WHERE table_schema = 'public' AND data_type = 'boolean'")
        boolesch = set(c.fetchall())
    with my.cursor() as c:
        c.execute("""
            SELECT tc.table_name, cc.constraint_name, cc.check_clause
            FROM information_schema.check_constraints cc
            JOIN information_schema.table_constraints tc
              ON tc.constraint_schema = cc.constraint_schema AND tc.constraint_name = cc.constraint_name
            WHERE cc.constraint_schema = %s AND tc.constraint_type = 'CHECK'""", (DB,))
        regeln = c.fetchall()
    with pg.cursor() as c:
        for tabelle, name, klausel in regeln:
            if tabelle in UEBERSPRINGEN:
                continue
            ausdruck = klausel.replace("_utf8mb4'", "'").replace("`", '"')
            # BIT/TINYINT-Vergleiche (= 1) auf PostgreSQL-boolean
            ausdruck = re.sub(r'"(\w+)" = ([01])\b', lambda m: f'"{m.group(1)}" = '
                              + (("true" if m.group(2) == "1" else "false") if (tabelle, m.group(1)) in boolesch
                                 else m.group(2)), ausdruck)
            c.execute(f'ALTER TABLE "{tabelle}" ADD CONSTRAINT "{name[:63]}" CHECK ({ausdruck})')
    pg.commit()
    log(f"CHECK-Regeln: Hibernate-Enum-CHECKs entfernt, {len(regeln)} aus MySQL uebernommen")


def spalten_angleichen(my, pg):
    """NOT NULL und Textlaengen wie in MySQL.

    Hibernate kennt nur, was in den Entities steht; MySQL hat ueber die
    Migrationen manches gelockert (NULL erlaubt) oder verlaengert (VARCHAR(1000),
    TEXT). Strenger als MySQL darf PostgreSQL nicht sein, sonst scheitert ein
    Speichern nur bei Kunden. Laengere Texte als in MySQL bleiben erlaubt."""
    with my.cursor() as c:
        c.execute("""
            SELECT table_name, column_name, is_nullable, data_type, character_maximum_length
            FROM information_schema.columns WHERE table_schema = %s""", (DB,))
        mysql_spalten = {(t, s): (n == "YES", typ, laenge) for t, s, n, typ, laenge in c.fetchall()}
    with pg.cursor() as c:
        c.execute("""
            SELECT c.table_name, c.column_name, c.is_nullable, c.data_type, c.character_maximum_length,
                   EXISTS (SELECT 1 FROM information_schema.key_column_usage k
                           JOIN information_schema.table_constraints tc
                             ON tc.constraint_name = k.constraint_name AND tc.table_name = k.table_name
                            AND tc.constraint_type = 'PRIMARY KEY'
                           WHERE k.table_name = c.table_name AND k.column_name = c.column_name
                             AND k.table_schema = 'public')
            FROM information_schema.columns c WHERE c.table_schema = 'public'""")
        pg_spalten = c.fetchall()
    gelockert = verschaerft = verlaengert = 0
    with pg.cursor() as c:
        for tabelle, spalte, nullbar, typ, laenge, primaer in pg_spalten:
            mysql = mysql_spalten.get((tabelle, spalte))
            if mysql is None or tabelle in UEBERSPRINGEN:
                continue
            mysql_nullbar, mysql_typ, mysql_laenge = mysql
            if not primaer and mysql_nullbar != (nullbar == "YES"):
                c.execute(f'ALTER TABLE "{tabelle}" ALTER COLUMN "{spalte}" '
                          + ("DROP NOT NULL" if mysql_nullbar else "SET NOT NULL"))
                if mysql_nullbar:
                    gelockert += 1
                else:
                    verschaerft += 1
            if typ == "character varying" and laenge is not None:
                if mysql_typ in ("text", "mediumtext", "longtext"):
                    c.execute(f'ALTER TABLE "{tabelle}" ALTER COLUMN "{spalte}" TYPE text')
                    verlaengert += 1
                elif mysql_typ in ("varchar", "char") and mysql_laenge and mysql_laenge > laenge:
                    c.execute(f'ALTER TABLE "{tabelle}" ALTER COLUMN "{spalte}" TYPE varchar({int(mysql_laenge)})')
                    verlaengert += 1
    pg.commit()
    log(f"Spalten: {gelockert}x NULL erlaubt, {verschaerft}x NOT NULL, {verlaengert}x Text verlaengert (wie MySQL)")


def sequenzen_nachziehen(pg):
    """Nach dem Einfuegen mit festen IDs: Identity/Sequenz hinter die groesste ID setzen."""
    with pg.cursor() as c:
        c.execute("""
            SELECT table_name FROM information_schema.columns
            WHERE table_schema = 'public' AND column_name = 'id'""")
        tabellen = [t for (t,) in c.fetchall()]
        c.execute("SELECT sequencename FROM pg_sequences WHERE schemaname = 'public'")
        sequenzen = {s for (s,) in c.fetchall()}
        for tabelle in tabellen:
            c.execute(f'SELECT max(id) FROM "{tabelle}"')
            hoechste = c.fetchone()[0]
            if hoechste is None:
                continue
            c.execute("SELECT pg_get_serial_sequence(%s, 'id')", (f'public."{tabelle}"',))
            sequenz = c.fetchone()[0]
            if sequenz is None and f"{tabelle}_seq" in sequenzen:
                sequenz = f"public.{tabelle}_seq"
            if sequenz is not None:
                c.execute("SELECT setval(%s, %s)", (sequenz, hoechste))
    pg.commit()
    log("Sequenzen nachgezogen")


def main():
    mysql_port, pg_port = sys.argv[1:3]
    my = pymysql.connect(host="127.0.0.1", port=int(mysql_port), user="root", password=os.environ["MYSQL_PW"], database=DB)
    pg = psycopg.connect(host="127.0.0.1", port=int(pg_port), user="erp_user", password=os.environ["PG_PW"], dbname=DB)
    try:
        indizes_uebertragen(my, pg)
        defaults_uebertragen(my, pg)
        stammdaten_uebertragen(my, pg)
        fremdschluessel_angleichen(my, pg)
        checks_angleichen(my, pg)
        spalten_angleichen(my, pg)
        sequenzen_nachziehen(pg)
    finally:
        my.close()
        pg.close()


if __name__ == "__main__":
    main()
