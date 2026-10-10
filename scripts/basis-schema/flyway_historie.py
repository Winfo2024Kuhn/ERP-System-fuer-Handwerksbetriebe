#!/usr/bin/env python3
"""Erzeugt die Flyway-Historie fuer das Basis-Schema.

Schreibt SQL auf stdout: CREATE TABLE flyway_schema_history (exakt wie Flyway
9 sie auf MySQL anlegt) und je Migrationsdatei eine Zeile "erfolgreich
ausgefuehrt" - mit derselben Checksumme, die Flyway selbst berechnen wuerde.
Damit sieht Flyway eine aus dem Basis-Schema entstandene Datenbank genau wie
eine, die alle Migrationen einzeln durchlaufen hat: Neue Migrationen laufen
danach normal, auch solche mit kleinerer Nummer (out-of-order).

Aufruf: flyway_historie.py <ordner-mit-V*.sql>
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


def sql_text(wert: str) -> str:
    return "'" + wert.replace("\\", "\\\\").replace("'", "''") + "'"


def main() -> None:
    ordner = Path(sys.argv[1])
    migrationen = []
    for pfad in ordner.glob("V*.sql"):
        treffer = DATEINAME.match(pfad.name)
        if treffer:
            migrationen.append((int(treffer.group(1)), treffer.group(2), pfad))
    migrationen.sort()

    print("""CREATE TABLE `flyway_schema_history` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;""")
    for rang, (version, beschreibung, pfad) in enumerate(migrationen, start=1):
        print(
            "INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, "
            "`script`, `checksum`, `installed_by`, `execution_time`, `success`) VALUES "
            f"({rang}, '{version}', {sql_text(beschreibung.replace('_', ' '))}, 'SQL', "
            f"{sql_text(pfad.name)}, {flyway_checksumme(pfad)}, 'basis-schema', 0, 1);"
        )


if __name__ == "__main__":
    main()
