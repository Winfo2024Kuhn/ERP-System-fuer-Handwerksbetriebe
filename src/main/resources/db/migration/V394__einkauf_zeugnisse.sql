CREATE TABLE IF NOT EXISTS einkauf_anforderungsvorlage (
  id BIGINT NOT NULL AUTO_INCREMENT,
  artikel_id BIGINT NULL,
  projekt_id BIGINT NULL,
  art ENUM('ZEUGNIS_2_1','ZEUGNIS_2_2','ZEUGNIS_3_1','ZEUGNIS_3_2','LEISTUNGSERKLAERUNG','CE_NACHWEIS') NOT NULL,
  grundlage VARCHAR(1000) NOT NULL,
  versionsnummer INT NOT NULL,
  bestaetigt_von BIGINT NOT NULL,
  bestaetigt_am TIMESTAMP(6) NOT NULL,
  aktiv BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  KEY ix_anforderung_artikel (artikel_id, art, aktiv),
  KEY ix_anforderung_projekt (projekt_id, art, aktiv)
);

CREATE TABLE IF NOT EXISTS einkauf_zeugnis_erwartung (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version BIGINT NOT NULL DEFAULT 0,
  revision_id BIGINT NOT NULL,
  bestell_position_id BIGINT NOT NULL,
  anforderungs_index INT NOT NULL,
  art ENUM('ZEUGNIS_2_1','ZEUGNIS_2_2','ZEUGNIS_3_1','ZEUGNIS_3_2','LEISTUNGSERKLAERUNG','CE_NACHWEIS') NOT NULL,
  grundlage VARCHAR(1000) NOT NULL,
  grundlage_version VARCHAR(120) NOT NULL,
  frist DATE NULL,
  status ENUM('ANGEFORDERT','ERWARTET','EINGEGANGEN','ZUGEORDNET','GEPRUEFT','KLAERUNG_NOETIG') NOT NULL,
  material_freigegeben BOOLEAN NOT NULL DEFAULT FALSE,
  eingegangen_am TIMESTAMP(6) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_zeugnis_revision_position_soll UNIQUE (revision_id, bestell_position_id, anforderungs_index),
  KEY ix_zeugnis_bestellposition (bestell_position_id),
  KEY ix_zeugnis_faelligkeit (status, frist),
  CONSTRAINT fk_zeugnis_revision FOREIGN KEY (revision_id) REFERENCES einkauf_bestellung_revision(id),
  CONSTRAINT fk_zeugnis_position FOREIGN KEY (bestell_position_id) REFERENCES einkauf_bestellung_position(id)
);

CREATE TABLE IF NOT EXISTS einkauf_zeugnis_datei (
  zeugnis_id BIGINT NOT NULL,
  datei_id BIGINT NOT NULL,
  PRIMARY KEY (zeugnis_id, datei_id),
  CONSTRAINT fk_zeugnis_datei_zeugnis FOREIGN KEY (zeugnis_id) REFERENCES einkauf_zeugnis_erwartung(id),
  CONSTRAINT fk_zeugnis_datei_datei FOREIGN KEY (datei_id) REFERENCES einkauf_datei(id)
);

CREATE TABLE IF NOT EXISTS einkauf_zeugnis_lieferposition (
  zeugnis_id BIGINT NOT NULL,
  lieferposition_id BIGINT NOT NULL,
  PRIMARY KEY (zeugnis_id, lieferposition_id),
  CONSTRAINT fk_zeugnis_liefer_zeugnis FOREIGN KEY (zeugnis_id) REFERENCES einkauf_zeugnis_erwartung(id),
  CONSTRAINT fk_zeugnis_liefer_position FOREIGN KEY (lieferposition_id) REFERENCES einkauf_lieferung_position(id)
);

CREATE TABLE IF NOT EXISTS einkauf_zeugnis_charge (
  zeugnis_id BIGINT NOT NULL,
  charge_id BIGINT NOT NULL,
  PRIMARY KEY (zeugnis_id, charge_id),
  CONSTRAINT fk_zeugnis_charge_zeugnis FOREIGN KEY (zeugnis_id) REFERENCES einkauf_zeugnis_erwartung(id),
  CONSTRAINT fk_zeugnis_charge_charge FOREIGN KEY (charge_id) REFERENCES einkauf_charge(id)
);

CREATE TABLE IF NOT EXISTS einkauf_zeugnis_zuordnung (
  id BIGINT NOT NULL AUTO_INCREMENT,
  zeugnis_id BIGINT NOT NULL,
  datei_id BIGINT NOT NULL,
  klaerung_noetig BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id),
  CONSTRAINT uk_zeugnis_zuordnung UNIQUE (zeugnis_id, datei_id),
  CONSTRAINT fk_zeugnis_zuordnung_zeugnis FOREIGN KEY (zeugnis_id) REFERENCES einkauf_zeugnis_erwartung(id),
  CONSTRAINT fk_zeugnis_zuordnung_datei FOREIGN KEY (datei_id) REFERENCES einkauf_datei(id)
);

CREATE TABLE IF NOT EXISTS einkauf_zeugnis_zuordnung_lieferposition (
  zuordnung_id BIGINT NOT NULL, lieferposition_id BIGINT NOT NULL,
  PRIMARY KEY (zuordnung_id, lieferposition_id),
  CONSTRAINT fk_zeugnis_zuordnung_liefer FOREIGN KEY (zuordnung_id) REFERENCES einkauf_zeugnis_zuordnung(id),
  CONSTRAINT fk_zeugnis_zuordnung_lieferposition FOREIGN KEY (lieferposition_id) REFERENCES einkauf_lieferung_position(id)
);

CREATE TABLE IF NOT EXISTS einkauf_zeugnis_zuordnung_charge (
  zuordnung_id BIGINT NOT NULL, charge_id BIGINT NOT NULL,
  PRIMARY KEY (zuordnung_id, charge_id),
  CONSTRAINT fk_zeugnis_zuordnung_charge FOREIGN KEY (zuordnung_id) REFERENCES einkauf_zeugnis_zuordnung(id),
  CONSTRAINT fk_zeugnis_zuordnung_charge_ref FOREIGN KEY (charge_id) REFERENCES einkauf_charge(id)
);

CREATE TABLE IF NOT EXISTS einkauf_dokument_pruefung (
  id BIGINT NOT NULL AUTO_INCREMENT,
  zeugnis_id BIGINT NOT NULL,
  akteur_id BIGINT NOT NULL,
  geprueft_am TIMESTAMP(6) NOT NULL,
  ergebnis VARCHAR(32) NOT NULL,
  begruendung VARCHAR(2000) NOT NULL,
  grundlage_version VARCHAR(120) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_zeugnis_pruefung_zeit (zeugnis_id, geprueft_am, id),
  CONSTRAINT fk_dokument_pruefung_zeugnis FOREIGN KEY (zeugnis_id) REFERENCES einkauf_zeugnis_erwartung(id)
);
