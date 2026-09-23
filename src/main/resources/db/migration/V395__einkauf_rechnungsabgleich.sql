CREATE TABLE IF NOT EXISTS einkauf_beleg_zuordnung (
  id BIGINT NOT NULL AUTO_INCREMENT,
  dokument_id BIGINT NOT NULL,
  bestellung_id BIGINT NOT NULL,
  art VARCHAR(24) NOT NULL,
  bezugs_dokument_id BIGINT NULL,
  idempotenz_key VARCHAR(36) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  akteur_id BIGINT NOT NULL,
  zugeordnet_am TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id), UNIQUE KEY uk_beleg_zuordnung_idempotenz (idempotenz_key),
  UNIQUE KEY uk_beleg_zuordnung_dokument (dokument_id), KEY ix_beleg_zuordnung_bestellung (bestellung_id),
  CONSTRAINT fk_beleg_zuordnung_dokument FOREIGN KEY (dokument_id) REFERENCES lieferant_dokument(id),
  CONSTRAINT fk_beleg_zuordnung_bestellung FOREIGN KEY (bestellung_id) REFERENCES einkauf_bestellung(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS einkauf_beleg_position (
  id BIGINT NOT NULL AUTO_INCREMENT,
  dokument_id BIGINT NOT NULL,
  bestell_position_id BIGINT NULL,
  original_positionsnummer VARCHAR(100) NOT NULL,
  menge DECIMAL(19,6) NULL,
  einheit ENUM('STUECK','METER','KILOGRAMM','TONNE','QUADRATMETER') NULL,
  original_menge DECIMAL(19,6) NULL,
  original_einheit ENUM('STUECK','METER','KILOGRAMM','TONNE','QUADRATMETER') NULL,
  einzelpreis DECIMAL(19,6) NULL,
  original_einzelpreis DECIMAL(19,6) NULL,
  preis_basis_menge DECIMAL(19,6) NULL,
  nur_preis_korrektur BOOLEAN NOT NULL DEFAULT FALSE,
  waehrung CHAR(3) NULL,
  kosten_snapshot JSON NOT NULL,
  quellen_snapshot JSON NOT NULL,
  pruefen BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id), UNIQUE KEY uk_beleg_position_original (dokument_id, original_positionsnummer),
  KEY ix_beleg_position_bestellposition (bestell_position_id),
  CONSTRAINT fk_beleg_position_dokument FOREIGN KEY (dokument_id) REFERENCES lieferant_dokument(id),
  CONSTRAINT fk_beleg_position_bestellposition FOREIGN KEY (bestell_position_id) REFERENCES einkauf_bestellung_position(id)
) ENGINE=InnoDB;

SET @einkauf_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lieferant_reklamation' AND column_name='bestellung_id')=0,
  'ALTER TABLE lieferant_reklamation ADD COLUMN bestellung_id BIGINT NULL','SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl; EXECUTE einkauf_stmt; DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lieferant_reklamation' AND column_name='bestell_position_id')=0,
  'ALTER TABLE lieferant_reklamation ADD COLUMN bestell_position_id BIGINT NULL','SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl; EXECUTE einkauf_stmt; DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lieferant_reklamation' AND column_name='rechnung_id')=0,
  'ALTER TABLE lieferant_reklamation ADD COLUMN rechnung_id BIGINT NULL','SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl; EXECUTE einkauf_stmt; DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF((SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_reklamation_bestellung')=0,
  'ALTER TABLE lieferant_reklamation ADD CONSTRAINT fk_reklamation_bestellung FOREIGN KEY(bestellung_id) REFERENCES einkauf_bestellung(id)','SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl; EXECUTE einkauf_stmt; DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF((SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_reklamation_bestellposition')=0,
  'ALTER TABLE lieferant_reklamation ADD CONSTRAINT fk_reklamation_bestellposition FOREIGN KEY(bestell_position_id) REFERENCES einkauf_bestellung_position(id)','SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl; EXECUTE einkauf_stmt; DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF((SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_reklamation_rechnung')=0,
  'ALTER TABLE lieferant_reklamation ADD CONSTRAINT fk_reklamation_rechnung FOREIGN KEY(rechnung_id) REFERENCES lieferant_dokument(id)','SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl; EXECUTE einkauf_stmt; DEALLOCATE PREPARE einkauf_stmt;
