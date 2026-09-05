-- datensatz_lock.entitaet_typ: VARCHAR(32) -> natives ENUM
--
-- Symptom: Die Anwendung startet nach dem Deploy von V363 nicht mehr. Hibernate
-- bricht die Schemapruefung (ddl-auto=validate) ab:
--
--   Schema-validation: wrong column type encountered in column [entitaet_typ]
--   in table [datensatz_lock]; found [varchar (Types#VARCHAR)],
--   but expecting [enum ('ausgang','eingang') (Types#ENUM)]
--
-- Ursache: Hibernate 6.x mappt @Enumerated(EnumType.STRING) mit MySQL-Dialekt
-- auf einen nativen ENUM-Spaltentyp. V363 hat entitaet_typ bewusst als
-- VARCHAR(32) angelegt und sich dabei auf
--
--   spring.jpa.properties.hibernate.type.preferred_enum_jdbc_type=VARCHAR
--
-- berufen. Diese Property gibt es in Hibernate 6.4.4 -- der hier verwendeten
-- Version -- noch gar nicht; eingefuehrt wurde sie erst in 6.5. Hibernate
-- verwirft unbekannte hibernate.*-Keys stillschweigend, die Einstellung war
-- also von Anfang an wirkungslos.
--
-- Warum das bisher niemandem auffiel: Alle aelteren Enum-Spalten stammen noch
-- aus der Zeit von ddl-auto=update und wurden damit von Hibernate selbst als
-- native ENUMs angelegt -- sie validieren. Erst eine per Flyway von Hand
-- angelegte Enum-Spalte faellt auf die Nase. Genau dieser Fall ist schon einmal
-- aufgetreten: V289 legte steuerberater_ansprechpartner.anrede als VARCHAR an,
-- V291 musste die Spalte nachtraeglich auf ENUM ziehen. Die Regel steht seither
-- in docs/agent instructions/docs/BACKEND_ARCH.md.
--
-- Werte exakt wie die Java-Konstanten in SperrbarerTyp (UPPERCASE).
--
-- Kein Datenverlust: Lock-Eintraege sind reine Laufzeit-Zustaende ohne
-- fachlichen Wert. Schlimmstenfalls muss einmal neu auf "Bearbeiten" geklickt
-- werden.
--
-- Idempotent: Das DELETE trifft im Normalfall nichts (die Werte kommen
-- ausschliesslich aus dem Java-Enum) und raeumt nur den Fall ab, dass eine
-- Altzeile einen Wert traegt, den das ENUM nicht kennt -- sonst wuerde das
-- ALTER im strict mode scheitern. MODIFY COLUMN auf den bereits gesetzten Typ
-- ist ein No-Op, die Migration ist damit wiederholbar.

DELETE FROM datensatz_lock WHERE entitaet_typ NOT IN ('AUSGANG', 'EINGANG');

ALTER TABLE datensatz_lock
    MODIFY COLUMN entitaet_typ ENUM('AUSGANG', 'EINGANG') NOT NULL;
