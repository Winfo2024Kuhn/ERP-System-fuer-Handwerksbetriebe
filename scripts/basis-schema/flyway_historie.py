#!/usr/bin/env python3
"""Erzeugt die Flyway-Historie fuer das Basis-Schema.

Schreibt SQL auf stdout: CREATE TABLE flyway_schema_history (exakt wie Flyway
9 sie auf MySQL anlegt) und je Migrationsdatei eine Zeile "erfolgreich
ausgefuehrt" - mit derselben Checksumme, die Flyway selbst berechnen wuerde.
Damit sieht Flyway eine aus dem Basis-Schema entstandene Datenbank genau wie
eine, die alle Migrationen einzeln durchlaufen hat: Neue Migrationen laufen
danach normal, auch solche mit kleinerer Nummer (out-of-order).

Aufruf: flyway_historie.py <ordner-mit-V*.sql> [--postgres]
"""
import re
import struct
import sys
import zlib
from pathlib import Path

DATEINAME = re.compile(r"^V(\d+)__(.+)\.sql$")


def flyway_checksumme(pfad: Path) -> int:
    """Nachbau von Flyways ChecksumCalculator: CRC32 ueber alle Zeilen ohne
    Zeilenumbrueche, BOM entfernt, als vorzeichenbehafteter 32-Bit-Wert."""
    text = pfad.read_bytes().decode("utf-8")
    if text.startswith("﻿"):
        text = text[1:]
    crc = 0
    for zeile in re.split(r"\r\n|\r|\n", text):
        crc = zlib.crc32(zeile.encode("utf-8"), crc)
    return struct.unpack("i", struct.pack("I", crc & 0xFFFFFFFF))[0]


def sql_text(wert: str, postgres: bool = False) -> str:
    # PostgreSQL kennt (standardkonform) keinen Backslash als Escape in '...'
    if not postgres:
        wert = wert.replace("\\", "\\\\")
    return "'" + wert.replace("'", "''") + "'"


MYSQL_TABELLE = """CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;"""

# So legt Flyway 9 die Tabelle auf PostgreSQL an
POSTGRES_TABELLE = """CREATE TABLE "flyway_schema_history" (
    "installed_rank" INT NOT NULL,
    "version" VARCHAR(50),
    "description" VARCHAR(200) NOT NULL,
    "type" VARCHAR(20) NOT NULL,
    "script" VARCHAR(1000) NOT NULL,
    "checksum" INT,
    "installed_by" VARCHAR(100) NOT NULL,
    "installed_on" TIMESTAMP NOT NULL DEFAULT now(),
    "execution_time" INT NOT NULL,
    "success" BOOLEAN NOT NULL,
    CONSTRAINT "flyway_schema_history_pk" PRIMARY KEY ("installed_rank")
);
CREATE INDEX "flyway_schema_history_s_idx" ON "flyway_schema_history" ("success");"""


def main() -> None:
    ordner = Path(sys.argv[1])
    postgres = "--postgres" in sys.argv[2:]
    q = '"' if postgres else '`'
    erfolg = "true" if postgres else "1"
    migrationen = []
    for pfad in ordner.glob("V*.sql"):
        treffer = DATEINAME.match(pfad.name)
        if treffer:
            migrationen.append((int(treffer.group(1)), treffer.group(2), pfad))
    migrationen.sort()

    print(POSTGRES_TABELLE if postgres else MYSQL_TABELLE)
    for rang, (version, beschreibung, pfad) in enumerate(migrationen, start=1):
        spalten = ", ".join(f"{q}{s}{q}" for s in (
            "installed_rank", "version", "description", "type", "script", "checksum",
            "installed_by", "execution_time", "success"))
        print(
            f"INSERT INTO {q}flyway_schema_history{q} ({spalten}) VALUES "
            f"({rang}, '{version}', {sql_text(beschreibung.replace('_', ' '), postgres)}, 'SQL', "
            f"{sql_text(pfad.name, postgres)}, {flyway_checksumme(pfad)}, 'basis-schema', 0, {erfolg});"
        )


if __name__ == "__main__":
    main()
