-- =============================================================================
-- Basis-Schema fuer NEUINSTALLATIONEN - nicht von Hand bearbeiten!
-- Erzeugt mit scripts/basis-schema/erzeugen.sh (Erklaerung dort).
-- Enthaelt: komplettes Schema, Stammdaten aus den Migrationen und die
-- Flyway-Historie bis V406.
-- Keine Kunden- oder Personendaten.
-- Wird nur auf einer LEEREN Datenbank ausgefuehrt (FlywayStartSetupConfig).
-- =============================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `abteilung` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `darf_freigabe_annahme_pushen` bit(1) NOT NULL DEFAULT b'1',
  `darf_monat_abschliessen` bit(1) NOT NULL DEFAULT b'0',
  `darf_rechnungen_genehmigen` bit(1) NOT NULL,
  `darf_rechnungen_sehen` bit(1) NOT NULL,
  `darf_telefon_sehen` bit(1) NOT NULL DEFAULT b'0',
  `darf_webseiten_anfragen_pushen` bit(1) NOT NULL DEFAULT b'1',
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_a4tna5dyyg2evk1sp9ylugwj0` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `abteilung_dokument_berechtigung` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `darf_scannen` bit(1) NOT NULL,
  `darf_sehen` bit(1) NOT NULL,
  `dokument_typ` varchar(50) NOT NULL,
  `abteilung_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKiavi6buc56lm9cca4pvgvoua` (`abteilung_id`,`dokument_typ`),
  CONSTRAINT `FK2f0otqqmju7v9cwa45hplwapm` FOREIGN KEY (`abteilung_id`) REFERENCES `abteilung` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `abwesenheit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `datum` date NOT NULL,
  `notiz` varchar(500) DEFAULT NULL,
  `stunden` decimal(10,2) NOT NULL,
  `typ` enum('URLAUB','KRANKHEIT','FORTBILDUNG','ZEITAUSGLEICH') NOT NULL,
  `langzeitkrankmeldung_id` bigint DEFAULT NULL,
  `langzeitkrankmeldung_phase_id` bigint DEFAULT NULL,
  `mitarbeiter_id` bigint NOT NULL,
  `urlaubsantrag_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_abwesenheit_mitarbeiter_datum_typ` (`mitarbeiter_id`,`datum`,`typ`),
  KEY `FKp1m4m9m370l0ekoohchtq9p95` (`langzeitkrankmeldung_id`),
  KEY `FKt0ubrnf7d1gpsymykqurc95ih` (`langzeitkrankmeldung_phase_id`),
  KEY `FKq2767pce39mwtcrdj3klfwx6d` (`urlaubsantrag_id`),
  CONSTRAINT `fk_abwesenheit_langzeitkrankmeldung` FOREIGN KEY (`langzeitkrankmeldung_id`) REFERENCES `langzeitkrankmeldung` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_abwesenheit_langzeitkrankmeldung_phase` FOREIGN KEY (`langzeitkrankmeldung_phase_id`) REFERENCES `langzeitkrankmeldung_phase` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FKp1m4m9m370l0ekoohchtq9p95` FOREIGN KEY (`langzeitkrankmeldung_id`) REFERENCES `langzeitkrankmeldung` (`id`),
  CONSTRAINT `FKq2767pce39mwtcrdj3klfwx6d` FOREIGN KEY (`urlaubsantrag_id`) REFERENCES `urlaubsantrag` (`id`),
  CONSTRAINT `FKq6lqpw7ebfgi26b3x0c6ihksy` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKt0ubrnf7d1gpsymykqurc95ih` FOREIGN KEY (`langzeitkrankmeldung_phase_id`) REFERENCES `langzeitkrankmeldung_phase` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `aenderungsgrund_katalog` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bezeichnung` varchar(255) NOT NULL,
  `code` varchar(50) NOT NULL,
  `erfordert_freitext` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_dltfql0mc2apxad2go3t7eews` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `anfrage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `abgeschlossen` bit(1) NOT NULL,
  `anlegedatum` date DEFAULT NULL,
  `bauvorhaben` varchar(255) DEFAULT NULL,
  `betrag` decimal(38,2) DEFAULT NULL,
  `bild_url` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `email_versand_datum` date DEFAULT NULL,
  `kurzbeschreibung` varchar(1000) DEFAULT NULL,
  `projekt_ort` varchar(255) DEFAULT NULL,
  `projekt_plz` varchar(255) DEFAULT NULL,
  `projekt_strasse` varchar(255) DEFAULT NULL,
  `version` bigint DEFAULT '0',
  `kunde_id` bigint DEFAULT NULL,
  `projekt_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7tcxpwd7j1238baugoddm2c6c` (`kunde_id`),
  KEY `FK74hhvjetu7xw9kbg0hnq4f94s` (`projekt_id`),
  CONSTRAINT `FK74hhvjetu7xw9kbg0hnq4f94s` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`),
  CONSTRAINT `FK7tcxpwd7j1238baugoddm2c6c` FOREIGN KEY (`kunde_id`) REFERENCES `kunde` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `anfrage_dokument` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dateigroesse` bigint DEFAULT NULL,
  `dateityp` varchar(255) DEFAULT NULL,
  `dokument_gruppe` enum('BILDER','GESCHAEFTSDOKUMENTE','PLANUNGSDOKUMENTE','KALKULATIONSDOKUMENTE','DOKUMENTATION_1090','EINGANGSRECHNUNGEN','DIVERSE_DOKUMENTE') NOT NULL,
  `email_versand_datum` date DEFAULT NULL,
  `gespeicherter_dateiname` varchar(255) NOT NULL,
  `original_dateiname` varchar(255) NOT NULL,
  `upload_datum` date DEFAULT NULL,
  `anfrage_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_f1qfxhufkj7ct5cvv75e7pnu6` (`gespeicherter_dateiname`),
  KEY `FKkakl5w3pite8krxx0qo896ov4` (`anfrage_id`),
  CONSTRAINT `FKkakl5w3pite8krxx0qo896ov4` FOREIGN KEY (`anfrage_id`) REFERENCES `anfrage` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `anfrage_geschaeftsdokument` (
  `brutto_betrag` decimal(38,2) DEFAULT NULL,
  `dokumentid` varchar(255) NOT NULL,
  `geschaeftsdokumentart` varchar(255) NOT NULL,
  `id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `FKj3cokssp02w7swpoypkt5jv95` FOREIGN KEY (`id`) REFERENCES `anfrage_dokument` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `anfrage_kunden_emails` (
  `anfrage_id` bigint NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  KEY `FKpinl3l31eh8d690d23q86g88s` (`anfrage_id`),
  CONSTRAINT `FKpinl3l31eh8d690d23q86g88s` FOREIGN KEY (`anfrage_id`) REFERENCES `anfrage` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `anfrage_notiz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `erstellt_am` datetime(6) NOT NULL,
  `mobile_sichtbar` bit(1) NOT NULL,
  `notiz` varchar(4000) NOT NULL,
  `nur_fuer_ersteller` bit(1) NOT NULL,
  `anfrage_id` bigint NOT NULL,
  `mitarbeiter_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdv65icmxtwcf1s2e5j63au1af` (`anfrage_id`),
  KEY `FK8b3ncalux4oq9ry9repssbnfs` (`mitarbeiter_id`),
  CONSTRAINT `FK8b3ncalux4oq9ry9repssbnfs` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKdv65icmxtwcf1s2e5j63au1af` FOREIGN KEY (`anfrage_id`) REFERENCES `anfrage` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `anfrage_notiz_bild` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dateityp` varchar(255) DEFAULT NULL,
  `erstellt_am` datetime(6) NOT NULL,
  `gespeicherter_dateiname` varchar(255) NOT NULL,
  `original_dateiname` varchar(255) DEFAULT NULL,
  `notiz_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK27l5iks86tywcvido9yxtv5e1` (`notiz_id`),
  CONSTRAINT `FK27l5iks86tywcvido9yxtv5e1` FOREIGN KEY (`notiz_id`) REFERENCES `anfrage_notiz` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `arbeitsgang` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beschreibung` varchar(255) NOT NULL,
  `version` bigint DEFAULT '0',
  `abteilung_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_k8fnkcddd9feg6dh055euj3ng` (`beschreibung`),
  KEY `FK6n88tt8b8w4r1opg8ernqera9` (`abteilung_id`),
  CONSTRAINT `FK6n88tt8b8w4r1opg8ernqera9` FOREIGN KEY (`abteilung_id`) REFERENCES `abteilung` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `arbeitsgang_stundensatz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `jahr` int NOT NULL,
  `satz` decimal(10,2) NOT NULL,
  `arbeitsgang_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9n1dvl3im1swo1dw4ctybcs41` (`arbeitsgang_id`),
  CONSTRAINT `FK9n1dvl3im1swo1dw4ctybcs41` FOREIGN KEY (`arbeitsgang_id`) REFERENCES `arbeitsgang` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `arbeitszeitart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aktiv` bit(1) NOT NULL,
  `beschreibung` text,
  `bezeichnung` varchar(100) NOT NULL,
  `sortierung` int NOT NULL,
  `stundensatz` decimal(10,2) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `artikel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `artikelnummer` varchar(64) DEFAULT NULL,
  `beschreibung` text,
  `fertigungszustand` enum('WARMGEFERTIGT','KALTGEFERTIGT','WARMGEWALZT','KALTGEWALZT','KALTGEZOGEN','BLANK','GEBEIZT','GESCHLIFFEN') DEFAULT NULL,
  `herstellverfahren` enum('NAHTLOS','GESCHWEISST','STRANGGEPRESST','GEWALZT','GEZOGEN','GEKANTET') DEFAULT NULL,
  `hicad_name` varchar(255) DEFAULT NULL,
  `kurzbeschreibung` varchar(255) DEFAULT NULL,
  `massnorm` varchar(64) DEFAULT NULL,
  `preiseinheit` varchar(255) DEFAULT NULL,
  `produktlinie` varchar(255) DEFAULT NULL,
  `produktname` varchar(255) DEFAULT NULL,
  `produkttext` varchar(255) DEFAULT NULL,
  `profilform` enum('RUNDROHR','QUADRATROHR','RECHTECKROHR','RUNDSTAB','VIERKANTSTAB','SECHSKANTSTAB','FLACHSTAB','BREITFLACHSTAHL','WINKEL_GLEICHSCHENKLIG','WINKEL_UNGLEICHSCHENKLIG','U_PROFIL','UPE_PROFIL','UAP_PROFIL','I_PROFIL','IPE_PROFIL','HEA_PROFIL','HEB_PROFIL','HEM_PROFIL','T_PROFIL','Z_PROFIL','BLECH','RIFFELBLECH','LOCHBLECH','SONSTIGES') DEFAULT NULL,
  `pulverbeschichtungsgeeignet` bit(1) DEFAULT NULL,
  `suchtext` text,
  `system_stammdaten` bit(1) NOT NULL,
  `verkaufsaufschlag_prozent` decimal(5,2) DEFAULT NULL,
  `verpackungseinheit` bigint DEFAULT NULL,
  `verrechnungseinheit` enum('LAUFENDE_METER','QUADRATMETER','KILOGRAMM','STUECK') DEFAULT NULL,
  `version` bigint DEFAULT '0',
  `verzinkungsgeeignet` bit(1) DEFAULT NULL,
  `werkstoffnorm` varchar(64) DEFAULT NULL,
  `kategorie_id` int DEFAULT NULL,
  `werkstoff_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_javgwy4hh7mkxia0auux8qnbe` (`artikelnummer`),
  UNIQUE KEY `ux_artikel_artikelnummer` (`artikelnummer`),
  KEY `FKab40tv5rnynmyk9fk0uu1o24r` (`kategorie_id`),
  KEY `FK3eve2o35lujpa0um9h5kiakh1` (`werkstoff_id`),
  KEY `ix_artikel_profilform` (`profilform`),
  FULLTEXT KEY `ft_artikel_suchtext` (`suchtext`),
  CONSTRAINT `FK3eve2o35lujpa0um9h5kiakh1` FOREIGN KEY (`werkstoff_id`) REFERENCES `werkstoff` (`id`),
  CONSTRAINT `FKab40tv5rnynmyk9fk0uu1o24r` FOREIGN KEY (`kategorie_id`) REFERENCES `kategorie` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `artikel` VALUES (1,'AL57-BL-0.75',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','0.75 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-0.75 0.75 0,75    0.75  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(2,'AL57-BL-1',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','1 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-1 1 1    1  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(3,'AL57-BL-1.25',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','1.25 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-1.25 1.25 1,25    1.25  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(4,'AL57-BL-1.5',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','1.5 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-1.5 1.5 1,5    1.5  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(5,'AL57-BL-2',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','2 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-2 2 2    2  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(6,'AL57-BL-2.5',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','2.5 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-2.5 2.5 2,5    2.5  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(7,'AL57-BL-3',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','3 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-3 3 3    3  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(8,'AL57-BL-4',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','4 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-4 4 4    4  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(9,'AL57-BL-5',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','5 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-5 5 5    5  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(10,'AL57-BL-6',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','6 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-6 6 6    6  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(11,'AL57-BL-8',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','8 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-8 8 8    8  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(12,'AL57-BL-10',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','10 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-10 10 10    10  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(13,'AL57-BL-12',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','12 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-12 12 12    12  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(14,'AL57-BL-15',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','15 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-15 15 15    15  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(15,'AL57-BL-20',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','20 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte al57-bl-20 20 20    20  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,3),(16,'DXZ-BL-0.75',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 10346',NULL,'EN 10346','0.75 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte dxz-bl-0.75 0.75 0,75    0.75  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,4),(17,'DXZ-BL-1',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 10346',NULL,'EN 10346','1 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte dxz-bl-1 1 1    1  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,4),(18,'DXZ-BL-1.25',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 10346',NULL,'EN 10346','1.25 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte dxz-bl-1.25 1.25 1,25    1.25  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,4),(19,'DXZ-BL-1.5',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 10346',NULL,'EN 10346','1.5 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte dxz-bl-1.5 1.5 1,5    1.5  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,4),(20,'DXZ-BL-2',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 10346',NULL,'EN 10346','2 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte dxz-bl-2 2 2    2  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,4),(21,'DXZ-BL-2.5',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 10346',NULL,'EN 10346','2.5 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte dxz-bl-2.5 2.5 2,5    2.5  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,4),(22,'DXZ-BL-3',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 10346',NULL,'EN 10346','3 mm',NULL,'BLECH',NULL,'blech glattblech tafel platte dxz-bl-3 3 3    3  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,8,4),(23,'AL57-RB-2',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','2 mm',NULL,'RIFFELBLECH',NULL,'riffelblech traenenblech blech rutschhemmend al57-rb-2 2 2    2  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,9,3),(24,'AL57-RB-3',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','3 mm',NULL,'RIFFELBLECH',NULL,'riffelblech traenenblech blech rutschhemmend al57-rb-3 3 3    3  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,9,3),(25,'AL57-RB-4',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','4 mm',NULL,'RIFFELBLECH',NULL,'riffelblech traenenblech blech rutschhemmend al57-rb-4 4 4    4  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,9,3),(26,'AL57-RB-5',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','5 mm',NULL,'RIFFELBLECH',NULL,'riffelblech traenenblech blech rutschhemmend al57-rb-5 5 5    5  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,9,3),(27,'AL57-RB-6',NULL,'KALTGEWALZT','GEWALZT',NULL,NULL,'EN 485-2',NULL,'EN 485-2','6 mm',NULL,'RIFFELBLECH',NULL,'riffelblech traenenblech blech rutschhemmend al57-rb-6 6 6    6  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt',0x01,NULL,NULL,'QUADRATMETER',NULL,NULL,NULL,9,3);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `artikel_bereinigung_backup` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `artikel_id` bigint NOT NULL,
  `feld` varchar(64) NOT NULL,
  `alt_wert` varchar(255) DEFAULT NULL,
  `neu_wert` varchar(255) DEFAULT NULL,
  `grund` varchar(255) DEFAULT NULL,
  `migration` varchar(32) NOT NULL,
  `gesichert_am` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `ix_abb_artikel` (`artikel_id`),
  KEY `ix_abb_migration` (`migration`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `artikel_dokument` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `artikel_id` bigint NOT NULL,
  `original_dateiname` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gespeicherter_dateiname` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `typ` enum('VORSCHAUBILD','ZULASSUNG','ZEICHNUNG','DATENBLATT','MONTAGEANLEITUNG','SONSTIGES') COLLATE utf8mb4_unicode_ci NOT NULL,
  `beschreibung` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `erstellt_am` datetime(6) NOT NULL,
  `mitarbeiter_id` bigint DEFAULT NULL,
  `dateigroesse_bytes` bigint DEFAULT NULL,
  `sortierung` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_artikel_dokument_artikel_typ` (`artikel_id`,`typ`),
  CONSTRAINT `fk_artikel_dokument_artikel` FOREIGN KEY (`artikel_id`) REFERENCES `artikel` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `artikel_hilfsstoffe` (
  `masse_pro_meter` bigint DEFAULT NULL,
  `id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `FK239l3yk1x02byfley91p4idwf` FOREIGN KEY (`id`) REFERENCES `artikel` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `artikel_in_projekt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anschnitt_winkel_links` varchar(255) DEFAULT NULL,
  `anschnitt_winkel_rechts` varchar(255) DEFAULT NULL,
  `aus_lager` bit(1) NOT NULL DEFAULT b'0',
  `bestellt` bit(1) NOT NULL,
  `bestellt_am` date DEFAULT NULL,
  `hinzugefuegt_am` date NOT NULL,
  `kilogramm` decimal(19,2) DEFAULT NULL,
  `kommentar` varchar(255) DEFAULT NULL,
  `meter` decimal(19,2) DEFAULT NULL,
  `preis_pro_stueck` decimal(19,4) DEFAULT NULL,
  `schnitt_form` varchar(255) DEFAULT NULL,
  `stueckzahl` int DEFAULT NULL,
  `artikel_id` bigint NOT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  `lieferanten_artikel_preis_id` bigint DEFAULT NULL,
  `projekt_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbff4hugr1a1yb7wpntofif4lj` (`artikel_id`),
  KEY `FKcntoko2hycpioejmu3pjx9wv9` (`lieferant_id`),
  KEY `FK5dlbaf5e0coskoalc5drxmf9m` (`lieferanten_artikel_preis_id`),
  KEY `FK6tk7id8jx0b4jw05lqwr2k5es` (`projekt_id`),
  KEY `ix_aip_preisstand` (`lieferanten_artikel_preis_id`),
  CONSTRAINT `FK6tk7id8jx0b4jw05lqwr2k5es` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`),
  CONSTRAINT `fk_aip_preisstand` FOREIGN KEY (`lieferanten_artikel_preis_id`) REFERENCES `lieferanten_artikel_preise` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FKbff4hugr1a1yb7wpntofif4lj` FOREIGN KEY (`artikel_id`) REFERENCES `artikel` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKcntoko2hycpioejmu3pjx9wv9` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `artikel_werkstoffe` (
  `breite` decimal(10,2) DEFAULT NULL,
  `durchmesser` decimal(10,2) DEFAULT NULL,
  `flanschdicke` decimal(10,2) DEFAULT NULL,
  `geschliffen` bit(1) NOT NULL DEFAULT b'0',
  `hoehe` decimal(10,2) DEFAULT NULL,
  `mantelflaeche` decimal(12,4) DEFAULT NULL,
  `masse_pro_meter` decimal(12,4) DEFAULT NULL,
  `masse_pro_qm` decimal(10,4) DEFAULT NULL,
  `querschnittsflaeche` decimal(10,3) DEFAULT NULL,
  `standardlaenge_mm` int DEFAULT NULL,
  `stegdicke` decimal(10,2) DEFAULT NULL,
  `wandstaerke` decimal(10,2) DEFAULT NULL,
  `id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `FK94rsucrfvxc8c0usier6dsj3q` FOREIGN KEY (`id`) REFERENCES `artikel` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `artikel_werkstoffe` VALUES (NULL,NULL,NULL,0x00,NULL,2.0000,NULL,1.9950,NULL,NULL,NULL,0.75,1),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,2.6600,NULL,NULL,NULL,1.00,2),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,3.3250,NULL,NULL,NULL,1.25,3),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,3.9900,NULL,NULL,NULL,1.50,4),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,5.3200,NULL,NULL,NULL,2.00,5),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,6.6500,NULL,NULL,NULL,2.50,6),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,7.9800,NULL,NULL,NULL,3.00,7),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,10.6400,NULL,NULL,NULL,4.00,8),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,13.3000,NULL,NULL,NULL,5.00,9),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,15.9600,NULL,NULL,NULL,6.00,10),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,21.2800,NULL,NULL,NULL,8.00,11),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,26.6000,NULL,NULL,NULL,10.00,12),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,31.9200,NULL,NULL,NULL,12.00,13),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,39.9000,NULL,NULL,NULL,15.00,14),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,53.2000,NULL,NULL,NULL,20.00,15),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,5.8875,NULL,NULL,NULL,0.75,16),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,7.8500,NULL,NULL,NULL,1.00,17),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,9.8125,NULL,NULL,NULL,1.25,18),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,11.7750,NULL,NULL,NULL,1.50,19),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,15.7000,NULL,NULL,NULL,2.00,20),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,19.6250,NULL,NULL,NULL,2.50,21),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,23.5500,NULL,NULL,NULL,3.00,22),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,6.0914,NULL,NULL,NULL,2.00,23),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,8.7514,NULL,NULL,NULL,3.00,24),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,11.4114,NULL,NULL,NULL,4.00,25),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,14.0714,NULL,NULL,NULL,5.00,26),(NULL,NULL,NULL,0x00,NULL,2.0000,NULL,16.7314,NULL,NULL,NULL,6.00,27);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_chain_state` (
  `id` int NOT NULL,
  `last_chain_index` bigint NOT NULL DEFAULT '-1',
  `last_entry_hash` char(64) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_audit_chain_state_singleton` CHECK ((`id` = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `audit_chain_state` VALUES (1,-1,NULL,'2026-10-10 14:45:38.165197');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ausgangs_geschaeftsdokument` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `abschlags_nummer` int DEFAULT NULL,
  `betrag_brutto` decimal(12,2) DEFAULT NULL,
  `betrag_netto` decimal(12,2) DEFAULT NULL,
  `betreff` varchar(500) DEFAULT NULL,
  `datum` date NOT NULL,
  `digital_angenommen` bit(1) NOT NULL DEFAULT b'0',
  `dokument_nummer` varchar(20) NOT NULL,
  `erstellt_am` datetime(6) NOT NULL,
  `geaendert_am` datetime(6) DEFAULT NULL,
  `gebucht` bit(1) NOT NULL,
  `gebucht_am` date DEFAULT NULL,
  `html_inhalt` longtext,
  `mwst_satz` decimal(5,4) DEFAULT NULL,
  `pdf_dateiname` varchar(255) DEFAULT NULL,
  `positionen_json` longtext,
  `rechnungsadresse_override` varchar(500) DEFAULT NULL,
  `storniert` bit(1) NOT NULL,
  `storniert_am` date DEFAULT NULL,
  `typ` varchar(30) NOT NULL,
  `versand_datum` date DEFAULT NULL,
  `version` bigint DEFAULT '0',
  `zahlungsziel_tage` int DEFAULT NULL,
  `anfrage_id` bigint DEFAULT NULL,
  `erstellt_von_id` bigint DEFAULT NULL,
  `kunde_id` bigint DEFAULT NULL,
  `projekt_id` bigint DEFAULT NULL,
  `vorgaenger_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_d61b4c7iv88ps56kv50krxa5e` (`dokument_nummer`),
  KEY `FKlgtye9xh70mbopblv0t2p5u17` (`anfrage_id`),
  KEY `FKqllc9cls1j4n5ccy55faye7l9` (`erstellt_von_id`),
  KEY `FKlllmb6a21xp4buuykrebpws5i` (`kunde_id`),
  KEY `FKn2517m61de0rysg7uokfa8ac7` (`projekt_id`),
  KEY `FK4tpqnu19710wax59ela3bkggm` (`vorgaenger_id`),
  CONSTRAINT `FK4tpqnu19710wax59ela3bkggm` FOREIGN KEY (`vorgaenger_id`) REFERENCES `ausgangs_geschaeftsdokument` (`id`),
  CONSTRAINT `FKlgtye9xh70mbopblv0t2p5u17` FOREIGN KEY (`anfrage_id`) REFERENCES `anfrage` (`id`),
  CONSTRAINT `FKlllmb6a21xp4buuykrebpws5i` FOREIGN KEY (`kunde_id`) REFERENCES `kunde` (`id`),
  CONSTRAINT `FKn2517m61de0rysg7uokfa8ac7` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`),
  CONSTRAINT `FKqllc9cls1j4n5ccy55faye7l9` FOREIGN KEY (`erstellt_von_id`) REFERENCES `frontend_user_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ausgangs_geschaeftsdokument_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chain_index` bigint DEFAULT NULL,
  `dokument_id` bigint NOT NULL,
  `aktion` varchar(20) NOT NULL,
  `dokument_nummer` varchar(20) NOT NULL,
  `typ` varchar(30) NOT NULL,
  `datum` date DEFAULT NULL,
  `betreff` varchar(500) DEFAULT NULL,
  `betrag_netto` decimal(12,2) DEFAULT NULL,
  `betrag_brutto` decimal(12,2) DEFAULT NULL,
  `mwst_satz` decimal(5,4) DEFAULT NULL,
  `abschlags_nummer` int DEFAULT NULL,
  `projekt_id` bigint DEFAULT NULL,
  `anfrage_id` bigint DEFAULT NULL,
  `kunde_id` bigint DEFAULT NULL,
  `vorgaenger_id` bigint DEFAULT NULL,
  `versand_datum` date DEFAULT NULL,
  `gebucht` tinyint(1) NOT NULL DEFAULT '0',
  `gebucht_am` date DEFAULT NULL,
  `storniert` tinyint(1) NOT NULL DEFAULT '0',
  `storniert_am` date DEFAULT NULL,
  `digital_angenommen` tinyint(1) NOT NULL DEFAULT '0',
  `inhalt_hash` char(64) DEFAULT NULL,
  `previous_hash` char(64) DEFAULT NULL,
  `entry_hash` char(64) DEFAULT NULL,
  `geaendert_von_id` bigint DEFAULT NULL,
  `geaendert_am` datetime(6) NOT NULL,
  `aenderungsgrund` text,
  `ip_adresse` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_audit_chain_index` (`chain_index`),
  KEY `idx_audit_dokument_id` (`dokument_id`),
  KEY `idx_audit_geaendert_am` (`geaendert_am`),
  KEY `idx_audit_aktion` (`aktion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ausgangs_geschaeftsdokument_counter` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `monat_key` varchar(10) NOT NULL,
  `zaehler` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7bibbc61yjhn43gnj3exafyha` (`monat_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `beleg` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beleg_kategorie` enum('UNZUGEORDNET','KASSE_EINNAHME','KASSE_AUSGABE','PRIVATENTNAHME','PRIVATEINLAGE','BANK','KREDITKARTE','SONSTIGER_BELEG') NOT NULL DEFAULT 'UNZUGEORDNET',
  `dokument_typ` enum('ANGEBOT','AUFTRAGSBESTAETIGUNG','LIEFERSCHEIN','RECHNUNG','GUTSCHRIFT','SONSTIG','BELEG','WERKSTOFFZEUGNIS') DEFAULT NULL,
  `status` enum('NEU','VALIDIERT','VERWORFEN') NOT NULL DEFAULT 'NEU',
  `ki_analyse_status` enum('PENDING','LAEUFT','DONE','FAILED') NOT NULL DEFAULT 'PENDING',
  `beleg_datum` date DEFAULT NULL,
  `beleg_nummer` varchar(100) DEFAULT NULL,
  `beschreibung` varchar(500) DEFAULT NULL,
  `betrag_netto` decimal(15,2) DEFAULT NULL,
  `betrag_brutto` decimal(15,2) DEFAULT NULL,
  `mwst_satz` decimal(5,2) DEFAULT NULL,
  `zahlungsart` varchar(40) DEFAULT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  `ki_vorgeschlagener_lieferant` varchar(255) DEFAULT NULL,
  `ki_confidence` decimal(3,2) DEFAULT NULL,
  `ki_extraktion_json` longtext,
  `ki_fehler_text` varchar(1000) DEFAULT NULL,
  `original_dateiname` varchar(255) DEFAULT NULL,
  `gespeicherter_dateiname` varchar(255) DEFAULT NULL,
  `mime_type` varchar(120) DEFAULT NULL,
  `upload_datum` datetime NOT NULL,
  `uploaded_by_id` bigint DEFAULT NULL,
  `validiert_am` datetime DEFAULT NULL,
  `validiert_von_id` bigint DEFAULT NULL,
  `notiz` varchar(1000) DEFAULT NULL,
  `sachkonto_id` bigint DEFAULT NULL,
  `ist_umbuchung` tinyint(1) NOT NULL DEFAULT '0',
  `kostenstelle_id` bigint DEFAULT NULL,
  `ki_vorgeschlagener_kostenstelle_id` bigint DEFAULT NULL,
  `ki_vorgeschlagener_sachkonto_id` bigint DEFAULT NULL,
  `ki_kostenkonto_confidence` decimal(3,2) DEFAULT NULL,
  `ki_kostenkonto_begruendung` varchar(500) DEFAULT NULL,
  `aufteilungs_modus` enum('VOLLSTAENDIG','TEILWEISE') NOT NULL DEFAULT 'VOLLSTAENDIG',
  `betrag_firma_netto` decimal(15,2) DEFAULT NULL,
  `betrag_firma_brutto` decimal(15,2) DEFAULT NULL,
  `betrag_firma_mwst` decimal(15,2) DEFAULT NULL,
  `laufende_nummer` bigint DEFAULT NULL,
  `festgeschrieben` tinyint(1) NOT NULL DEFAULT '0',
  `festgeschrieben_am` datetime(6) DEFAULT NULL,
  `festgeschrieben_von_id` bigint DEFAULT NULL,
  `monatsabschluss_id` bigint DEFAULT NULL,
  `storno_fuer_beleg_id` bigint DEFAULT NULL,
  `storniert_durch_beleg_id` bigint DEFAULT NULL,
  `storniert_am` datetime(6) DEFAULT NULL,
  `storno_grund` varchar(500) DEFAULT NULL,
  `datei_hash` char(64) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `quelle` enum('SCAN','QUITTUNG','EIGENBELEG','TRANSFER') NOT NULL DEFAULT 'SCAN',
  `gegenpartei` varchar(120) DEFAULT NULL,
  `ausgangsrechnung_id` bigint DEFAULT NULL,
  `ki_zahlungsart` varchar(40) DEFAULT NULL,
  `ki_belegdatum` date DEFAULT NULL,
  `ki_betrag_brutto` decimal(15,2) DEFAULT NULL,
  `ki_kostenkonto_hinweis` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_beleg_laufende_nummer` (`laufende_nummer`),
  KEY `fk_beleg_lieferant` (`lieferant_id`),
  KEY `fk_beleg_validiert_von` (`validiert_von_id`),
  KEY `idx_beleg_status` (`status`),
  KEY `idx_beleg_kategorie` (`beleg_kategorie`),
  KEY `idx_beleg_datum` (`beleg_datum`),
  KEY `idx_beleg_sachkonto` (`sachkonto_id`),
  KEY `idx_beleg_kostenstelle` (`kostenstelle_id`,`beleg_datum`),
  KEY `idx_beleg_uploaded_by_upload_datum` (`uploaded_by_id`,`upload_datum`),
  KEY `idx_beleg_festgeschrieben` (`festgeschrieben`,`beleg_datum`),
  KEY `fk_beleg_ausgangsrechnung` (`ausgangsrechnung_id`),
  CONSTRAINT `fk_beleg_ausgangsrechnung` FOREIGN KEY (`ausgangsrechnung_id`) REFERENCES `projekt_geschaeftsdokument` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_beleg_kostenstelle` FOREIGN KEY (`kostenstelle_id`) REFERENCES `firma_kostenstelle` (`id`),
  CONSTRAINT `fk_beleg_lieferant` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_beleg_sachkonto` FOREIGN KEY (`sachkonto_id`) REFERENCES `sachkonto` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_beleg_uploaded_by` FOREIGN KEY (`uploaded_by_id`) REFERENCES `mitarbeiter` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_beleg_validiert_von` FOREIGN KEY (`validiert_von_id`) REFERENCES `mitarbeiter` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_beleg_datei_oder_umbuchung` CHECK (((`ist_umbuchung` = 1) or (`gespeicherter_dateiname` is not null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `beleg_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chain_index` bigint DEFAULT NULL,
  `beleg_id` bigint DEFAULT NULL,
  `aktion` varchar(30) NOT NULL,
  `laufende_nummer` bigint DEFAULT NULL,
  `beleg_kategorie` varchar(40) DEFAULT NULL,
  `beleg_status` varchar(20) DEFAULT NULL,
  `beleg_datum` date DEFAULT NULL,
  `beleg_nummer` varchar(100) DEFAULT NULL,
  `beschreibung` varchar(500) DEFAULT NULL,
  `betrag_netto` decimal(15,2) DEFAULT NULL,
  `betrag_brutto` decimal(15,2) DEFAULT NULL,
  `mwst_satz` decimal(5,2) DEFAULT NULL,
  `zahlungsart` varchar(40) DEFAULT NULL,
  `aufteilungs_modus` varchar(20) DEFAULT NULL,
  `betrag_firma_netto` decimal(15,2) DEFAULT NULL,
  `betrag_firma_brutto` decimal(15,2) DEFAULT NULL,
  `betrag_firma_mwst` decimal(15,2) DEFAULT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  `sachkonto_id` bigint DEFAULT NULL,
  `sachkonto_nummer` varchar(20) DEFAULT NULL,
  `kostenstelle_id` bigint DEFAULT NULL,
  `ist_umbuchung` tinyint(1) NOT NULL DEFAULT '0',
  `gespeicherter_dateiname` varchar(255) DEFAULT NULL,
  `datei_hash` char(64) DEFAULT NULL,
  `storno_fuer_beleg_id` bigint DEFAULT NULL,
  `storniert_durch_beleg_id` bigint DEFAULT NULL,
  `festgeschrieben` tinyint(1) NOT NULL DEFAULT '0',
  `bezug_typ` varchar(30) DEFAULT NULL,
  `bezug_id` bigint DEFAULT NULL,
  `zusatz` text,
  `previous_hash` char(64) DEFAULT NULL,
  `entry_hash` char(64) DEFAULT NULL,
  `geaendert_von_id` bigint DEFAULT NULL,
  `geaendert_am` datetime(6) NOT NULL,
  `aenderungsgrund` text,
  `ip_adresse` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_beleg_audit_chain_index` (`chain_index`),
  KEY `idx_beleg_audit_beleg` (`beleg_id`),
  KEY `idx_beleg_audit_geaendert_am` (`geaendert_am`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `beleg_audit_chain_state` (
  `id` int NOT NULL,
  `last_chain_index` bigint NOT NULL DEFAULT '-1',
  `last_entry_hash` char(64) DEFAULT NULL,
  `last_laufende_nummer` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_beleg_audit_chain_state_singleton` CHECK ((`id` = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `beleg_audit_chain_state` VALUES (1,-1,NULL,0,'2026-10-10 14:45:46.282469');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `beleg_kostenstellen_anteil` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beleg_id` bigint NOT NULL,
  `kostenstelle_id` bigint NOT NULL,
  `prozent` int DEFAULT NULL,
  `absoluter_betrag` decimal(15,2) DEFAULT NULL,
  `berechneter_betrag` decimal(15,2) DEFAULT NULL,
  `beschreibung` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `zugeordnet_am` datetime DEFAULT NULL,
  `zugeordnet_von_user_id` bigint DEFAULT NULL,
  `streckung_jahre` int NOT NULL DEFAULT '1',
  `streckung_start_jahr` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_bka_user` (`zugeordnet_von_user_id`),
  KEY `idx_bka_beleg` (`beleg_id`),
  KEY `idx_bka_kostenstelle_jahr` (`kostenstelle_id`,`streckung_start_jahr`),
  CONSTRAINT `fk_bka_beleg` FOREIGN KEY (`beleg_id`) REFERENCES `beleg` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_bka_kostenstelle` FOREIGN KEY (`kostenstelle_id`) REFERENCES `firma_kostenstelle` (`id`),
  CONSTRAINT `fk_bka_user` FOREIGN KEY (`zugeordnet_von_user_id`) REFERENCES `frontend_user_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `beleg_position` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beleg_id` bigint NOT NULL,
  `sortierung` int NOT NULL DEFAULT '0',
  `beschreibung` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `menge` decimal(15,3) DEFAULT NULL,
  `einheit` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `einzelpreis` decimal(15,4) DEFAULT NULL,
  `betrag_netto` decimal(15,2) DEFAULT NULL,
  `betrag_brutto` decimal(15,2) DEFAULT NULL,
  `mwst_satz` decimal(5,2) DEFAULT NULL,
  `ist_fuer_firma` tinyint(1) NOT NULL DEFAULT '0',
  `erstellt_am` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_beleg_position_beleg` (`beleg_id`,`sortierung`),
  CONSTRAINT `fk_beleg_position_beleg` FOREIGN KEY (`beleg_id`) REFERENCES `beleg` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bwa_position` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `betrag_kumuliert` decimal(14,2) DEFAULT NULL,
  `betrag_monat` decimal(14,2) NOT NULL,
  `bezeichnung` varchar(255) NOT NULL,
  `differenz` decimal(14,2) DEFAULT NULL,
  `in_rechnungen_gefunden` bit(1) NOT NULL,
  `kategorie` varchar(50) DEFAULT NULL,
  `kontonummer` varchar(20) DEFAULT NULL,
  `manuell_korrigiert` bit(1) NOT NULL,
  `notiz` varchar(500) DEFAULT NULL,
  `rechnungssumme` decimal(14,2) DEFAULT NULL,
  `bwa_upload_id` bigint NOT NULL,
  `kostenstelle_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5maeljbih33gg9i4sse6po1n5` (`bwa_upload_id`),
  KEY `FKjkmp1yibx9emk2fh51t9stkvh` (`kostenstelle_id`),
  CONSTRAINT `FK5maeljbih33gg9i4sse6po1n5` FOREIGN KEY (`bwa_upload_id`) REFERENCES `bwa_upload` (`id`),
  CONSTRAINT `FKjkmp1yibx9emk2fh51t9stkvh` FOREIGN KEY (`kostenstelle_id`) REFERENCES `firma_kostenstelle` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bwa_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_confidence` double DEFAULT NULL,
  `ai_raw_json` text,
  `analyse_datum` datetime(6) DEFAULT NULL,
  `analysiert` bit(1) NOT NULL,
  `freigegeben` bit(1) NOT NULL,
  `freigegeben_am` datetime(6) DEFAULT NULL,
  `gesamt_gemeinkosten` decimal(14,2) DEFAULT NULL,
  `gespeicherter_dateiname` varchar(255) DEFAULT NULL,
  `jahr` int NOT NULL,
  `kosten_aus_bwa` decimal(14,2) DEFAULT NULL,
  `kosten_aus_rechnungen` decimal(14,2) DEFAULT NULL,
  `monat` int DEFAULT NULL,
  `original_dateiname` varchar(255) DEFAULT NULL,
  `typ` enum('MONATLICH','JAEHRLICH') NOT NULL,
  `upload_datum` datetime(6) NOT NULL,
  `freigegeben_von_id` bigint DEFAULT NULL,
  `email_id` bigint DEFAULT NULL,
  `steuerberater_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9t7wn2ad5lclb957icluomdt` (`freigegeben_von_id`),
  KEY `FK66q12gi4xqo63u2c8ea7hco5w` (`email_id`),
  KEY `FKmwf1exgkseq7yksx2j4xrr551` (`steuerberater_id`),
  CONSTRAINT `FK66q12gi4xqo63u2c8ea7hco5w` FOREIGN KEY (`email_id`) REFERENCES `email` (`id`),
  CONSTRAINT `FK9t7wn2ad5lclb957icluomdt` FOREIGN KEY (`freigegeben_von_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKmwf1exgkseq7yksx2j4xrr551` FOREIGN KEY (`steuerberater_id`) REFERENCES `steuerberater_kontakt` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `datensatz_lock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entitaet_typ` enum('AUSGANG','EINGANG') NOT NULL,
  `entitaet_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_display_name` varchar(255) NOT NULL,
  `acquired_at` datetime NOT NULL,
  `last_heartbeat_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_datensatz_lock_target` (`entitaet_typ`,`entitaet_id`),
  KEY `idx_datensatz_lock_heartbeat` (`last_heartbeat_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `datev_konfiguration` (
  `id` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `ziel` varchar(10) NOT NULL DEFAULT 'LODAS',
  `berater_nr` varchar(7) NOT NULL DEFAULT '',
  `mandanten_nr` varchar(5) NOT NULL DEFAULT '',
  `zuordnungen_json` text NOT NULL,
  `aenderungszaehler` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `datev_konfiguration` VALUES (1,0,'LODAS','','','[]',0);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `datev_personalnummer` (
  `mitarbeiter_id` bigint NOT NULL,
  `personalnummer` varchar(5) NOT NULL,
  `normalisiert` varchar(5) NOT NULL,
  PRIMARY KEY (`mitarbeiter_id`),
  UNIQUE KEY `uk_datev_personalnummer_normalisiert` (`normalisiert`),
  CONSTRAINT `fk_datev_personalnummer_mitarbeiter` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dokument_freigabe` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(36) NOT NULL,
  `quell_typ` varchar(20) NOT NULL,
  `quell_dokument_id` bigint NOT NULL,
  `dokument_nummer` varchar(100) NOT NULL,
  `dokument_art` varchar(50) NOT NULL,
  `dokument_betrag` decimal(12,2) DEFAULT NULL,
  `dokument_datei` varchar(255) DEFAULT NULL,
  `bauvorhaben` varchar(500) DEFAULT NULL,
  `kunde_name` varchar(255) DEFAULT NULL,
  `kunde_email` varchar(255) DEFAULT NULL,
  `erstellt_am` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `ablauf_datum` datetime(6) NOT NULL,
  `hash_original` varchar(128) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `akzeptiert_am` datetime(6) DEFAULT NULL,
  `akzeptiert_ip` varchar(45) DEFAULT NULL,
  `akzeptiert_user_agent` varchar(500) DEFAULT NULL,
  `akzeptiert_email` varchar(255) DEFAULT NULL,
  `hash_acceptance` varchar(128) DEFAULT NULL,
  `unterzeichner_vorname` varchar(80) DEFAULT NULL,
  `unterzeichner_nachname` varchar(80) DEFAULT NULL,
  `unterzeichner_name` varchar(160) DEFAULT NULL,
  `akzeptierte_alternativen` longtext,
  `akzeptierter_betrag` decimal(12,2) DEFAULT NULL,
  `positionen_snapshot` longtext,
  `basis_netto` decimal(12,2) DEFAULT NULL,
  `mwst_satz` decimal(5,4) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dokument_freigabe_uuid` (`uuid`),
  KEY `idx_dokument_freigabe_quelle` (`quell_typ`,`quell_dokument_id`),
  KEY `idx_dokument_freigabe_status_ablauf` (`status`,`ablauf_datum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dokumentnummer_counter` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `counter` bigint NOT NULL,
  `month_key` varchar(10) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3fph1ahom65xfs6cjgwptxkdj` (`month_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `authentication_results` text,
  `bayes_score` double DEFAULT NULL,
  `body` longtext,
  `cc` varchar(1000) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `direction` enum('IN','OUT') NOT NULL,
  `error_message` text,
  `first_viewed_at` datetime(6) DEFAULT NULL,
  `from_address` varchar(255) DEFAULT NULL,
  `html_body` longtext,
  `imap_folder` varchar(255) DEFAULT NULL,
  `imap_uid` bigint DEFAULT NULL,
  `inquiry_score` int DEFAULT NULL,
  `is_newsletter` bit(1) NOT NULL,
  `is_potential_inquiry` bit(1) NOT NULL,
  `is_read` bit(1) NOT NULL,
  `is_spam` bit(1) NOT NULL,
  `is_starred` bit(1) NOT NULL DEFAULT b'0',
  `message_id` varchar(512) NOT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `processing_status` enum('QUEUED','PROCESSING','DONE','ERROR') NOT NULL,
  `raw_body` longtext,
  `recipient` varchar(1000) DEFAULT NULL,
  `reply_to_address` varchar(255) DEFAULT NULL,
  `sender_domain` varchar(255) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `spam_score` int DEFAULT NULL,
  `subject` varchar(1000) DEFAULT NULL,
  `user_spam_verdict` varchar(20) DEFAULT NULL,
  `zuordnung_typ` enum('PROJEKT','ANFRAGE','LIEFERANT','STEUERBERATER','KEINE') NOT NULL,
  `zustell_fehler` varchar(500) DEFAULT NULL,
  `zustell_geprueft_am` datetime(6) DEFAULT NULL,
  `zustell_status` enum('OFFEN','UNZUSTELLBAR') NOT NULL DEFAULT 'OFFEN',
  `anfrage_id` bigint DEFAULT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  `parent_email_id` bigint DEFAULT NULL,
  `projekt_id` bigint DEFAULT NULL,
  `steuerberater_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_email_message_id` (`message_id`),
  KEY `idx_email_sender_domain` (`sender_domain`),
  KEY `idx_email_direction` (`direction`),
  KEY `idx_email_zuordnung` (`zuordnung_typ`),
  KEY `idx_email_projekt` (`projekt_id`),
  KEY `idx_email_anfrage` (`anfrage_id`),
  KEY `idx_email_lieferant` (`lieferant_id`),
  KEY `idx_email_processing` (`processing_status`),
  KEY `idx_email_sent_at` (`sent_at`),
  KEY `FKdxywpbb3i2uhx6n5e4jpv2a9b` (`parent_email_id`),
  KEY `FK8nttd3ghg0jdhfmcr9o1ow65s` (`steuerberater_id`),
  KEY `idx_email_starred` (`is_starred`),
  CONSTRAINT `FK40n6q5qbytkv2ts98itxdh2mb` FOREIGN KEY (`anfrage_id`) REFERENCES `anfrage` (`id`),
  CONSTRAINT `FK4iets5h6ml4htxhh8r2cyg61v` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`),
  CONSTRAINT `FK8nttd3ghg0jdhfmcr9o1ow65s` FOREIGN KEY (`steuerberater_id`) REFERENCES `steuerberater_kontakt` (`id`),
  CONSTRAINT `FKdxywpbb3i2uhx6n5e4jpv2a9b` FOREIGN KEY (`parent_email_id`) REFERENCES `email` (`id`),
  CONSTRAINT `FKko6srmhjms5dem9hnbvp3lev2` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_absender` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email_adresse` varchar(255) NOT NULL,
  `anzeigename` varchar(255) DEFAULT NULL,
  `aktiv` tinyint(1) NOT NULL DEFAULT '1',
  `sortierung` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_absender_adresse` (`email_adresse`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_processed` bit(1) DEFAULT NULL,
  `ai_processed_at` datetime(6) DEFAULT NULL,
  `content_id` varchar(255) DEFAULT NULL,
  `inline_attachment` bit(1) DEFAULT NULL,
  `mime_type` varchar(255) DEFAULT NULL,
  `original_filename` varchar(500) DEFAULT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `stored_filename` varchar(500) DEFAULT NULL,
  `email_id` bigint NOT NULL,
  `lieferant_dokument_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_attachment_email` (`email_id`),
  KEY `idx_attachment_ai` (`ai_processed`),
  KEY `FK361t957grpy6abpe8stl7rips` (`lieferant_dokument_id`),
  CONSTRAINT `FK361t957grpy6abpe8stl7rips` FOREIGN KEY (`lieferant_dokument_id`) REFERENCES `lieferant_dokument` (`id`),
  CONSTRAINT `FKqxylawa4l8ipoxstne8bdtghs` FOREIGN KEY (`email_id`) REFERENCES `email` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_blacklist_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `blocked_at` datetime(6) DEFAULT NULL,
  `blocked_by` varchar(255) DEFAULT NULL,
  `email_address` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_blacklist_email` (`email_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recipient` text COLLATE utf8mb4_unicode_ci,
  `cc` text COLLATE utf8mb4_unicode_ci,
  `subject` text COLLATE utf8mb4_unicode_ci,
  `body` longtext COLLATE utf8mb4_unicode_ci,
  `from_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reply_email_id` bigint DEFAULT NULL,
  `projekt_id` bigint DEFAULT NULL,
  `anfrage_id` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `geschaeftsdokument` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `fk_draft_reply_email` (`reply_email_id`),
  KEY `fk_draft_projekt` (`projekt_id`),
  KEY `fk_draft_anfrage` (`anfrage_id`),
  CONSTRAINT `fk_draft_anfrage` FOREIGN KEY (`anfrage_id`) REFERENCES `anfrage` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_draft_projekt` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_draft_reply_email` FOREIGN KEY (`reply_email_id`) REFERENCES `email` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_draft_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `draft_id` bigint NOT NULL,
  `filename` varchar(255) NOT NULL,
  `content_type` varchar(255) NOT NULL,
  `size` bigint NOT NULL,
  `data` longblob NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_email_draft_attachment_draft` (`draft_id`),
  CONSTRAINT `fk_email_draft_attachment_draft` FOREIGN KEY (`draft_id`) REFERENCES `email_draft` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_signature` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `html` longtext NOT NULL,
  `is_system_default` bit(1) NOT NULL DEFAULT b'0',
  `name` varchar(200) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `email_signature` VALUES (1,'2026-10-10 14:45:54.260156','<div class=\"email-signature\" data-system-placeholder=\"1\" style=\"font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#888;border:1px dashed #cbd5e1;background:#f8fafc;padding:12px;border-radius:6px;\"><p style=\"margin:0 0 6px 0;font-weight:600;color:#475569;\">Hier kann Ihre System-Signatur eingetragen werden.</p><p style=\"margin:0;\">Diese Signatur wird an alle automatisch versendeten E-Mails (Auftragsbest&auml;tigungen, Mahnungen, ...) angeh&auml;ngt. Bitte im Bereich „E-Mail-Signaturen\" anpassen.</p></div>',0x01,'System (automatische E-Mails)','2026-10-10 14:45:54.260156');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_signature_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cid` varchar(120) NOT NULL,
  `content_type` varchar(255) NOT NULL,
  `original_filename` varchar(255) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `sort_order` int DEFAULT NULL,
  `stored_filename` varchar(255) NOT NULL,
  `signature_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpfwt7v1tmth3t6xp79fip4du8` (`signature_id`),
  CONSTRAINT `FKpfwt7v1tmth3t6xp79fip4du8` FOREIGN KEY (`signature_id`) REFERENCES `email_signature` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_text_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dokument_typ` varchar(40) NOT NULL,
  `kategorie` enum('DOKUMENT','MAHNWESEN','WEBSITE','SYSTEM') DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `subject_template` varchar(500) NOT NULL,
  `html_body` longtext NOT NULL,
  `aktiv` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_text_template_doktyp` (`dokument_typ`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `email_text_template` VALUES (1,'RECHNUNG','DOKUMENT','Rechnung','Rechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei sende ich Ihnen die Rechnung für unsere erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style=\"color:#C00000\">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style=\"color:#C00000\">{{RECHNUNGSDATUM}}</span><br><strong>Fälligkeitsdatum:</strong> <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span><br><strong>Gesamtbetrag:</strong> <span style=\"color:#C00000\">{{BETRAG}}</span></p><p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span> auf das in der Rechnung angegebene Konto.<br><strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>',1,'2026-10-10 14:45:36.371859','2026-10-10 14:45:36.371859'),(2,'TEILRECHNUNG','DOKUMENT','Teilrechnung','Teilrechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei sende ich Ihnen eine Teilrechnung für unsere bereits erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style=\"color:#C00000\">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style=\"color:#C00000\">{{RECHNUNGSDATUM}}</span><br><strong>Fälligkeitsdatum:</strong> <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span><br><strong>Gesamtbetrag:</strong> <span style=\"color:#C00000\">{{BETRAG}}</span></p><p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span>.<br><strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>',1,'2026-10-10 14:45:36.373728','2026-10-10 14:45:36.373728'),(3,'SCHLUSSRECHNUNG','DOKUMENT','Schlussrechnung','Schlussrechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei sende ich Ihnen die Schlussrechnung für unsere erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p><p>Wir würden uns sehr über eine Bewertung freuen: {{REVIEW_LINK}}</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style=\"color:#C00000\">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style=\"color:#C00000\">{{RECHNUNGSDATUM}}</span><br><strong>Fälligkeitsdatum:</strong> <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span><br><strong>Gesamtbetrag:</strong> <span style=\"color:#C00000\">{{BETRAG}}</span></p><p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span>.<br><strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>',1,'2026-10-10 14:45:36.374933','2026-10-10 14:45:36.374933'),(4,'ABSCHLAGSRECHNUNG','DOKUMENT','Abschlagsrechnung','Abschlagsrechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei sende ich Ihnen eine Abschlagsrechnung gemäß unserer Vereinbarung. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style=\"color:#C00000\">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style=\"color:#C00000\">{{RECHNUNGSDATUM}}</span><br><strong>Fälligkeitsdatum:</strong> <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span><br><strong>Gesamtbetrag:</strong> <span style=\"color:#C00000\">{{BETRAG}}</span></p><p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span>.<br><strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>',1,'2026-10-10 14:45:36.375795','2026-10-10 14:45:36.375795'),(5,'ERSTE_MAHNUNG','MAHNWESEN','1. Mahnung','1. Mahnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>leider haben wir festgestellt, dass die Rechnung mit der Nummer {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}} noch nicht beglichen wurde.</p><p>Der Betrag in Höhe von <strong>{{BETRAG}}</strong> war am <strong>{{FAELLIGKEITSDATUM}}</strong> fällig.</p><p>Bitte überweisen Sie den ausstehenden Betrag umgehend, um zusätzliche Mahngebühren zu vermeiden.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style=\"color:#C00000\">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span><br><strong>Fälligkeitsdatum:</strong> <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span><br><strong>Offener Betrag:</strong> <span style=\"color:#C00000\">{{BETRAG}}</span></p>',1,'2026-10-10 14:45:36.376670','2026-10-10 14:45:37.736683'),(6,'ANGEBOT','DOKUMENT','Anfrage / Angebot','Anfrage: (BV: {{BAUVORHABEN}}) Anfragesnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>im Anhang finden Sie das besprochene Angebot.<br>Bei Rückfragen können Sie sich gerne telefonisch oder per E-Mail bei uns melden.</p><p>Bei Auftragserteilung wird von uns eine 3D-Zeichnung mit genauen Maßen erstellt.<br>Nach Freigabe der Zeichnung gehen wir in die Produktion.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Anfragesnummer:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span></p>',1,'2026-10-10 14:45:36.377371','2026-10-10 14:45:36.377371'),(7,'AUFTRAGSBESTAETIGUNG','DOKUMENT','Auftragsbestätigung','Auftragsbestätigung: (BV: {{BAUVORHABEN}}) Auftragsnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei sende ich Ihnen die Auftragsbestätigung. Die detaillierte Auftragsbestätigung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style=\"color:#C00000\">{{PROJEKTNUMMER}}</span><br><strong>Auftragsnummer:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span><br><strong>Auftragssumme:</strong> <span style=\"color:#C00000\">{{BETRAG}}</span></p>',1,'2026-10-10 14:45:36.378007','2026-10-10 14:45:36.378007'),(8,'ZEICHNUNG','DOKUMENT','Zeichnung / Entwurf','Kundenzeichnung BV: ({{BAUVORHABEN}})','<p>{{ANREDE}},</p><p>anbei finden Sie die PDF mit dem ersten Entwurf Ihres Bauprojekts.<br>Bitte nehmen Sie sich etwas Zeit, um das Design sorgfältig zu überprüfen.<br>Sollten Sie weitere Änderungswünsche haben oder Fragen auftauchen, stehe ich Ihnen gerne zur Verfügung.</p><p>Wir möchten Sie darauf hinweisen, dass größere Zeichnungsänderungen, die gravierend vom ursprünglichen Angebot abweichen, aufgrund des damit verbundenen Zeitaufwands zusätzliche Kosten verursachen können. Wir bitten um Ihr Verständnis dafür.</p><p>Falls dies im Angebot so vereinbart war, wird nach Abschluss der Planung eine Abschlagsrechnung erstellt.<br>Bei Fragen oder weiteren Anliegen stehe ich Ihnen jederzeit zur Verfügung.</p><p>Vielen Dank für Ihre Zusammenarbeit und Ihr Verständnis.</p>',1,'2026-10-10 14:45:36.378518','2026-10-10 14:45:36.378518'),(9,'ZAHLUNGSERINNERUNG','MAHNWESEN','Zahlungserinnerung','Zahlungserinnerung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>vermutlich ist es Ihrer Aufmerksamkeit entgangen, dass die Rechnung mit der Nummer {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}} noch nicht beglichen wurde.</p><p>Der Betrag in Höhe von <strong>{{BETRAG}}</strong> war am <strong>{{FAELLIGKEITSDATUM}}</strong> fällig.</p><p>Bitte überweisen Sie den ausstehenden Betrag in den nächsten Tagen. Sollte sich Ihre Zahlung mit dieser Erinnerung überschnitten haben, betrachten Sie diese E-Mail bitte als gegenstandslos.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style=\"color:#C00000\">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span><br><strong>Fälligkeitsdatum:</strong> <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span><br><strong>Offener Betrag:</strong> <span style=\"color:#C00000\">{{BETRAG}}</span></p>',1,'2026-10-10 14:45:37.738560','2026-10-10 14:45:37.738560'),(11,'ZWEITE_MAHNUNG','MAHNWESEN','2. Mahnung','2. Mahnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>leider mussten wir feststellen, dass die Rechnung mit der Nummer {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}} auch nach unserer 1. Mahnung noch immer nicht beglichen wurde.</p><p>Der Betrag in Höhe von <strong>{{BETRAG}}</strong> war bereits am <strong>{{FAELLIGKEITSDATUM}}</strong> fällig.</p><p>Wir fordern Sie hiermit letztmalig auf, den ausstehenden Betrag innerhalb von 7 Tagen zu überweisen. Andernfalls sehen wir uns gezwungen, die Forderung an ein Inkassobüro zu übergeben oder gerichtliche Schritte einzuleiten. Die dadurch entstehenden Kosten gehen zu Ihren Lasten.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style=\"color:#C00000\">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span><br><strong>Fälligkeitsdatum:</strong> <span style=\"color:#C00000\">{{FAELLIGKEITSDATUM}}</span><br><strong>Offener Betrag:</strong> <span style=\"color:#C00000\">{{BETRAG}}</span></p>',1,'2026-10-10 14:45:37.740721','2026-10-10 14:45:37.740721'),(12,'STORNORECHNUNG','DOKUMENT','Stornorechnung','Stornorechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei erhalten Sie die Stornorechnung zur Rechnung {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}}.</p><p>Mit dieser Stornorechnung wird die ursprüngliche Rechnung in voller Höhe storniert. Bitte ersetzen Sie die ursprüngliche Rechnung in Ihren Unterlagen durch die anliegende Stornorechnung.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style=\"color:#C00000\">{{PROJEKTNUMMER}}</span><br><strong>Stornorechnung-Nr.:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style=\"color:#C00000\">{{RECHNUNGSDATUM}}</span><br><strong>Stornierter Betrag:</strong> <span style=\"color:#C00000\">{{BETRAG}}</span></p>',1,'2026-10-10 14:45:37.741732','2026-10-10 14:45:37.741732'),(13,'GUTSCHRIFT','DOKUMENT','Gutschrift','Gutschrift: (BV: {{BAUVORHABEN}}) Gutschrift-Nr.: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei erhalten Sie die Gutschrift für das Bauvorhaben {{BAUVORHABEN}}.</p><p>Den Gutschriftsbetrag werden wir in den nächsten Tagen auf Ihr Konto überweisen bzw. mit der nächsten Rechnung verrechnen.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style=\"color:#C00000\">{{PROJEKTNUMMER}}</span><br><strong>Gutschrift-Nr.:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style=\"color:#C00000\">{{RECHNUNGSDATUM}}</span><br><strong>Gutschriftsbetrag:</strong> <span style=\"color:#C00000\">{{BETRAG}}</span></p>',1,'2026-10-10 14:45:37.742564','2026-10-10 14:45:37.742564'),(14,'WEBSITE_ANFRAGE_BESTAETIGUNG','WEBSITE','Webseite — Anfragebestätigung','Wir haben Ihre Anfrage erhalten — BV: {{BAUVORHABEN}}','<p>{{ANREDE}},</p><p>vielen Dank für Ihre Anfrage über unsere Webseite! Wir haben Ihre Nachricht erhalten und melden uns innerhalb der nächsten 1–2 Werktage persönlich bei Ihnen.</p><p><strong>Ihre Angaben:</strong><br>Bauvorhaben: <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br>Anfrage-Datum: <span style=\"color:#C00000\">{{ANFRAGE_DATUM}}</span><br>Anfrage-Nr.: <span style=\"color:#C00000\">{{ANFRAGENUMMER}}</span></p><p><strong>Ihre Nachricht an uns:</strong></p><p style=\"white-space:pre-wrap;color:#475569;border-left:3px solid #e5e7eb;padding:6px 12px;\">{{NACHRICHT}}</p><p>Sollten sich Details an Ihrem Projekt geändert haben, antworten Sie einfach auf diese E-Mail — wir ergänzen Ihre Anfrage dann gerne.</p>',1,'2026-10-10 14:45:40.846500','2026-10-10 14:45:40.846500'),(15,'NACHTRAGSANGEBOT','DOKUMENT','Nachtragsangebot','Nachtragsangebot: (BV: {{BAUVORHABEN}}) Angebotsnummer: {{DOKUMENTNUMMER}}','<p>{{ANREDE}} {{KUNDENNAME}},</p><p>im Anhang finden Sie unser Nachtragsangebot zu dem laufenden Projekt.<br>Bei Rückfragen können Sie sich gerne telefonisch oder per E-Mail bei uns melden.</p><p><strong>Bauvorhaben:</strong> <span style=\"color:#C00000\">{{BAUVORHABEN}}</span><br><strong>Angebotsnummer:</strong> <span style=\"color:#C00000\">{{DOKUMENTNUMMER}}</span></p>',1,'2026-10-10 14:45:43.295378','2026-10-10 14:45:43.295378');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_last_accessed` (
  `user_id` bigint NOT NULL,
  `entity_type` varchar(64) NOT NULL,
  `entity_id` bigint NOT NULL,
  `zugegriffen_am` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`,`entity_type`,`entity_id`),
  KEY `idx_entity_last_accessed_lookup` (`user_id`,`entity_type`,`zugegriffen_am`),
  CONSTRAINT `fk_entity_last_accessed_user` FOREIGN KEY (`user_id`) REFERENCES `frontend_user_profile` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feiertag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bezeichnung` varchar(255) NOT NULL,
  `bundesland` varchar(10) NOT NULL,
  `datum` date NOT NULL,
  `halb_tag` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf2r4nh1rxcgarpavbxe33bf7j` (`datum`,`bundesland`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `firma_kostenstelle` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aktiv` bit(1) NOT NULL,
  `beschreibung` varchar(500) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `ist_fixkosten` bit(1) NOT NULL,
  `ist_investition` bit(1) NOT NULL,
  `sortierung` int DEFAULT NULL,
  `typ` enum('LAGER','GEMEINKOSTEN','PROJEKT','SONSTIG') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_g54inn9epgrjg61vdtpbwybu2` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `firmeninformation` (
  `id` bigint NOT NULL,
  `bank_name` varchar(255) DEFAULT NULL,
  `bg_satz_override` decimal(5,2) DEFAULT NULL,
  `bic` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `fax` varchar(255) DEFAULT NULL,
  `firmenfarbe` varchar(7) DEFAULT NULL,
  `firmenname` varchar(255) NOT NULL,
  `fusszeile_text` varchar(1000) DEFAULT NULL,
  `geschaeftsfuehrer` varchar(255) DEFAULT NULL,
  `google_bewertungs_link` varchar(500) DEFAULT NULL,
  `handelsregister` varchar(255) DEFAULT NULL,
  `handelsregister_nummer` varchar(255) DEFAULT NULL,
  `iban` varchar(255) DEFAULT NULL,
  `logo_dateiname` varchar(255) DEFAULT NULL,
  `mahnverfahren_aktiv` bit(1) NOT NULL DEFAULT b'0',
  `mahnverfahren_neues_zahlungsziel_tage` int NOT NULL DEFAULT '7',
  `ort` varchar(255) DEFAULT NULL,
  `plz` varchar(255) DEFAULT NULL,
  `steuernummer` varchar(255) DEFAULT NULL,
  `strasse` varchar(255) DEFAULT NULL,
  `tage_bis_erste_mahnung` int NOT NULL DEFAULT '14',
  `tage_bis_zahlungserinnerung` int NOT NULL DEFAULT '7',
  `tage_bis_zweite_mahnung` int NOT NULL DEFAULT '21',
  `telefon` varchar(255) DEFAULT NULL,
  `ust_id_nr` varchar(255) DEFAULT NULL,
  `version` bigint DEFAULT '0',
  `website` varchar(255) DEFAULT NULL,
  `gewerk_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1cut60jy9u292yx9x339p43ay` (`gewerk_id`),
  CONSTRAINT `FK1cut60jy9u292yx9x339p43ay` FOREIGN KEY (`gewerk_id`) REFERENCES `gewerk` (`id`),
  CONSTRAINT `fk_firmeninformation_gewerk` FOREIGN KEY (`gewerk_id`) REFERENCES `gewerk` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `flyway_schema_history` VALUES (1,'208','spam bayes model','SQL','V208__spam_bayes_model.sql',616499901,'basis-schema','2026-10-10 14:46:02',0,1),(2,'209','zeitbuchung mitarbeiter nullable','SQL','V209__zeitbuchung_mitarbeiter_nullable.sql',-556038251,'basis-schema','2026-10-10 14:46:02',0,1),(3,'210','email starred','SQL','V210__email_starred.sql',917057867,'basis-schema','2026-10-10 14:46:02',0,1),(4,'211','email drafts','SQL','V211__email_drafts.sql',1222262582,'basis-schema','2026-10-10 14:46:02',0,1),(5,'212','zuordnung user tracking','SQL','V212__zuordnung_user_tracking.sql',1888633490,'basis-schema','2026-10-10 14:46:02',0,1),(6,'213','zuordnung user tracking','SQL','V213__zuordnung_user_tracking.sql',1402190509,'basis-schema','2026-10-10 14:46:02',0,1),(7,'214','lieferant standard kostenstelle','SQL','V214__lieferant_standard_kostenstelle.sql',755117527,'basis-schema','2026-10-10 14:46:02',0,1),(8,'215','imap default settings','SQL','V215__imap_default_settings.sql',1546176595,'basis-schema','2026-10-10 14:46:02',0,1),(9,'216','email draft subject text','SQL','V216__email_draft_subject_text.sql',1711781676,'basis-schema','2026-10-10 14:46:02',0,1),(10,'217','formular template textbaustein default','SQL','V217__formular_template_textbaustein_default.sql',-2048682858,'basis-schema','2026-10-10 14:46:02',0,1),(11,'218','email text template','SQL','V218__email_text_template.sql',-478564629,'basis-schema','2026-10-10 14:46:02',0,1),(12,'219','ooo reply log','SQL','V219__ooo_reply_log.sql',-1446521360,'basis-schema','2026-10-10 14:46:02',0,1),(13,'220','artikel in projekt anschnittwinkel to varchar','SQL','V220__artikel_in_projekt_anschnittwinkel_to_varchar.sql',1704656673,'basis-schema','2026-10-10 14:46:02',0,1),(14,'221','system mitarbeiter webseite','SQL','V221__system_mitarbeiter_webseite.sql',2037631657,'basis-schema','2026-10-10 14:46:02',0,1),(15,'222','kunden zaehler','SQL','V222__kunden_zaehler.sql',1027772329,'basis-schema','2026-10-10 14:46:02',0,1),(16,'244','entity last accessed','SQL','V244__entity_last_accessed.sql',-1274031161,'basis-schema','2026-10-10 14:46:02',0,1),(17,'245','dokument freigabe','SQL','V245__dokument_freigabe.sql',642372782,'basis-schema','2026-10-10 14:46:02',0,1),(18,'246','abteilung freigabe annahme push','SQL','V246__abteilung_freigabe_annahme_push.sql',-1579415066,'basis-schema','2026-10-10 14:46:02',0,1),(19,'247','ausgangs dokument digital angenommen','SQL','V247__ausgangs_dokument_digital_angenommen.sql',2072106073,'basis-schema','2026-10-10 14:46:02',0,1),(20,'248','drop geschaeftsdokument zahlung','SQL','V248__drop_geschaeftsdokument_zahlung.sql',965201783,'basis-schema','2026-10-10 14:46:02',0,1),(21,'249','ausgangs geschaeftsdokument audit','SQL','V249__ausgangs_geschaeftsdokument_audit.sql',925154422,'basis-schema','2026-10-10 14:46:02',0,1),(22,'250','email text template mahnstufen','SQL','V250__email_text_template_mahnstufen.sql',-267886101,'basis-schema','2026-10-10 14:46:02',0,1),(23,'251','firmeninformation mahnverfahren','SQL','V251__firmeninformation_mahnverfahren.sql',689827873,'basis-schema','2026-10-10 14:46:02',0,1),(24,'252','firmeninformation google bewertungs link','SQL','V252__firmeninformation_google_bewertungs_link.sql',1290271610,'basis-schema','2026-10-10 14:46:02',0,1),(25,'254','rollback dokument freigabe zwei faktor','SQL','V254__rollback_dokument_freigabe_zwei_faktor.sql',353141432,'basis-schema','2026-10-10 14:46:02',0,1),(26,'255','audit hash chain','SQL','V255__audit_hash_chain.sql',-741136220,'basis-schema','2026-10-10 14:46:02',0,1),(27,'256','email signature system default','SQL','V256__email_signature_system_default.sql',-1201696834,'basis-schema','2026-10-10 14:46:02',0,1),(28,'257','email out mark read','SQL','V257__email_out_mark_read.sql',1926350033,'basis-schema','2026-10-10 14:46:02',0,1),(29,'258','email spam header features','SQL','V258__email_spam_header_features.sql',898860345,'basis-schema','2026-10-10 14:46:02',0,1),(30,'288','lieferant dokument ausgeblendet','SQL','V288__lieferant_dokument_ausgeblendet.sql',1475020196,'basis-schema','2026-10-10 14:46:02',0,1),(31,'289','steuerberater ansprechpartner','SQL','V289__steuerberater_ansprechpartner.sql',1118823449,'basis-schema','2026-10-10 14:46:02',0,1),(32,'290','email absender','SQL','V290__email_absender.sql',674577309,'basis-schema','2026-10-10 14:46:02',0,1),(33,'291','steuerberater ansprechpartner anrede enum','SQL','V291__steuerberater_ansprechpartner_anrede_enum.sql',117004945,'basis-schema','2026-10-10 14:46:02',0,1),(34,'292','dokument lock','SQL','V292__dokument_lock.sql',-1280376006,'basis-schema','2026-10-10 14:46:02',0,1),(35,'293','seen sender domain','SQL','V293__seen_sender_domain.sql',228405962,'basis-schema','2026-10-10 14:46:02',0,1),(36,'294','krankenkasse','SQL','V294__krankenkasse.sql',-1168959231,'basis-schema','2026-10-10 14:46:02',0,1),(37,'295','sv satz','SQL','V295__sv_satz.sql',-589920824,'basis-schema','2026-10-10 14:46:02',0,1),(38,'296','gewerk','SQL','V296__gewerk.sql',600900197,'basis-schema','2026-10-10 14:46:02',0,1),(39,'297','mitarbeiter sv felder','SQL','V297__mitarbeiter_sv_felder.sql',-1430974261,'basis-schema','2026-10-10 14:46:02',0,1),(40,'298','firma gewerk','SQL','V298__firma_gewerk.sql',-1115235013,'basis-schema','2026-10-10 14:46:02',0,1),(41,'299','mitarbeiter stundenlohn historie','SQL','V299__mitarbeiter_stundenlohn_historie.sql',379312970,'basis-schema','2026-10-10 14:46:02',0,1),(42,'300','mitarbeiter geschaeftsfuehrer','SQL','V300__mitarbeiter_geschaeftsfuehrer.sql',-245660848,'basis-schema','2026-10-10 14:46:02',0,1),(43,'301','website analytics snapshot','SQL','V301__website_analytics_snapshot.sql',1866850494,'basis-schema','2026-10-10 14:46:02',0,1),(44,'302','beleg','SQL','V302__beleg.sql',-1284322380,'basis-schema','2026-10-10 14:46:02',0,1),(45,'303','sachkonto','SQL','V303__sachkonto.sql',1925850456,'basis-schema','2026-10-10 14:46:02',0,1),(46,'304','beleg dokument typ und umbuchung','SQL','V304__beleg_dokument_typ_und_umbuchung.sql',-1904229831,'basis-schema','2026-10-10 14:46:02',0,1),(47,'305','lieferant dokument beleg fk','SQL','V305__lieferant_dokument_beleg_fk.sql',264018372,'basis-schema','2026-10-10 14:46:02',0,1),(48,'306','email text template kategorie und website anfrage','SQL','V306__email_text_template_kategorie_und_website_anfrage.sql',-1591748870,'basis-schema','2026-10-10 14:46:02',0,1),(49,'307','sachkonto standardkonten handwerker','SQL','V307__sachkonto_standardkonten_handwerker.sql',-1120203268,'basis-schema','2026-10-10 14:46:02',0,1),(50,'308','zahlungsart stammdaten','SQL','V308__zahlungsart_stammdaten.sql',-217115857,'basis-schema','2026-10-10 14:46:02',0,1),(51,'309','beleg sachkonto enums','SQL','V309__beleg_sachkonto_enums.sql',-1920162188,'basis-schema','2026-10-10 14:46:02',0,1),(52,'310','beleg kategorie privateinlage','SQL','V310__beleg_kategorie_privateinlage.sql',709904012,'basis-schema','2026-10-10 14:46:02',0,1),(53,'311','abteilung webseiten anfragen push','SQL','V311__abteilung_webseiten_anfragen_push.sql',1050766770,'basis-schema','2026-10-10 14:46:02',0,1),(54,'312','beleg kostenstelle und ki vorschlaege','SQL','V312__beleg_kostenstelle_und_ki_vorschlaege.sql',-1894593998,'basis-schema','2026-10-10 14:46:02',0,1),(55,'313','beleg aufteilung positionen','SQL','V313__beleg_aufteilung_positionen.sql',-2136834903,'basis-schema','2026-10-10 14:46:02',0,1),(56,'314','zeitbuchung audit erfassungsquelle system','SQL','V314__zeitbuchung_audit_erfassungsquelle_system.sql',147688855,'basis-schema','2026-10-10 14:46:02',0,1),(57,'315','beleg uploaded by index','SQL','V315__beleg_uploaded_by_index.sql',-1698925617,'basis-schema','2026-10-10 14:46:02',0,1),(58,'316','email sent at index','SQL','V316__email_sent_at_index.sql',1349382451,'basis-schema','2026-10-10 14:46:02',0,1),(59,'317','freigabe unterzeichner name','SQL','V317__freigabe_unterzeichner_name.sql',751114496,'basis-schema','2026-10-10 14:46:02',0,1),(60,'318','beleg kostenstellen anteil','SQL','V318__beleg_kostenstellen_anteil.sql',1090243607,'basis-schema','2026-10-10 14:46:02',0,1),(61,'319','kasse einstellung','SQL','V319__kasse_einstellung.sql',-1963122914,'basis-schema','2026-10-10 14:46:02',0,1),(62,'320','kasse einstellung ehegattengehalt vereinfachung','SQL','V320__kasse_einstellung_ehegattengehalt_vereinfachung.sql',-1777777647,'basis-schema','2026-10-10 14:46:02',0,1),(63,'321','zeitbuchung eindeutige aktive buchung','SQL','V321__zeitbuchung_eindeutige_aktive_buchung.sql',-1988487041,'basis-schema','2026-10-10 14:46:02',0,1),(64,'322','nachtragsangebot enum check constraints','SQL','V322__nachtragsangebot_enum_check_constraints.sql',734814453,'basis-schema','2026-10-10 14:46:02',0,1),(65,'323','nachtragsangebot native enum columns to varchar','SQL','V323__nachtragsangebot_native_enum_columns_to_varchar.sql',-1481559667,'basis-schema','2026-10-10 14:46:02',0,1),(66,'324','email text template nachtragsangebot','SQL','V324__email_text_template_nachtragsangebot.sql',1413080911,'basis-schema','2026-10-10 14:46:02',0,1),(67,'325','dokument freigabe ausgewaehlte alternativen','SQL','V325__dokument_freigabe_ausgewaehlte_alternativen.sql',-618150092,'basis-schema','2026-10-10 14:46:02',0,1),(68,'326','dokument freigabe positionen snapshot','SQL','V326__dokument_freigabe_positionen_snapshot.sql',465011131,'basis-schema','2026-10-10 14:46:02',0,1),(69,'327','projekt geschaeftsdokument system generiert','SQL','V327__projekt_geschaeftsdokument_system_generiert.sql',1911824702,'basis-schema','2026-10-10 14:46:02',0,1),(70,'328','mahnverfahren stufen abstaende','SQL','V328__mahnverfahren_stufen_abstaende.sql',2097492855,'basis-schema','2026-10-10 14:46:02',0,1),(71,'329','projekt kurzbeschreibung text','SQL','V329__projekt_kurzbeschreibung_text.sql',1412275688,'basis-schema','2026-10-10 14:46:02',0,1),(72,'330','lieferant kategorie rollen tabellen','SQL','V330__lieferant_kategorie_rollen_tabellen.sql',758535961,'basis-schema','2026-10-10 14:46:02',0,1),(73,'331','kunde notiz','SQL','V331__kunde_notiz.sql',-1967107759,'basis-schema','2026-10-10 14:46:02',0,1),(74,'332','email zustellstatus','SQL','V332__email_zustellstatus.sql',-737914839,'basis-schema','2026-10-10 14:46:02',0,1),(75,'333','zeitbuchung automatisch beendet','SQL','V333__zeitbuchung_automatisch_beendet.sql',-147408158,'basis-schema','2026-10-10 14:46:02',0,1),(76,'334','projekt abgeschlossen manuell','SQL','V334__projekt_abgeschlossen_manuell.sql',-107928665,'basis-schema','2026-10-10 14:46:02',0,1),(77,'335','lieferant alias name','SQL','V335__lieferant_alias_name.sql',-1458581334,'basis-schema','2026-10-10 14:46:02',0,1),(78,'336','artikel technische stammdaten','SQL','V336__artikel_technische_stammdaten.sql',1494201698,'basis-schema','2026-10-10 14:46:02',0,1),(79,'337','werkstoff dichte und eignungen','SQL','V337__werkstoff_dichte_und_eignungen.sql',-1051005368,'basis-schema','2026-10-10 14:46:02',0,1),(80,'338','lieferantenpreise historie','SQL','V338__lieferantenpreise_historie.sql',-1141436381,'basis-schema','2026-10-10 14:46:02',0,1),(81,'339','artikel in projekt preisbezug','SQL','V339__artikel_in_projekt_preisbezug.sql',-1941973494,'basis-schema','2026-10-10 14:46:02',0,1),(82,'340','artikel normen bereinigung','SQL','V340__artikel_normen_bereinigung.sql',330633618,'basis-schema','2026-10-10 14:46:02',0,1),(83,'341','artikel masse aus produktname','SQL','V341__artikel_masse_aus_produktname.sql',596133730,'basis-schema','2026-10-10 14:46:02',0,1),(84,'342','blech kategorien','SQL','V342__blech_kategorien.sql',2111047669,'basis-schema','2026-10-10 14:46:02',0,1),(85,'343','stammdaten generator','SQL','V343__stammdaten_generator.sql',-1853189823,'basis-schema','2026-10-10 14:46:02',0,1),(86,'344','flachstahl gewichte korrektur','SQL','V344__flachstahl_gewichte_korrektur.sql',-853283636,'basis-schema','2026-10-10 14:46:02',0,1),(87,'345','stammdaten aluminium','SQL','V345__stammdaten_aluminium.sql',39137403,'basis-schema','2026-10-10 14:46:02',0,1),(88,'346','stammdaten bleche','SQL','V346__stammdaten_bleche.sql',-914177379,'basis-schema','2026-10-10 14:46:02',0,1),(89,'347','stammdaten rohrvarianten','SQL','V347__stammdaten_rohrvarianten.sql',-1825382018,'basis-schema','2026-10-10 14:46:02',0,1),(90,'348','artikelnummern und suchtext bestand','SQL','V348__artikelnummern_und_suchtext_bestand.sql',-1588139907,'basis-schema','2026-10-10 14:46:02',0,1),(91,'349','belegsplit auf firmenanteil nachrechnen','SQL','V349__belegsplit_auf_firmenanteil_nachrechnen.sql',1122002802,'basis-schema','2026-10-10 14:46:02',0,1),(92,'350','lieferant vorauskasse','SQL','V350__lieferant_vorauskasse.sql',-813666807,'basis-schema','2026-10-10 14:46:02',0,1),(93,'351','kassenbuch festschreibung','SQL','V351__kassenbuch_festschreibung.sql',1037960892,'basis-schema','2026-10-10 14:46:02',0,1),(94,'352','dokument mailkonto','SQL','V352__dokument_mailkonto.sql',-1051790900,'basis-schema','2026-10-10 14:46:02',0,1),(95,'353','mail absender anzeigename','SQL','V353__mail_absender_anzeigename.sql',-1767202280,'basis-schema','2026-10-10 14:46:02',0,1),(96,'354','dokument mailkonto imap','SQL','V354__dokument_mailkonto_imap.sql',-997562948,'basis-schema','2026-10-10 14:46:02',0,1),(97,'355','artikel dokumenttexte und verkaufsaufschlag','SQL','V355__artikel_dokumenttexte_und_verkaufsaufschlag.sql',-1614562823,'basis-schema','2026-10-10 14:46:02',0,1),(98,'356','artikel in projekt aus lager','SQL','V356__artikel_in_projekt_aus_lager.sql',-1040018529,'basis-schema','2026-10-10 14:46:02',0,1),(99,'357','artikel dokumente und vorschaubild','SQL','V357__artikel_dokumente_und_vorschaubild.sql',1592442600,'basis-schema','2026-10-10 14:46:02',0,1),(100,'358','werkstoff werkstattname','SQL','V358__werkstoff_werkstattname.sql',1366763439,'basis-schema','2026-10-10 14:46:02',0,1),(101,'359','lieferantenpreise skala vier','SQL','V359__lieferantenpreise_skala_vier.sql',-648090906,'basis-schema','2026-10-10 14:46:02',0,1),(102,'360','lieferantenpreise 100er staffel wuerth','SQL','V360__lieferantenpreise_100er_staffel_wuerth.sql',-1688706947,'basis-schema','2026-10-10 14:46:02',0,1),(103,'361','artikel in projekt preis skala vier','SQL','V361__artikel_in_projekt_preis_skala_vier.sql',-678967185,'basis-schema','2026-10-10 14:46:02',0,1),(104,'362','firmeninformation firmenfarbe','SQL','V362__firmeninformation_firmenfarbe.sql',119550094,'basis-schema','2026-10-10 14:46:02',0,1),(105,'363','datensatz lock','SQL','V363__datensatz_lock.sql',149471975,'basis-schema','2026-10-10 14:46:02',0,1),(106,'364','aggregat versionsspalten','SQL','V364__aggregat_versionsspalten.sql',-1439313063,'basis-schema','2026-10-10 14:46:02',0,1),(107,'365','dokument lock entfernen','SQL','V365__dokument_lock_entfernen.sql',-490279496,'basis-schema','2026-10-10 14:46:02',0,1),(108,'366','datensatz lock entitaet typ enum','SQL','V366__datensatz_lock_entitaet_typ_enum.sql',2013165510,'basis-schema','2026-10-10 14:46:02',0,1),(109,'367','langzeitkrankmeldung','SQL','V367__langzeitkrankmeldung.sql',265920906,'basis-schema','2026-10-10 14:46:02',0,1),(110,'368','zeitkonto datenfundament','SQL','V368__zeitkonto_datenfundament.sql',-1358887259,'basis-schema','2026-10-10 14:46:02',0,1),(111,'369','zeitkonto bestand versionieren','SQL','V369__zeitkonto_bestand_versionieren.sql',-1844300805,'basis-schema','2026-10-10 14:46:02',0,1),(112,'370','zeitkonto pausen','SQL','V370__zeitkonto_pausen.sql',53608955,'basis-schema','2026-10-10 14:46:02',0,1),(113,'371','zeitkonto altmodell entfernen','SQL','V371__zeitkonto_altmodell_entfernen.sql',673374152,'basis-schema','2026-10-10 14:46:02',0,1),(114,'372','monatsabschluss abwesenheit snapshot','SQL','V372__monatsabschluss_abwesenheit_snapshot.sql',1981181317,'basis-schema','2026-10-10 14:46:02',0,1),(115,'373','datev konfiguration','SQL','V373__datev_konfiguration.sql',-1378840815,'basis-schema','2026-10-10 14:46:02',0,1),(116,'374','ausgangsdokument pdf archiv','SQL','V374__ausgangsdokument_pdf_archiv.sql',1614813215,'basis-schema','2026-10-10 14:46:02',0,1),(117,'375','kasse buchungen und export','SQL','V375__kasse_buchungen_und_export.sql',-805909642,'basis-schema','2026-10-10 14:46:02',0,1),(118,'376','email draft attachments','SQL','V376__email_draft_attachments.sql',1064462286,'basis-schema','2026-10-10 14:46:02',0,1),(119,'400','telefon anbindung','SQL','V400__telefon_anbindung.sql',174190425,'basis-schema','2026-10-10 14:46:02',0,1),(120,'401','telefon steuerberater','SQL','V401__telefon_steuerberater.sql',1994233194,'basis-schema','2026-10-10 14:46:02',0,1),(121,'402','startabsender eines betriebs entfernen','SQL','V402__startabsender_eines_betriebs_entfernen.sql',-1928851200,'basis-schema','2026-10-10 14:46:02',0,1),(122,'403','lieferant dokument verknuepfung gesperrt','SQL','V403__lieferant_dokument_verknuepfung_gesperrt.sql',1327892353,'basis-schema','2026-10-10 14:46:02',0,1),(123,'404','lieferant dokument position','SQL','V404__lieferant_dokument_position.sql',2049982722,'basis-schema','2026-10-10 14:46:02',0,1),(124,'405','werkstoffzeugnis','SQL','V405__werkstoffzeugnis.sql',-472146746,'basis-schema','2026-10-10 14:46:02',0,1),(125,'406','abteilungen rechnungsrechte seed','SQL','V406__abteilungen_rechnungsrechte_seed.sql',504639203,'basis-schema','2026-10-10 14:46:02',0,1);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `formular_template_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dokumenttyp_enum` varchar(30) NOT NULL,
  `template_name` varchar(150) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKl9mghuhiorivc7ecvx7x64oe2` (`template_name`,`dokumenttyp_enum`,`user_id`),
  KEY `FKrqvgxham8n6b54ep6ybi1bx0a` (`user_id`),
  CONSTRAINT `FKrqvgxham8n6b54ep6ybi1bx0a` FOREIGN KEY (`user_id`) REFERENCES `frontend_user_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `formular_template_textbaustein_default` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_name` varchar(150) NOT NULL,
  `dokumenttyp` varchar(40) NOT NULL,
  `position` varchar(8) NOT NULL,
  `textbaustein_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_fttd_lookup` (`template_name`,`dokumenttyp`,`position`,`sort_order`),
  KEY `idx_fttd_textbaustein` (`textbaustein_id`),
  CONSTRAINT `fk_fttd_textbaustein` FOREIGN KEY (`textbaustein_id`) REFERENCES `textbaustein` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `frontend_user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `display_name` varchar(200) NOT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `short_code` varchar(50) DEFAULT NULL,
  `username` varchar(120) DEFAULT NULL,
  `default_signature_id` bigint DEFAULT NULL,
  `email_absender_id` bigint DEFAULT NULL,
  `mitarbeiter_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_frontend_user_profile_username` (`username`),
  UNIQUE KEY `UK_9lbmj7c9xn6mq0mauuxb8q0d9` (`mitarbeiter_id`),
  KEY `FKr832ttbmukk4sdbp46wmdmpvv` (`default_signature_id`),
  KEY `FKgy9nu6luuoih1woj77ivgqyoi` (`email_absender_id`),
  CONSTRAINT `FK8hy25aovgdk9kjt68i60ysan7` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `fk_frontend_user_email_absender` FOREIGN KEY (`email_absender_id`) REFERENCES `email_absender` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FKgy9nu6luuoih1woj77ivgqyoi` FOREIGN KEY (`email_absender_id`) REFERENCES `email_absender` (`id`),
  CONSTRAINT `FKr832ttbmukk4sdbp46wmdmpvv` FOREIGN KEY (`default_signature_id`) REFERENCES `email_signature` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `frontend_user_profile_role` (
  `frontend_user_profile_id` bigint NOT NULL,
  `role_name` enum('ADMIN','USER') NOT NULL,
  PRIMARY KEY (`frontend_user_profile_id`,`role_name`),
  CONSTRAINT `FKljm8yrdwbcil1a6ee4iairxtw` FOREIGN KEY (`frontend_user_profile_id`) REFERENCES `frontend_user_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gewerk` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `bg_name` varchar(255) NOT NULL,
  `bg_satz_prozent` decimal(5,2) NOT NULL,
  `aktiv` tinyint(1) NOT NULL DEFAULT '1',
  `bemerkung` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gewerk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `gewerk` VALUES (1,'Bauhauptgewerbe (Hochbau, Maurer, Beton)','BG BAU',5.46,1,'Richtwert - genauer Beitrag kommt aus dem Beitragsbescheid.'),(2,'Ausbau (Trockenbau, Putz, Stuck)','BG BAU',3.30,1,NULL),(3,'Dachdecker','BG BAU',7.70,1,NULL),(4,'Maler und Lackierer','BG BAU',3.30,1,NULL),(5,'Geruestbau','BG BAU',6.80,1,NULL),(6,'Fliesen-, Platten- und Mosaikleger','BG BAU',5.50,1,NULL),(7,'Tischler / Schreiner','BGHM',1.13,1,NULL),(8,'Metallbau / Schlosserei','BGHM',1.85,1,NULL),(9,'Kfz-Werkstatt','BGHM',1.85,1,NULL),(10,'Elektroinstallation','BG ETEM',1.10,1,NULL),(11,'Sanitaer / Heizung / Klima (SHK)','BG ETEM',2.10,1,NULL),(12,'Garten- und Landschaftsbau','SVLFG',3.40,1,NULL),(13,'Gebaeudereinigung','BG BAU',5.46,1,NULL),(14,'Andere / Sonstige','Individuell',3.00,1,'Platzhalter - bitte BG und Satz manuell eintragen.');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kalender_eintrag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aktualisiert_am` datetime(6) DEFAULT NULL,
  `beschreibung` varchar(2000) DEFAULT NULL,
  `datum` date NOT NULL,
  `ende_zeit` time(6) DEFAULT NULL,
  `erstellt_am` datetime(6) NOT NULL,
  `farbe` varchar(255) DEFAULT NULL,
  `ganztaegig` bit(1) NOT NULL,
  `start_zeit` time(6) DEFAULT NULL,
  `titel` varchar(255) NOT NULL,
  `anfrage_id` bigint DEFAULT NULL,
  `ersteller_id` bigint DEFAULT NULL,
  `kunde_id` bigint DEFAULT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  `projekt_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK825cvevrtryta7qgjmr138jt0` (`anfrage_id`),
  KEY `FKfcwj8kcgklypce1r1miuo2g0m` (`ersteller_id`),
  KEY `FKiulrt6wgh5ykpy8ejpl2j80ev` (`kunde_id`),
  KEY `FKfqbhhs48w4fnvr9e4fir9aoat` (`lieferant_id`),
  KEY `FK23nredk9cp87futr9wbd2w79u` (`projekt_id`),
  CONSTRAINT `FK23nredk9cp87futr9wbd2w79u` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`),
  CONSTRAINT `FK825cvevrtryta7qgjmr138jt0` FOREIGN KEY (`anfrage_id`) REFERENCES `anfrage` (`id`),
  CONSTRAINT `FKfcwj8kcgklypce1r1miuo2g0m` FOREIGN KEY (`ersteller_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKfqbhhs48w4fnvr9e4fir9aoat` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`),
  CONSTRAINT `FKiulrt6wgh5ykpy8ejpl2j80ev` FOREIGN KEY (`kunde_id`) REFERENCES `kunde` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kalender_eintrag_teilnehmer` (
  `kalender_eintrag_id` bigint NOT NULL,
  `mitarbeiter_id` bigint NOT NULL,
  PRIMARY KEY (`kalender_eintrag_id`,`mitarbeiter_id`),
  KEY `FKm0jouf8mkfhwu9bttn3rjji1m` (`mitarbeiter_id`),
  CONSTRAINT `FKm0jouf8mkfhwu9bttn3rjji1m` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKqlfi7d92htoye3w09akhb1xtd` FOREIGN KEY (`kalender_eintrag_id`) REFERENCES `kalender_eintrag` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kasse_einstellung` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mindestbestand` decimal(10,2) NOT NULL DEFAULT '0.00',
  `ehegattengehalt_aktiv` tinyint(1) NOT NULL DEFAULT '0',
  `ehegattengehalt_betrag` decimal(10,2) DEFAULT NULL,
  `ehegattengehalt_tag` int DEFAULT NULL,
  `ehegattengehalt_empfaenger_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `privateinlage_sachkonto_id` bigint DEFAULT NULL,
  `letzte_buchung_jahrmonat` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `aktualisiert_am` datetime DEFAULT NULL,
  `datev_beraternummer` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `datev_mandantennummer` varchar(5) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `wirtschaftsjahr_beginn_monat` int NOT NULL DEFAULT '1',
  `kassenkonto_nummer` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT '1000',
  `bankkonto_nummer` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT '1200',
  PRIMARY KEY (`id`),
  KEY `fk_kasse_privateinlage_konto` (`privateinlage_sachkonto_id`),
  CONSTRAINT `fk_kasse_privateinlage_konto` FOREIGN KEY (`privateinlage_sachkonto_id`) REFERENCES `sachkonto` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `kasse_einstellung` VALUES (1,0.00,0,NULL,NULL,NULL,NULL,NULL,'2026-10-10 14:45:42',NULL,NULL,1,'1000','1200');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kassenbuch_monatsabschluss` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `jahr` int NOT NULL,
  `monat` int NOT NULL,
  `abgeschlossen_am` datetime(6) NOT NULL,
  `abgeschlossen_von_id` bigint DEFAULT NULL,
  `anfangsbestand` decimal(15,2) NOT NULL,
  `endbestand` decimal(15,2) NOT NULL,
  `summe_einnahmen` decimal(15,2) NOT NULL,
  `summe_ausgaben` decimal(15,2) NOT NULL,
  `anzahl_belege` int NOT NULL,
  `erste_laufende_nummer` bigint DEFAULT NULL,
  `letzte_laufende_nummer` bigint DEFAULT NULL,
  `chain_index` bigint DEFAULT NULL,
  `entry_hash` char(64) DEFAULT NULL,
  `bemerkung` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_monatsabschluss_jahr_monat` (`jahr`,`monat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kassenzaehlung` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `gezaehlt_am` datetime(6) NOT NULL,
  `stichtag` date NOT NULL,
  `gezaehlter_bestand` decimal(15,2) NOT NULL,
  `rechnerischer_bestand` decimal(15,2) NOT NULL,
  `differenz` decimal(15,2) NOT NULL,
  `stueckelung_json` text,
  `bemerkung` varchar(1000) DEFAULT NULL,
  `ausgleich_beleg_id` bigint DEFAULT NULL,
  `erfasst_von_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kassenzaehlung_stichtag` (`stichtag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kategorie` (
  `id` int NOT NULL AUTO_INCREMENT,
  `beschreibung` varchar(255) DEFAULT NULL,
  `parent_kategorie_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7rsp0w04q1kipndasbaxkhds9` (`parent_kategorie_id`),
  CONSTRAINT `FK7rsp0w04q1kipndasbaxkhds9` FOREIGN KEY (`parent_kategorie_id`) REFERENCES `kategorie` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `kategorie` VALUES (7,'Blech',NULL),(8,'Glattblech',7),(9,'Riffelblech',7);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kategorie_rollen` (
  `kategorie_id` int NOT NULL,
  `rolle` enum('STAHLHANDEL','SCHRAUBEN_NORMTEILE','BESCHICHTUNG_VERZINKEN','LACKIERER','FERTIGTEILE_ZUKAUF','ALUMINIUM_NE','EDELSTAHL','WERKZEUG_VERBRAUCH','IT','SONSTIGER') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`kategorie_id`,`rolle`),
  CONSTRAINT `fk_kategorie_rollen_kategorie` FOREIGN KEY (`kategorie_id`) REFERENCES `kategorie` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kontakt_rufnummer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kunde_id` bigint DEFAULT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  `nummer_roh` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nummer_normalisiert` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `angelegt_am` datetime NOT NULL,
  `steuerberater_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_kontakt_rufnummer_kunde` (`kunde_id`),
  KEY `fk_kontakt_rufnummer_lieferant` (`lieferant_id`),
  KEY `idx_kontakt_rufnummer_nummer` (`nummer_normalisiert`),
  KEY `fk_kontakt_rufnummer_steuerberater` (`steuerberater_id`),
  CONSTRAINT `fk_kontakt_rufnummer_kunde` FOREIGN KEY (`kunde_id`) REFERENCES `kunde` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_kontakt_rufnummer_lieferant` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_kontakt_rufnummer_steuerberater` FOREIGN KEY (`steuerberater_id`) REFERENCES `steuerberater_kontakt` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kostenposition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `abrechnungs_jahr` int NOT NULL,
  `beleg_nummer` varchar(255) DEFAULT NULL,
  `berechnung` enum('BETRAG','VERBRAUCHSFAKTOR') DEFAULT NULL,
  `beschreibung` varchar(255) DEFAULT NULL,
  `betrag` decimal(19,2) DEFAULT NULL,
  `buchungsdatum` date DEFAULT NULL,
  `verbrauchsfaktor` decimal(19,6) DEFAULT NULL,
  `kostenstelle_id` bigint NOT NULL,
  `verteilungsschluessel_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5vo941bwtiwsi4sjr3eva5kwd` (`kostenstelle_id`),
  KEY `FKaaog4r7chwdxf5igxppd7owwj` (`verteilungsschluessel_id`),
  CONSTRAINT `FK5vo941bwtiwsi4sjr3eva5kwd` FOREIGN KEY (`kostenstelle_id`) REFERENCES `miete_kostenstelle` (`id`),
  CONSTRAINT `FKaaog4r7chwdxf5igxppd7owwj` FOREIGN KEY (`verteilungsschluessel_id`) REFERENCES `verteilungsschluessel` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `krankenkasse` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `kuerzel` varchar(32) DEFAULT NULL,
  `zusatzbeitrag_prozent` decimal(5,2) NOT NULL,
  `aktiv` tinyint(1) NOT NULL DEFAULT '1',
  `gueltig_ab` date DEFAULT NULL,
  `bemerkung` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_krankenkasse_name` (`name`),
  KEY `idx_krankenkasse_aktiv` (`aktiv`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `krankenkasse` VALUES (1,'Techniker Krankenkasse','TK',2.45,1,'2026-01-01',NULL),(2,'AOK Bayern','AOK-BY',2.70,1,'2026-01-01',NULL),(3,'AOK NordWest','AOK-NW',2.70,1,'2026-01-01',NULL),(4,'AOK Baden-Wuerttemberg','AOK-BW',2.70,1,'2026-01-01',NULL),(5,'Barmer','BARMER',3.49,1,'2026-01-01',NULL),(6,'DAK-Gesundheit','DAK',2.70,1,'2026-01-01',NULL),(7,'IKK classic','IKK',2.70,1,'2026-01-01',NULL),(8,'Knappschaft','KBS',2.70,1,'2026-01-01',NULL),(9,'BKK VBU','BKK-VBU',2.40,1,'2026-01-01',NULL),(10,'HEK - Hanseatische Krankenkasse','HEK',2.70,1,'2026-01-01',NULL),(11,'hkk Krankenkasse','HKK',1.84,1,'2026-01-01',NULL),(12,'mhplus Krankenkasse','MHPLUS',2.40,1,'2026-01-01',NULL),(13,'BIG direkt gesund','BIG',1.99,1,'2026-01-01',NULL);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kunde` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anrede` enum('HERR','FRAU','FAMILIE','FIRMA','DAMEN_HERREN') DEFAULT NULL,
  `ansprechspartner` varchar(255) DEFAULT NULL,
  `kundennummer` varchar(255) NOT NULL,
  `mobiltelefon` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `ort` varchar(255) DEFAULT NULL,
  `plz` varchar(255) DEFAULT NULL,
  `strasse` varchar(255) DEFAULT NULL,
  `telefon` varchar(255) DEFAULT NULL,
  `version` bigint DEFAULT '0',
  `zahlungsziel` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_hrpogg27jk88re0qc0a8hlfug` (`kundennummer`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kunde_notiz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kunde_id` bigint NOT NULL,
  `text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `erstellt_am` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kunde_notiz_kunde` (`kunde_id`,`erstellt_am`),
  CONSTRAINT `fk_kunde_notiz_kunde` FOREIGN KEY (`kunde_id`) REFERENCES `kunde` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kunden_emails` (
  `kunden_id` bigint NOT NULL,
  `email` varchar(255) NOT NULL,
  UNIQUE KEY `UK_rbeo8g0h6kse6liqmbrdidtfj` (`email`),
  KEY `FKocfqfi03pyffpsruvkng044x9` (`kunden_id`),
  CONSTRAINT `FKocfqfi03pyffpsruvkng044x9` FOREIGN KEY (`kunden_id`) REFERENCES `kunde` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kunden_zaehler` (
  `id` int NOT NULL,
  `naechste_nummer` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `kunden_zaehler` VALUES (1,1000);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `langzeitkrankmeldung` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mitarbeiter_id` bigint NOT NULL,
  `beginn` date NOT NULL,
  `ende` date DEFAULT NULL,
  `status` enum('LAUFEND','BEENDET','ABGEBROCHEN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `lohnfortzahlung_bis` date NOT NULL,
  `notiz` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_langzeitkrankmeldung_mitarbeiter_beginn` (`mitarbeiter_id`,`beginn`),
  CONSTRAINT `fk_langzeitkrankmeldung_mitarbeiter` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `langzeitkrankmeldung_phase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `langzeitkrankmeldung_id` bigint NOT NULL,
  `typ` enum('LOHNFORTZAHLUNG','KRANKENGELD','WIEDEREINGLIEDERUNG') COLLATE utf8mb4_unicode_ci NOT NULL,
  `von_datum` date NOT NULL,
  `bis_datum` date DEFAULT NULL,
  `stunden_pro_tag` decimal(4,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_langzeitkrankmeldung_phase_meldung_von` (`langzeitkrankmeldung_id`,`von_datum`),
  CONSTRAINT `fk_langzeitkrankmeldung_phase_meldung` FOREIGN KEY (`langzeitkrankmeldung_id`) REFERENCES `langzeitkrankmeldung` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `leistung` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beschreibung` text,
  `bezeichnung` varchar(255) NOT NULL,
  `einheit` enum('LAUFENDE_METER','QUADRATMETER','KILOGRAMM','STUECK') NOT NULL,
  `preis` decimal(19,2) DEFAULT NULL,
  `kategorie_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7hofs5fopj2sk601jk5m69nse` (`kategorie_id`),
  CONSTRAINT `FK7hofs5fopj2sk601jk5m69nse` FOREIGN KEY (`kategorie_id`) REFERENCES `produktkategorie` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferant_bild` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beschreibung` varchar(255) DEFAULT NULL,
  `erstellt_am` datetime(6) DEFAULT NULL,
  `gespeicherter_dateiname` varchar(255) NOT NULL,
  `original_dateiname` varchar(255) NOT NULL,
  `mitarbeiter_id` bigint DEFAULT NULL,
  `lieferant_id` bigint NOT NULL,
  `reklamation_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnbar2m456dfal5qvyr7i3lu1q` (`mitarbeiter_id`),
  KEY `FKdmj0fo47ntl2ckj3m9k5j6vgw` (`lieferant_id`),
  KEY `FK3jsl58pjxxlgi2g1awao4wx5p` (`reklamation_id`),
  CONSTRAINT `FK3jsl58pjxxlgi2g1awao4wx5p` FOREIGN KEY (`reklamation_id`) REFERENCES `lieferant_reklamation` (`id`),
  CONSTRAINT `FKdmj0fo47ntl2ckj3m9k5j6vgw` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`),
  CONSTRAINT `FKnbar2m456dfal5qvyr7i3lu1q` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferant_dokument` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ausgeblendet` bit(1) NOT NULL DEFAULT b'0',
  `gespeicherter_dateiname` varchar(255) DEFAULT NULL,
  `original_dateiname` varchar(255) DEFAULT NULL,
  `typ` enum('ANGEBOT','AUFTRAGSBESTAETIGUNG','LIEFERSCHEIN','RECHNUNG','GUTSCHRIFT','SONSTIG','BELEG','WERKSTOFFZEUGNIS') NOT NULL,
  `upload_datum` datetime(6) NOT NULL,
  `version` bigint DEFAULT '0',
  `attachment_id` bigint DEFAULT NULL,
  `beleg_id` bigint DEFAULT NULL,
  `lieferant_id` bigint NOT NULL,
  `uploaded_by_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKo26ih6io5iveeuwsq7vykh08o` (`attachment_id`),
  KEY `FKn3js7qygrabjp3p8v9dvnrmoa` (`beleg_id`),
  KEY `FK5fnimy797c9vpvc3si7h2ltsc` (`lieferant_id`),
  KEY `FKg21j111exuttor4np4xu4ot24` (`uploaded_by_id`),
  KEY `idx_lieferant_dokument_ausgeblendet` (`ausgeblendet`),
  KEY `idx_lieferant_dokument_beleg` (`beleg_id`),
  CONSTRAINT `FK5fnimy797c9vpvc3si7h2ltsc` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`),
  CONSTRAINT `fk_lieferant_dokument_beleg` FOREIGN KEY (`beleg_id`) REFERENCES `beleg` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FKg21j111exuttor4np4xu4ot24` FOREIGN KEY (`uploaded_by_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKn3js7qygrabjp3p8v9dvnrmoa` FOREIGN KEY (`beleg_id`) REFERENCES `beleg` (`id`),
  CONSTRAINT `FKo26ih6io5iveeuwsq7vykh08o` FOREIGN KEY (`attachment_id`) REFERENCES `email_attachment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferant_dokument_position` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `geschaeftsdokument_id` bigint NOT NULL,
  `position_nr` int NOT NULL,
  `positions_art` enum('WARE','NEBENKOSTEN','RABATT') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WARE',
  `externe_artikelnummer` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bezeichnung` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `werkstoff` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `charge` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `abmessung` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `menge` decimal(15,3) DEFAULT NULL,
  `mengeneinheit` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `einzelpreis` decimal(15,4) DEFAULT NULL,
  `preiseinheit` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gesamtpreis_netto` decimal(15,2) DEFAULT NULL,
  `projekt_id` bigint DEFAULT NULL,
  `kostenstelle_id` bigint DEFAULT NULL,
  `suchtext` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_ld_position_projekt` (`projekt_id`),
  KEY `fk_ld_position_kostenstelle` (`kostenstelle_id`),
  KEY `idx_ld_position_geschaeftsdokument` (`geschaeftsdokument_id`,`position_nr`),
  CONSTRAINT `fk_ld_position_geschaeftsdokument` FOREIGN KEY (`geschaeftsdokument_id`) REFERENCES `lieferant_geschaeftsdokument` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ld_position_kostenstelle` FOREIGN KEY (`kostenstelle_id`) REFERENCES `firma_kostenstelle` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ld_position_projekt` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferant_dokument_projekt_anteil` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `absoluter_betrag` decimal(12,2) DEFAULT NULL,
  `berechneter_betrag` decimal(12,2) DEFAULT NULL,
  `beschreibung` varchar(255) DEFAULT NULL,
  `prozent` int DEFAULT NULL,
  `streckung_jahre` int NOT NULL,
  `streckung_start_jahr` int DEFAULT NULL,
  `zugeordnet_am` datetime(6) DEFAULT NULL,
  `dokument_id` bigint NOT NULL,
  `kostenstelle_id` bigint DEFAULT NULL,
  `projekt_id` bigint DEFAULT NULL,
  `zugeordnet_von_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkxmiv0ydwaea4rsafh2sqgy7m` (`dokument_id`),
  KEY `FKo7j3pr0m7md9gh6cmi3iaxmnf` (`kostenstelle_id`),
  KEY `FK9s9oi3un5efau5swc1kcvq708` (`projekt_id`),
  KEY `FK8e8msmb5hg51mtnyy9wjdgyve` (`zugeordnet_von_user_id`),
  CONSTRAINT `FK8e8msmb5hg51mtnyy9wjdgyve` FOREIGN KEY (`zugeordnet_von_user_id`) REFERENCES `frontend_user_profile` (`id`),
  CONSTRAINT `FK9s9oi3un5efau5swc1kcvq708` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`),
  CONSTRAINT `fk_projekt_anteil_zugeordnet_von` FOREIGN KEY (`zugeordnet_von_user_id`) REFERENCES `frontend_user_profile` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FKkxmiv0ydwaea4rsafh2sqgy7m` FOREIGN KEY (`dokument_id`) REFERENCES `lieferant_dokument` (`id`),
  CONSTRAINT `FKo7j3pr0m7md9gh6cmi3iaxmnf` FOREIGN KEY (`kostenstelle_id`) REFERENCES `firma_kostenstelle` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferant_dokument_verknuepfung` (
  `dokument_id` bigint NOT NULL,
  `verknuepft_id` bigint NOT NULL,
  PRIMARY KEY (`dokument_id`,`verknuepft_id`),
  KEY `FK557cmofj4yh8o7v3iprjkrolx` (`verknuepft_id`),
  CONSTRAINT `FK557cmofj4yh8o7v3iprjkrolx` FOREIGN KEY (`verknuepft_id`) REFERENCES `lieferant_dokument` (`id`),
  CONSTRAINT `FKfuu9q0mrj8q3c4rc72h41nb6o` FOREIGN KEY (`dokument_id`) REFERENCES `lieferant_dokument` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferant_dokument_verknuepfung_gesperrt` (
  `dokument_id` bigint NOT NULL,
  `verknuepft_id` bigint NOT NULL,
  `gesperrt_am` datetime(6) NOT NULL,
  PRIMARY KEY (`dokument_id`,`verknuepft_id`),
  KEY `idx_ld_verkn_gesperrt_verknuepft` (`verknuepft_id`),
  CONSTRAINT `fk_ld_verkn_gesperrt_dokument` FOREIGN KEY (`dokument_id`) REFERENCES `lieferant_dokument` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ld_verkn_gesperrt_verknuepft` FOREIGN KEY (`verknuepft_id`) REFERENCES `lieferant_dokument` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferant_geschaeftsdokument` (
  `id` bigint NOT NULL,
  `ai_confidence` double DEFAULT NULL,
  `ai_raw_json` text,
  `analysiert_am` datetime(6) DEFAULT NULL,
  `bereits_gezahlt` bit(1) DEFAULT NULL,
  `bestellnummer` varchar(50) DEFAULT NULL,
  `betrag_brutto` decimal(12,2) DEFAULT NULL,
  `betrag_netto` decimal(12,2) DEFAULT NULL,
  `bezahlt` bit(1) NOT NULL,
  `bezahlt_am` date DEFAULT NULL,
  `datenquelle` varchar(255) DEFAULT NULL,
  `dokument_datum` date DEFAULT NULL,
  `dokument_nummer` varchar(50) DEFAULT NULL,
  `genehmigt` bit(1) NOT NULL,
  `lagerbestellung` bit(1) NOT NULL,
  `liefertermin` date DEFAULT NULL,
  `manuelle_pruefung_erforderlich` bit(1) NOT NULL,
  `mit_skonto` bit(1) DEFAULT NULL,
  `mwst_satz` decimal(5,4) DEFAULT NULL,
  `netto_tage` int DEFAULT NULL,
  `referenz_nummer` varchar(50) DEFAULT NULL,
  `skonto_prozent` decimal(5,2) DEFAULT NULL,
  `skonto_tage` int DEFAULT NULL,
  `tatsaechlich_gezahlt` decimal(12,2) DEFAULT NULL,
  `verifiziert` bit(1) DEFAULT NULL,
  `zahlungsart` varchar(50) DEFAULT NULL,
  `zahlungsziel` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `FK7mhykqqkvutp5xn2kq1nvte10` FOREIGN KEY (`id`) REFERENCES `lieferant_dokument` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferant_notiz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `erstellt_am` datetime(6) NOT NULL,
  `text` text NOT NULL,
  `lieferant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKny9ewg3u4n84j2n9452ojfn3c` (`lieferant_id`),
  CONSTRAINT `FKny9ewg3u4n84j2n9452ojfn3c` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferant_reklamation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beschreibung` text,
  `erstellt_am` datetime(6) NOT NULL,
  `status` enum('OFFEN','IN_BEARBEITUNG','ABGESCHLOSSEN','STORNIERT') NOT NULL,
  `version` bigint DEFAULT '0',
  `erstellt_von_id` bigint DEFAULT NULL,
  `lieferant_id` bigint NOT NULL,
  `lieferschein_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKs18k0x598vcgjadie04an4ykh` (`erstellt_von_id`),
  KEY `FKf4w70mefnf5c696mwhr3o07wy` (`lieferant_id`),
  KEY `FKi0vq2dxmy7twmr91rl4m9eq9` (`lieferschein_id`),
  CONSTRAINT `FKf4w70mefnf5c696mwhr3o07wy` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`),
  CONSTRAINT `FKi0vq2dxmy7twmr91rl4m9eq9` FOREIGN KEY (`lieferschein_id`) REFERENCES `lieferant_dokument` (`id`),
  CONSTRAINT `FKs18k0x598vcgjadie04an4ykh` FOREIGN KEY (`erstellt_von_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferanten` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alias_name` varchar(255) DEFAULT NULL,
  `bestellungen` int DEFAULT '0',
  `eigene_kundennummer` varchar(255) DEFAULT NULL,
  `ist_aktiv` bit(1) DEFAULT NULL,
  `lieferanten_typ` varchar(255) DEFAULT NULL,
  `lieferantenname` varchar(255) NOT NULL,
  `mobiltelefon` varchar(255) DEFAULT NULL,
  `ort` varchar(255) DEFAULT NULL,
  `plz` varchar(255) DEFAULT NULL,
  `start_zusammenarbeit` datetime(6) DEFAULT NULL,
  `strasse` varchar(255) DEFAULT NULL,
  `telefon` varchar(255) DEFAULT NULL,
  `version` bigint DEFAULT '0',
  `vertreter` varchar(255) DEFAULT NULL,
  `vorauskasse` bit(1) NOT NULL DEFAULT b'0',
  `standard_kostenstelle_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_9gi6q6aktx4lmbsk23mknb45p` (`lieferantenname`),
  KEY `FKgopne9hwl7n945f7ghdqc21mw` (`standard_kostenstelle_id`),
  CONSTRAINT `fk_lieferanten_standard_kostenstelle` FOREIGN KEY (`standard_kostenstelle_id`) REFERENCES `firma_kostenstelle` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FKgopne9hwl7n945f7ghdqc21mw` FOREIGN KEY (`standard_kostenstelle_id`) REFERENCES `firma_kostenstelle` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferanten_artikel_preise` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aktuell` bit(1) NOT NULL DEFAULT b'1',
  `erfasst_am` datetime(6) DEFAULT NULL,
  `externe_artikelnummer` varchar(255) DEFAULT NULL,
  `notiz` varchar(255) DEFAULT NULL,
  `preis` decimal(19,4) DEFAULT NULL,
  `preis_aenderungsdatum` datetime(6) DEFAULT NULL,
  `quelle` enum('MANUELL','ANGEBOT_EMAIL','CSV_IMPORT','RECHNUNG','SYSTEM','UNBEKANNT') NOT NULL DEFAULT 'UNBEKANNT',
  `artikel_id` bigint DEFAULT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_lap_lieferant_aen` (`lieferant_id`,`externe_artikelnummer`),
  KEY `ix_lap_artikel_aktuell` (`artikel_id`,`aktuell`,`preis`),
  KEY `ix_lap_verlauf` (`artikel_id`,`lieferant_id`,`preis_aenderungsdatum`),
  KEY `ix_lap_artikel` (`artikel_id`),
  KEY `ix_lap_lieferant` (`lieferant_id`),
  CONSTRAINT `FK9p4q5hy21v564dw1dpwpmy1t2` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`),
  CONSTRAINT `FKpduh3vh6vf524fyjqmtdf0i3p` FOREIGN KEY (`artikel_id`) REFERENCES `artikel` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferanten_artikel_preise_korrektur_v360` (
  `preis_id` bigint NOT NULL,
  `artikel_id` bigint DEFAULT NULL,
  `preis_alt` decimal(19,4) DEFAULT NULL,
  `preis_neu` decimal(19,4) DEFAULT NULL,
  `korrigiert_am` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`preis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferanten_emails` (
  `lieferanten_id` bigint NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  KEY `FK7n9pw9uq6twnqk2gvohrasjqp` (`lieferanten_id`),
  CONSTRAINT `FK7n9pw9uq6twnqk2gvohrasjqp` FOREIGN KEY (`lieferanten_id`) REFERENCES `lieferanten` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lieferanten_rollen` (
  `lieferant_id` bigint NOT NULL,
  `rolle` enum('STAHLHANDEL','SCHRAUBEN_NORMTEILE','BESCHICHTUNG_VERZINKEN','LACKIERER','FERTIGTEILE_ZUKAUF','ALUMINIUM_NE','EDELSTAHL','WERKZEUG_VERBRAUCH','IT','SONSTIGER') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`lieferant_id`,`rolle`),
  CONSTRAINT `fk_lieferanten_rollen_lieferant` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lohnabrechnung` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_raw_json` text,
  `bruttolohn` decimal(10,2) DEFAULT NULL,
  `gespeicherter_dateiname` varchar(255) NOT NULL,
  `import_datum` datetime(6) NOT NULL,
  `jahr` int NOT NULL,
  `monat` int NOT NULL,
  `nettolohn` decimal(10,2) DEFAULT NULL,
  `original_dateiname` varchar(255) DEFAULT NULL,
  `status` enum('IMPORTIERT','WIRD_ANALYSIERT','ANALYSIERT','FEHLER') NOT NULL,
  `mitarbeiter_id` bigint NOT NULL,
  `email_id` bigint DEFAULT NULL,
  `steuerberater_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lohnabrechnung_mitarbeiter` (`mitarbeiter_id`),
  KEY `idx_lohnabrechnung_periode` (`jahr`,`monat`),
  KEY `idx_lohnabrechnung_steuerberater` (`steuerberater_id`),
  KEY `FKrvbnxfacldipkbyrkt9w8ylex` (`email_id`),
  CONSTRAINT `FK2nhfk54qj9ltp98w3uu7h7yd2` FOREIGN KEY (`steuerberater_id`) REFERENCES `steuerberater_kontakt` (`id`),
  CONSTRAINT `FK9yhpq6453cxflovfab1p5nsxa` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKrvbnxfacldipkbyrkt9w8ylex` FOREIGN KEY (`email_id`) REFERENCES `email` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `materialkosten` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beschreibung` varchar(255) DEFAULT NULL,
  `betrag` decimal(19,2) NOT NULL,
  `externe_artikelnummer` varchar(255) DEFAULT NULL,
  `monat` int DEFAULT NULL,
  `rechnungsnummer` varchar(255) DEFAULT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  `projekt_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgbrmg6ai07b0n46ff48knxx7c` (`lieferant_id`),
  KEY `FK334hjof01f74lut1bq6n8t5lo` (`projekt_id`),
  CONSTRAINT `FK334hjof01f74lut1bq6n8t5lo` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`),
  CONSTRAINT `FKgbrmg6ai07b0n46ff48knxx7c` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `miete_kostenstelle` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beschreibung` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `umlagefaehig` bit(1) NOT NULL,
  `mietobjekt_id` bigint NOT NULL,
  `standard_schluessel_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9grn5tr8ms48v8p7xqxmhpe0t` (`mietobjekt_id`,`name`),
  KEY `FKds25jf6f8jo0wa3nmptgcnuv0` (`standard_schluessel_id`),
  CONSTRAINT `FKds25jf6f8jo0wa3nmptgcnuv0` FOREIGN KEY (`standard_schluessel_id`) REFERENCES `verteilungsschluessel` (`id`),
  CONSTRAINT `FKs6hr5d8ibqwc878l2v7buysip` FOREIGN KEY (`mietobjekt_id`) REFERENCES `mietobjekt` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mietobjekt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `ort` varchar(255) DEFAULT NULL,
  `plz` varchar(255) DEFAULT NULL,
  `strasse` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_b8rs3wclfpshqjauixcqro3pt` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mietpartei` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) DEFAULT NULL,
  `monatlicher_vorschuss` decimal(19,2) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `rolle` enum('EIGENTUEMER','MIETER') NOT NULL,
  `telefon` varchar(255) DEFAULT NULL,
  `mietobjekt_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1xh5y59jica5j83pantmqo6mm` (`mietobjekt_id`,`name`),
  CONSTRAINT `FKpo7mpkdxv05wxnqxaghkl5uns` FOREIGN KEY (`mietobjekt_id`) REFERENCES `mietobjekt` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mitarbeiter` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aktiv` bit(1) NOT NULL,
  `art` enum('MENSCH','SYSTEM') NOT NULL DEFAULT 'MENSCH',
  `beschaeftigungsart` enum('REGULAER','MINIJOB','GF_SV_PFLICHTIG','GF_SV_FREI') NOT NULL DEFAULT 'REGULAER',
  `eintrittsdatum` date DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `festnetz` varchar(255) DEFAULT NULL,
  `fuehrt_zeitkonto` bit(1) NOT NULL DEFAULT b'1',
  `geburtstag` date DEFAULT NULL,
  `geldwert_vorteil_monat` decimal(12,2) DEFAULT NULL,
  `ist_geschaeftsfuehrer` bit(1) NOT NULL DEFAULT b'0',
  `jahres_urlaub` int DEFAULT NULL,
  `kalkulatorischer_lohn_monat` decimal(12,2) DEFAULT NULL,
  `kinderlos` bit(1) NOT NULL DEFAULT b'0',
  `login_token` varchar(255) DEFAULT NULL,
  `nachname` varchar(255) NOT NULL,
  `ort` varchar(255) DEFAULT NULL,
  `plz` varchar(255) DEFAULT NULL,
  `qualifikation` enum('AUSZUBILDENDER','FACHARBEITER','MEISTER') DEFAULT NULL,
  `resturlaub_vorjahr` int DEFAULT NULL,
  `strasse` varchar(255) DEFAULT NULL,
  `stundenlohn` decimal(10,2) DEFAULT NULL,
  `telefon` varchar(255) DEFAULT NULL,
  `urlaubs_korrektur` int DEFAULT NULL,
  `version` bigint DEFAULT '0',
  `vorname` varchar(255) NOT NULL,
  `krankenkasse_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_bl4mjnd867q4c670vc14te7np` (`login_token`),
  KEY `FK28s44gxbnll5cni8jjse68nq1` (`krankenkasse_id`),
  CONSTRAINT `FK28s44gxbnll5cni8jjse68nq1` FOREIGN KEY (`krankenkasse_id`) REFERENCES `krankenkasse` (`id`),
  CONSTRAINT `fk_mitarbeiter_krankenkasse` FOREIGN KEY (`krankenkasse_id`) REFERENCES `krankenkasse` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `mitarbeiter` VALUES (1,0x01,'SYSTEM','REGULAER',NULL,NULL,NULL,0x00,NULL,NULL,0x00,NULL,NULL,0x00,'__SYSTEM_FUNNEL__','Webseite',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'System',NULL);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mitarbeiter_abteilung` (
  `mitarbeiter_id` bigint NOT NULL,
  `abteilung_id` bigint NOT NULL,
  PRIMARY KEY (`mitarbeiter_id`,`abteilung_id`),
  KEY `FK4mo0bw7rs0hulyfhqvfwledlv` (`abteilung_id`),
  CONSTRAINT `FK4mo0bw7rs0hulyfhqvfwledlv` FOREIGN KEY (`abteilung_id`) REFERENCES `abteilung` (`id`),
  CONSTRAINT `FKp3306e0e54tyw98r0tqhp9qae` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mitarbeiter_dokument` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dateigroesse` bigint DEFAULT NULL,
  `dateityp` varchar(255) DEFAULT NULL,
  `dokument_gruppe` enum('BILDER','GESCHAEFTSDOKUMENTE','PLANUNGSDOKUMENTE','KALKULATIONSDOKUMENTE','DOKUMENTATION_1090','EINGANGSRECHNUNGEN','DIVERSE_DOKUMENTE') NOT NULL,
  `email_versand_datum` date DEFAULT NULL,
  `gespeicherter_dateiname` varchar(255) NOT NULL,
  `original_dateiname` varchar(255) NOT NULL,
  `upload_datum` date DEFAULT NULL,
  `mitarbeiter_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_4nhida8qobup263pylmokx11y` (`gespeicherter_dateiname`),
  KEY `FKc2g6vg1xmyls40mfplx0kid67` (`mitarbeiter_id`),
  CONSTRAINT `FKc2g6vg1xmyls40mfplx0kid67` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mitarbeiter_notiz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `erstellt_am` datetime(6) NOT NULL,
  `inhalt` text,
  `mitarbeiter_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6lxmk16cci1suaujkgw14ouj4` (`mitarbeiter_id`),
  CONSTRAINT `FK6lxmk16cci1suaujkgw14ouj4` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mitarbeiter_stundenlohn` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mitarbeiter_id` bigint NOT NULL,
  `stundenlohn` decimal(10,2) NOT NULL,
  `gueltig_ab` date NOT NULL,
  `bemerkung` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stundenlohn_mitarbeiter_datum` (`mitarbeiter_id`,`gueltig_ab`),
  KEY `idx_stundenlohn_mitarbeiter_datum` (`mitarbeiter_id`,`gueltig_ab`),
  CONSTRAINT `fk_stundenlohn_mitarbeiter` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `monats_saldo` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `abwesenheits_stunden` decimal(10,2) NOT NULL,
  `berechnet_am` datetime(6) NOT NULL,
  `feiertags_stunden` decimal(10,2) NOT NULL,
  `festgeschrieben` bit(1) NOT NULL DEFAULT b'0',
  `festgeschrieben_am` datetime(6) DEFAULT NULL,
  `fortbildung_stunden` decimal(10,2) DEFAULT NULL,
  `gueltig` bit(1) NOT NULL,
  `ist_stunden` decimal(10,2) NOT NULL,
  `jahr` int NOT NULL,
  `korrektur_stunden` decimal(10,2) NOT NULL,
  `krankengeld_stunden` decimal(10,2) DEFAULT NULL,
  `krankheit_stunden` decimal(10,2) DEFAULT NULL,
  `monat` int NOT NULL,
  `soll_stunden` decimal(10,2) NOT NULL,
  `urlaub_stunden` decimal(10,2) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `wiedereingliederung_stunden` decimal(10,2) DEFAULT NULL,
  `zeitausgleich_stunden` decimal(10,2) DEFAULT NULL,
  `festgeschrieben_von_mitarbeiter_id` bigint DEFAULT NULL,
  `mitarbeiter_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monats_saldo_mitarbeiter_jahr_monat` (`mitarbeiter_id`,`jahr`,`monat`),
  KEY `FK1iflfii6yokov9l2cmv36m6a3` (`festgeschrieben_von_mitarbeiter_id`),
  CONSTRAINT `FK1iflfii6yokov9l2cmv36m6a3` FOREIGN KEY (`festgeschrieben_von_mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FK9hw3giv6y9wiyunwnwdo3tjgf` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `fk_monats_saldo_festgeschrieben_von` FOREIGN KEY (`festgeschrieben_von_mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `monatsabschluss_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mitarbeiter_id` bigint NOT NULL,
  `jahr` int NOT NULL,
  `monat` int NOT NULL,
  `aktion` enum('ABSCHLIESSEN','OEFFNEN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `akteur_id` bigint NOT NULL,
  `zeitpunkt` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_monatsabschluss_audit_akteur` (`akteur_id`),
  KEY `idx_monatsabschluss_audit_monat` (`mitarbeiter_id`,`jahr`,`monat`,`zeitpunkt`,`id`),
  CONSTRAINT `fk_monatsabschluss_audit_akteur` FOREIGN KEY (`akteur_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `fk_monatsabschluss_audit_mitarbeiter` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ooo_reply_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `schedule_id` bigint NOT NULL,
  `sender_address` varchar(320) COLLATE utf8mb4_unicode_ci NOT NULL,
  `replied_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ooo_reply_log_schedule_sender` (`schedule_id`,`sender_address`),
  CONSTRAINT `fk_ooo_reply_log_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `out_of_office_schedule` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `out_of_office_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `body_template` longtext,
  `end_at` date NOT NULL,
  `start_at` date NOT NULL,
  `subject_template` varchar(300) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `signature_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKptqv7sqf2u8v5wky19f3uauu0` (`signature_id`),
  CONSTRAINT `FKptqv7sqf2u8v5wky19f3uauu0` FOREIGN KEY (`signature_id`) REFERENCES `email_signature` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `produktkategorie` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beschreibung` text,
  `bezeichnung` varchar(255) NOT NULL,
  `bild_url` varchar(255) DEFAULT NULL,
  `verrechnungseinheit` enum('LAUFENDE_METER','QUADRATMETER','KILOGRAMM','STUECK') NOT NULL,
  `version` bigint DEFAULT '0',
  `parent_kategorie_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbtgb9v2fmjrsvd5pfpl27voty` (`parent_kategorie_id`),
  CONSTRAINT `FKbtgb9v2fmjrsvd5pfpl27voty` FOREIGN KEY (`parent_kategorie_id`) REFERENCES `produktkategorie` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projekt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `abgeschlossen` bit(1) NOT NULL,
  `abgeschlossen_manuell` bit(1) NOT NULL DEFAULT b'0',
  `abschlussdatum` date DEFAULT NULL,
  `anlegedatum` date NOT NULL,
  `auftragsnummer` varchar(255) NOT NULL,
  `bauvorhaben` varchar(255) NOT NULL,
  `bezahlt` bit(1) NOT NULL,
  `bild_url` varchar(255) DEFAULT NULL,
  `brutto_preis` decimal(38,2) NOT NULL,
  `kurzbeschreibung` text,
  `ort` varchar(255) DEFAULT NULL,
  `plz` varchar(255) DEFAULT NULL,
  `projekt_art` enum('PAUSCHAL','REGIE','INTERN','GARANTIE') NOT NULL,
  `strasse` varchar(255) DEFAULT NULL,
  `version` bigint DEFAULT '0',
  `kunden_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_ddj7u9qa7ovyydmjeooycnkb0` (`auftragsnummer`),
  KEY `FKj0obnxobkta8gnjhn4hq6bdlt` (`kunden_id`),
  CONSTRAINT `FKj0obnxobkta8gnjhn4hq6bdlt` FOREIGN KEY (`kunden_id`) REFERENCES `kunde` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projekt_dokument` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dateigroesse` bigint DEFAULT NULL,
  `dateityp` varchar(255) DEFAULT NULL,
  `dokument_gruppe` enum('BILDER','GESCHAEFTSDOKUMENTE','PLANUNGSDOKUMENTE','KALKULATIONSDOKUMENTE','DOKUMENTATION_1090','EINGANGSRECHNUNGEN','DIVERSE_DOKUMENTE') NOT NULL,
  `email_versand_datum` date DEFAULT NULL,
  `gespeicherter_dateiname` varchar(255) NOT NULL,
  `original_dateiname` varchar(255) NOT NULL,
  `upload_datum` date DEFAULT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  `projekt` bigint DEFAULT NULL,
  `uploaded_by_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_245cyxm7sta9uw57hkdmtn4jy` (`gespeicherter_dateiname`),
  KEY `FKjkeyqk6vd8g3gd2uxxb7cdjwm` (`lieferant_id`),
  KEY `FKs3kbr4k2j7s0rmjlav1660el8` (`projekt`),
  KEY `FKk12opivflsv1abgvokn1u56f3` (`uploaded_by_id`),
  CONSTRAINT `FKjkeyqk6vd8g3gd2uxxb7cdjwm` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`),
  CONSTRAINT `FKk12opivflsv1abgvokn1u56f3` FOREIGN KEY (`uploaded_by_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKs3kbr4k2j7s0rmjlav1660el8` FOREIGN KEY (`projekt`) REFERENCES `projekt` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projekt_geschaeftsdokument` (
  `bezahlt` bit(1) NOT NULL,
  `brutto_betrag` decimal(38,2) DEFAULT NULL,
  `dokumentid` varchar(255) NOT NULL,
  `faelligkeitsdatum` date DEFAULT NULL,
  `geschaeftsdokumentart` varchar(255) NOT NULL,
  `mahnstufe` enum('ZAHLUNGSERINNERUNG','ERSTE_MAHNUNG','ZWEITE_MAHNUNG') DEFAULT NULL,
  `rechnungsdatum` date DEFAULT NULL,
  `system_generiert` bit(1) NOT NULL DEFAULT b'0',
  `id` bigint NOT NULL,
  `referenz_dokument_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1lnans707maovpsgpkgxnyg84` (`referenz_dokument_id`),
  CONSTRAINT `FK1lnans707maovpsgpkgxnyg84` FOREIGN KEY (`referenz_dokument_id`) REFERENCES `projekt_geschaeftsdokument` (`id`),
  CONSTRAINT `FK4pmmorcrh4u9c4l557t3l4ch` FOREIGN KEY (`id`) REFERENCES `projekt_dokument` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projekt_kunden_emails` (
  `projekt_id` bigint NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  KEY `FK7bt6ot4v6rget7ei5ur19u0y8` (`projekt_id`),
  CONSTRAINT `FK7bt6ot4v6rget7ei5ur19u0y8` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projekt_notiz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `erstellt_am` datetime(6) NOT NULL,
  `mobile_sichtbar` bit(1) NOT NULL,
  `notiz` varchar(4000) NOT NULL,
  `nur_fuer_ersteller` bit(1) NOT NULL,
  `mitarbeiter_id` bigint NOT NULL,
  `projekt_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKtco2khbngbo2vh7pmesx8cqbv` (`mitarbeiter_id`),
  KEY `FKl0rf54prfq2tjktp6svbe39wk` (`projekt_id`),
  CONSTRAINT `FKl0rf54prfq2tjktp6svbe39wk` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`),
  CONSTRAINT `FKtco2khbngbo2vh7pmesx8cqbv` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projekt_notiz_bild` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dateityp` varchar(255) DEFAULT NULL,
  `erstellt_am` datetime(6) NOT NULL,
  `gespeicherter_dateiname` varchar(255) NOT NULL,
  `original_dateiname` varchar(255) DEFAULT NULL,
  `notiz_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKk561w9l0apru61989md1tgkeb` (`notiz_id`),
  CONSTRAINT `FKk561w9l0apru61989md1tgkeb` FOREIGN KEY (`notiz_id`) REFERENCES `projekt_notiz` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projekt_produktkategorie` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `menge` decimal(19,2) NOT NULL,
  `produktkategorie_id` bigint NOT NULL,
  `projekt_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7cgq3q42get8289lvlxrncsrv` (`produktkategorie_id`),
  KEY `FKkv1qwmsdlkwmmjsr8ky93nchk` (`projekt_id`),
  CONSTRAINT `FK7cgq3q42get8289lvlxrncsrv` FOREIGN KEY (`produktkategorie_id`) REFERENCES `produktkategorie` (`id`),
  CONSTRAINT `FKkv1qwmsdlkwmmjsr8ky93nchk` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `push_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `auth` varchar(512) NOT NULL,
  `endpoint` varchar(2048) NOT NULL,
  `erstellt_am` datetime(6) NOT NULL,
  `p256dh` varchar(512) NOT NULL,
  `mitarbeiter_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1nrjw1akxmtj9ftoy163x09l6` (`mitarbeiter_id`),
  CONSTRAINT `FK1nrjw1akxmtj9ftoy163x09l6` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `raum` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beschreibung` varchar(255) DEFAULT NULL,
  `flaeche_quadratmeter` decimal(38,2) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `mietobjekt_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKemtrjhuoqwfmcxaxqgd6h9d5i` (`mietobjekt_id`,`name`),
  CONSTRAINT `FKd4ykw5sigm2uxqlsg497sqruk` FOREIGN KEY (`mietobjekt_id`) REFERENCES `mietobjekt` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sachkonto` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nummer` varchar(20) DEFAULT NULL,
  `bezeichnung` varchar(120) NOT NULL,
  `konto_typ` enum('AUFWAND','ERTRAG','PRIVAT','NEUTRAL') NOT NULL,
  `beschreibung` varchar(500) DEFAULT NULL,
  `aktiv` tinyint(1) NOT NULL DEFAULT '1',
  `sortierung` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sachkonto_bezeichnung` (`bezeichnung`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sachkonto` VALUES (1,'3400','Materialeinkauf','AUFWAND','Material, Baustoffe, Rohstoffe',1,10),(2,'4400','Werkzeug & Kleingeraete','AUFWAND','Werkzeug, Kleingeraete (sofort abschreibbar)',1,20),(3,'4530','Fahrzeugkosten','AUFWAND','Kraftstoff, Wartung, Reparaturen, KFZ-Versicherung',1,30),(4,'4910','Telefon & Internet','AUFWAND','Mobilfunk, Festnetz, Internet',1,40),(5,'4930','Buerobedarf','AUFWAND','Papier, Toner, Stifte, Software',1,50),(6,'4940','Reinigung','AUFWAND','Putzmittel, Reinigungsdienst',1,60),(7,'4945','Verpflegung & Bewirtung','AUFWAND','Geschaeftsessen, Verpflegung Mitarbeiter',1,70),(8,'4948','Reisekosten','AUFWAND','Hotel, Bahn, Spesen',1,80),(9,'4380','Versicherungen','AUFWAND','Betriebsversicherungen (ohne KFZ)',1,90),(10,'4360','Werbung & Marketing','AUFWAND','Anzeigen, Website, Visitenkarten',1,100),(11,'4980','Sonstiger Aufwand','AUFWAND','Diverse betriebliche Aufwendungen',1,200),(12,'8400','Erloese 19%','ERTRAG','Steuerpflichtige Erloese 19% (Bar/Karte/Bank)',1,300),(13,'8300','Erloese 7%','ERTRAG','Steuerpflichtige Erloese 7%',1,310),(14,'1800','Privatentnahme','PRIVAT','Bar-Entnahme durch Inhaber',1,400),(15,'1810','Privateinlage','PRIVAT','Einlage durch Inhaber',1,410),(16,'1200','Bank-Kassen-Umbuchung','NEUTRAL','Bar abgehoben/eingezahlt; keine GuV-Wirkung',1,500),(17,'1700','Durchlaufende Posten','NEUTRAL','Treuhand, Kautionen, durchlaufende Auslagen',1,510),(18,'3100','Fremdleistungen / Subunternehmer','AUFWAND','Bezahlte Rechnungen von Sub-Handwerkern und Dienstleistern',1,11),(19,'3300','Wareneingang 19%','AUFWAND','Handelswaren / Material zum Weiterverkauf (19% VSt)',1,12),(20,'3735','Skonti Aufwand','AUFWAND','Gewaehrte Skonti / Boni an Kunden',1,13),(21,'4120','Loehne & Gehaelter','AUFWAND','Bruttoloehne und Gehaelter der Mitarbeiter',1,21),(22,'4130','Sozialabgaben (AG-Anteil)','AUFWAND','Arbeitgeberanteil zur Sozialversicherung',1,22),(23,'4140','Berufsgenossenschaft','AUFWAND','Beitraege zur BG (BG BAU, BG ETEM, ...)',1,23),(24,'4150','Aushilfsloehne','AUFWAND','Minijobs, Aushilfen, kurzfristig Beschaeftigte',1,24),(25,'4665','Berufskleidung','AUFWAND','Arbeitskleidung, Schutzkleidung, Sicherheitsschuhe',1,25),(26,'4946','Fortbildung & Schulung','AUFWAND','Lehrgaenge, Meisterkurse, Sicherheitsschulungen',1,26),(27,'4210','Miete & Pacht (Geschaeft)','AUFWAND','Miete fuer Werkstatt, Lager, Buero',1,31),(28,'4240','Strom, Gas, Wasser','AUFWAND','Energie- und Wasserkosten Betriebsstaette',1,32),(29,'4250','Instandhaltung Gebaeude','AUFWAND','Reparaturen am Geschaeftsgebaeude',1,33),(30,'4650','Bewirtung geschaeftlich (70%)','AUFWAND','Bewirtungsbelege mit Kundenbezug, 70% abzugsfaehig',1,71),(31,'4630','Geschenke abzugsfaehig','AUFWAND','Kundengeschenke bis 50 EUR netto',1,72),(32,'4635','Geschenke nicht abzugsfaehig','AUFWAND','Geschenke ueber 50 EUR netto',1,73),(33,'4920','Porto','AUFWAND','Briefporto, Paketversand',1,41),(34,'4925','Software & IT-Abos','AUFWAND','Cloud-Software, Lizenzen, Office-Abos',1,42),(35,'4955','Buchfuehrungs- & Steuerberatung','AUFWAND','Honorare Steuerberater, Buchfuehrungsservice',1,91),(36,'4957','Rechts- & Beratungskosten','AUFWAND','Anwaltskosten, Unternehmensberatung',1,92),(37,'4390','Beitraege IHK / HWK / Innung','AUFWAND','Pflichtbeitraege Kammer, Innung, Verbaende',1,93),(38,'4970','Bankgebuehren & Kontofuehrung','AUFWAND','Kontofuehrung, Kartengebuehren, Auslandsspesen',1,101),(39,'4975','Zinsaufwand','AUFWAND','Zinsen fuer Betriebskredite, Kontokorrent',1,102),(40,'4830','Abschreibung Anlagen (AfA)','AUFWAND','Planmaessige Abschreibung Sachanlagen',1,110),(41,'4855','Abschreibung GWG','AUFWAND','Sofortabschreibung geringwertiger Wirtschaftsgueter (bis 800 EUR)',1,111),(42,'8338','Erloese steuerfrei (Reverse Charge)','ERTRAG','Bauleistungen an andere Unternehmer (§ 13b UStG)',1,320),(43,'8125','Erloese steuerfrei innergem.','ERTRAG','Innergemeinschaftliche Lieferungen (EU)',1,330),(44,'8736','Skontoertraege','ERTRAG','Erhaltene Skonti von Lieferanten',1,340),(45,'8100','Mieteinnahmen','ERTRAG','Vermietung von Raeumen / Gegenstaenden',1,350),(46,'1820','Privatsteuer (Einkommensteuer)','PRIVAT','Ueberwiesene Einkommensteuer-Vorauszahlung an FA',1,420),(47,'1830','Privatanteil KFZ','PRIVAT','Private Nutzung Firmenfahrzeug',1,430),(48,'1840','Privatanteil Telefon','PRIVAT','Privatnutzungsanteil Festnetz / Mobilfunk',1,440),(49,'1600','Geldtransit','NEUTRAL','Geld unterwegs zwischen Kasse / Bank (Verrechnung)',1,520);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schnittbilder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bild_url_schnittbild` varchar(255) NOT NULL,
  `form` varchar(255) NOT NULL,
  `kategorie_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_m4lwf19ouejdp0q21blxr2boq` (`bild_url_schnittbild`),
  UNIQUE KEY `UK_patwj8ajg6wddrpdjnn2d94pp` (`form`),
  KEY `FKb0r6fjh4pb8c0b44j1lbqtuca` (`kategorie_id`),
  CONSTRAINT `FKb0r6fjh4pb8c0b44j1lbqtuca` FOREIGN KEY (`kategorie_id`) REFERENCES `kategorie` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seen_sender_domain` (
  `domain` varchar(255) NOT NULL,
  `first_seen` datetime NOT NULL,
  `email_count` int NOT NULL DEFAULT '1',
  PRIMARY KEY (`domain`),
  KEY `idx_seen_sender_domain_first_seen` (`first_seen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `spam_model_stats` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_key` varchar(50) NOT NULL,
  `stat_value` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_spam_model_stats_key` (`stat_key`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `spam_model_stats` VALUES (1,'total_spam',0),(2,'total_ham',0);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `spam_token_count` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(100) NOT NULL,
  `spam_count` int NOT NULL DEFAULT '0',
  `ham_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_spam_token_unique` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sprachnachricht` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anrufbeantworter` int NOT NULL,
  `zeitpunkt` datetime NOT NULL,
  `nummer_roh` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `nummer_normalisiert` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dauer_sekunden` int NOT NULL DEFAULT '0',
  `datei_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `abgehoert_am` datetime DEFAULT NULL,
  `abgehoert_von` bigint DEFAULT NULL,
  `anruf_id` bigint DEFAULT NULL,
  `kunde_id` bigint DEFAULT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  `zuordnung` enum('AUTOMATISCH','MANUELL','KEINE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'KEINE',
  `angelegt_am` datetime NOT NULL,
  `steuerberater_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sprachnachricht` (`anrufbeantworter`,`zeitpunkt`,`nummer_roh`),
  KEY `fk_sprachnachricht_abgehoert_von` (`abgehoert_von`),
  KEY `fk_sprachnachricht_anruf` (`anruf_id`),
  KEY `fk_sprachnachricht_kunde` (`kunde_id`),
  KEY `fk_sprachnachricht_lieferant` (`lieferant_id`),
  KEY `idx_sprachnachricht_nummer` (`nummer_normalisiert`),
  KEY `idx_sprachnachricht_zeitpunkt` (`zeitpunkt`),
  KEY `fk_sprachnachricht_steuerberater` (`steuerberater_id`),
  CONSTRAINT `fk_sprachnachricht_abgehoert_von` FOREIGN KEY (`abgehoert_von`) REFERENCES `frontend_user_profile` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_sprachnachricht_anruf` FOREIGN KEY (`anruf_id`) REFERENCES `telefon_anruf` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_sprachnachricht_kunde` FOREIGN KEY (`kunde_id`) REFERENCES `kunde` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_sprachnachricht_lieferant` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_sprachnachricht_steuerberater` FOREIGN KEY (`steuerberater_id`) REFERENCES `steuerberater_kontakt` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `steuerberater_ansprechpartner` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `steuerberater_id` bigint NOT NULL,
  `anrede` enum('HERR','FRAU','FAMILIE','FIRMA','DAMEN_HERREN') DEFAULT NULL,
  `vorname` varchar(255) DEFAULT NULL,
  `nachname` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `telefon` varchar(64) DEFAULT NULL,
  `ist_lohn_ansprechpartner` tinyint(1) NOT NULL DEFAULT '0',
  `notizen` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sb_ap_steuerberater` (`steuerberater_id`),
  CONSTRAINT `fk_sb_ap_steuerberater` FOREIGN KEY (`steuerberater_id`) REFERENCES `steuerberater_kontakt` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `steuerberater_kontakt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aktiv` bit(1) NOT NULL,
  `ansprechpartner` varchar(255) DEFAULT NULL,
  `auto_process_emails` bit(1) NOT NULL,
  `email` varchar(255) NOT NULL,
  `gueltig_ab` date DEFAULT NULL,
  `gueltig_bis` date DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `notizen` varchar(500) DEFAULT NULL,
  `telefon` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `steuerberater_kontakt_emails` (
  `steuerberater_id` bigint NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  KEY `FKorfns4otta1ek0ivc3m05en5x` (`steuerberater_id`),
  CONSTRAINT `FKorfns4otta1ek0ivc3m05en5x` FOREIGN KEY (`steuerberater_id`) REFERENCES `steuerberater_kontakt` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sv_satz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `satz_typ` enum('KV_GESAMT','PV_GESAMT','PV_KINDERLOS_AN_ZUSCHLAG','RV_GESAMT','AV_GESAMT','MINIJOB_AG_KV','MINIJOB_AG_RV','MINIJOB_AG_PAUSCHALSTEUER','U1_UMLAGE','U2_UMLAGE','INSOLVENZGELDUMLAGE') NOT NULL,
  `prozent` decimal(5,2) NOT NULL,
  `gueltig_ab` date NOT NULL,
  `beschreibung` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sv_satz_typ_ab` (`satz_typ`,`gueltig_ab`),
  KEY `idx_sv_satz_typ` (`satz_typ`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sv_satz` VALUES (1,'KV_GESAMT',14.60,'2026-01-01','Allgemeiner Beitragssatz Krankenversicherung (mit Krankengeldanspruch).'),(2,'PV_GESAMT',3.40,'2026-01-01','Pflegeversicherung. Wird i. d. R. halbe/halbe getragen (Sachsen-Sonderregel ausgenommen).'),(3,'PV_KINDERLOS_AN_ZUSCHLAG',0.60,'2026-01-01','Zuschlag fuer kinderlose Arbeitnehmer ab 23 Jahren - traegt allein der Arbeitnehmer.'),(4,'RV_GESAMT',18.60,'2026-01-01','Rentenversicherung. Halbe/halbe.'),(5,'AV_GESAMT',2.60,'2026-01-01','Arbeitslosenversicherung. Halbe/halbe.'),(6,'MINIJOB_AG_KV',13.00,'2026-01-01','Minijob-Pauschale Krankenversicherung (Arbeitgeber, gewerblich).'),(7,'MINIJOB_AG_RV',15.00,'2026-01-01','Minijob-Pauschale Rentenversicherung (Arbeitgeber, gewerblich).'),(8,'MINIJOB_AG_PAUSCHALSTEUER',2.00,'2026-01-01','Pauschalsteuer Minijob (Arbeitgeber, optional - alternativ individuelle Lohnsteuer).'),(9,'U1_UMLAGE',1.10,'2026-01-01','Umlage U1 (Lohnfortzahlung im Krankheitsfall) - kassenindividuell, hier Default-Wert.'),(10,'U2_UMLAGE',0.24,'2026-01-01','Umlage U2 (Mutterschaft) - kassenindividuell, hier Default-Wert.'),(11,'INSOLVENZGELDUMLAGE',0.06,'2026-01-01','Insolvenzgeldumlage - traegt allein der Arbeitgeber.');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_setting` (
  `setting_key` varchar(128) NOT NULL,
  `beschreibung` varchar(255) DEFAULT NULL,
  `setting_value` text,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `system_setting` VALUES ('imap.dokumente.host','Posteingangs-Server des Dokument-Postfachs (leer = derselbe wie beim Versand)',''),('imap.dokumente.port','IMAP Port des Dokument-Postfachs (993 = SSL)','993'),('imap.host','IMAP Mail-Server Hostname','secureimap.t-online.de'),('imap.password','IMAP Passwort',''),('imap.port','IMAP Port (993 = SSL)','993'),('imap.username','IMAP Benutzername / E-Mail-Adresse',''),('mail.absender-name','Anzeigename des Absenders fuer Mails ueber das Standard-Postfach (leer = nur Adresse)',''),('mail.dokumente.absender-name','Anzeigename des Absenders fuer Ausgangsgeschaeftsdokumente (leer = nur Adresse)',''),('mail.dokumente.from-address','Sichtbare Absender-Adresse fuer Ausgangsgeschaeftsdokumente (leer = SMTP-Benutzer)',''),('smtp.dokumente.aktiv','Eigenes Mail-Konto fuer Ausgangsgeschaeftsdokumente verwenden (false = Standard-Konto)','false'),('smtp.dokumente.host','SMTP Mail-Server fuer Ausgangsgeschaeftsdokumente',''),('smtp.dokumente.password','SMTP Passwort fuer Ausgangsgeschaeftsdokumente',''),('smtp.dokumente.port','SMTP Port fuer Ausgangsgeschaeftsdokumente (465 = SSL)','465'),('smtp.dokumente.username','SMTP Benutzername / E-Mail-Adresse fuer Ausgangsgeschaeftsdokumente','');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `telefon_anruf` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `zeitpunkt` datetime NOT NULL,
  `art` enum('ANGENOMMEN','ANRUFBEANTWORTER','VERPASST','AUSGEHEND','ABGEWIESEN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `anrufbeantworter` int DEFAULT NULL,
  `nummer_roh` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `nummer_normalisiert` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `eigene_nummer` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dauer_minuten` int NOT NULL DEFAULT '0',
  `name_fritzbox` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `kunde_id` bigint DEFAULT NULL,
  `lieferant_id` bigint DEFAULT NULL,
  `zuordnung` enum('AUTOMATISCH','MANUELL','KEINE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'KEINE',
  `angelegt_am` datetime NOT NULL,
  `steuerberater_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_telefon_anruf` (`zeitpunkt`,`art`,`eigene_nummer`,`nummer_roh`),
  KEY `fk_telefon_anruf_kunde` (`kunde_id`),
  KEY `fk_telefon_anruf_lieferant` (`lieferant_id`),
  KEY `idx_telefon_anruf_nummer` (`nummer_normalisiert`),
  KEY `idx_telefon_anruf_zeitpunkt` (`zeitpunkt`),
  KEY `fk_telefon_anruf_steuerberater` (`steuerberater_id`),
  CONSTRAINT `fk_telefon_anruf_kunde` FOREIGN KEY (`kunde_id`) REFERENCES `kunde` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_telefon_anruf_lieferant` FOREIGN KEY (`lieferant_id`) REFERENCES `lieferanten` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_telefon_anruf_steuerberater` FOREIGN KEY (`steuerberater_id`) REFERENCES `steuerberater_kontakt` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `textbaustein` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beschreibung` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `html` longtext,
  `name` varchar(150) NOT NULL,
  `sort_order` int DEFAULT NULL,
  `typ` enum('VORTEXT','NACHTEXT','ZAHLUNGSZIEL','FREITEXT') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `textbaustein_dokumenttyp_enum` (
  `textbaustein_id` bigint NOT NULL,
  `dokumenttyp` varchar(30) DEFAULT NULL,
  KEY `FK8hgp7s2lhidjwhjqtrbdbnche` (`textbaustein_id`),
  CONSTRAINT `FK8hgp7s2lhidjwhjqtrbdbnche` FOREIGN KEY (`textbaustein_id`) REFERENCES `textbaustein` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `textbaustein_placeholder` (
  `textbaustein_id` bigint NOT NULL,
  `placeholder` varchar(120) DEFAULT NULL,
  KEY `FKqyjk8hutbj5qir19iifmhc20l` (`textbaustein_id`),
  CONSTRAINT `FKqyjk8hutbj5qir19iifmhc20l` FOREIGN KEY (`textbaustein_id`) REFERENCES `textbaustein` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `urlaubsantrag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bemerkung` varchar(2000) DEFAULT NULL,
  `bis_datum` date NOT NULL,
  `status` enum('OFFEN','GENEHMIGT','ABGELEHNT','STORNIERT') NOT NULL,
  `typ` enum('URLAUB','KRANKHEIT','FORTBILDUNG','ZEITAUSGLEICH','ARBEIT','PAUSE') NOT NULL,
  `von_datum` date NOT NULL,
  `mitarbeiter_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6pp00g1q8epx65rwnnx65bhg3` (`mitarbeiter_id`),
  CONSTRAINT `FK6pp00g1q8epx65rwnnx65bhg3` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `verbrauchsgegenstand` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aktiv` bit(1) NOT NULL,
  `einheit` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `seriennummer` varchar(255) DEFAULT NULL,
  `verbrauchsart` enum('WASSER','STROM','HEIZUNG','GAS','SONSTIGES') NOT NULL,
  `raum_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpxq4wlvshh9oudj6bhxi3q6ll` (`raum_id`,`name`),
  CONSTRAINT `FK4pc1yhjue8o31xj4uc0t204gm` FOREIGN KEY (`raum_id`) REFERENCES `raum` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `verteilungsschluessel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beschreibung` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `typ` enum('PROZENTUAL','VERBRAUCH','FLAECHE') NOT NULL,
  `mietobjekt_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKelncq56treqk11ulmx5cbx0jd` (`mietobjekt_id`,`name`),
  CONSTRAINT `FKmka8um8ln8mhd1sbt8w8lcqqh` FOREIGN KEY (`mietobjekt_id`) REFERENCES `mietobjekt` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `verteilungsschluessel_eintrag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anteil` decimal(10,4) NOT NULL,
  `kommentar` varchar(255) DEFAULT NULL,
  `mietpartei_id` bigint NOT NULL,
  `verbrauchsgegenstand_id` bigint DEFAULT NULL,
  `verteilungsschluessel_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgks5yei7k4aan3bou6lar8yfi` (`mietpartei_id`),
  KEY `FKj41gn459lmkmx6pk5nsecvuxc` (`verbrauchsgegenstand_id`),
  KEY `FKbou6nt4hmrpfuhh180vi2dmj4` (`verteilungsschluessel_id`),
  CONSTRAINT `FKbou6nt4hmrpfuhh180vi2dmj4` FOREIGN KEY (`verteilungsschluessel_id`) REFERENCES `verteilungsschluessel` (`id`),
  CONSTRAINT `FKgks5yei7k4aan3bou6lar8yfi` FOREIGN KEY (`mietpartei_id`) REFERENCES `mietpartei` (`id`),
  CONSTRAINT `FKj41gn459lmkmx6pk5nsecvuxc` FOREIGN KEY (`verbrauchsgegenstand_id`) REFERENCES `verbrauchsgegenstand` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `website_analytics_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `snapshot_date` date NOT NULL,
  `schema_version` int NOT NULL,
  `generated_at` datetime(6) NOT NULL,
  `received_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `totals_visitors` bigint NOT NULL DEFAULT '0',
  `totals_pageviews` bigint NOT NULL DEFAULT '0',
  `totals_leads_phone` bigint NOT NULL DEFAULT '0',
  `totals_leads_mail` bigint NOT NULL DEFAULT '0',
  `totals_submissions` bigint NOT NULL DEFAULT '0',
  `visitors_today` bigint NOT NULL DEFAULT '0',
  `visitors_yesterday` bigint NOT NULL DEFAULT '0',
  `conversion` int NOT NULL DEFAULT '0',
  `funnel_json` longtext COLLATE utf8mb4_unicode_ci,
  `top_pages_json` longtext COLLATE utf8mb4_unicode_ci,
  `devices_json` longtext COLLATE utf8mb4_unicode_ci,
  `browsers_json` longtext COLLATE utf8mb4_unicode_ci,
  `cities_json` longtext COLLATE utf8mb4_unicode_ci,
  `raw_payload` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_website_analytics_snapshot_date` (`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `werkstoff` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anzeigename` varchar(255) DEFAULT NULL,
  `beschichtungshinweis` varchar(255) DEFAULT NULL,
  `dichte` decimal(6,3) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `pulverbeschichtungsgeeignet` bit(1) NOT NULL DEFAULT b'0',
  `verzinkungsgeeignet` bit(1) NOT NULL DEFAULT b'0',
  `werkstattname` varchar(255) DEFAULT NULL,
  `werkstoffnorm` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_werkstoff_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `werkstoff` VALUES (1,'Baustahl S355J2 (hochfest)','Feuerverzinken und Pulverbeschichten moeglich, auch kombiniert (Duplex).',7.850,'S355J2',0x01,0x01,'Baustahl','EN 10025-2'),(2,'Edelstahl 1.4571 (seewasserfest)','Nicht feuerverzinken. Hoehere Bestaendigkeit als 1.4301, fuer Kuesten- und Poolbereich.',8.000,'1.4571',0x01,0x00,'V4A','EN 10088-3'),(3,'Aluminium EN AW-5754 (Blech)','Nicht feuerverzinken. Gut umformbar und schweissbar - die uebliche Blechlegierung.',2.660,'EN AW-5754',0x01,0x00,'Alu','EN 573-3'),(4,'Stahlblech verzinkt (Sendzimir)','Bereits bandverzinkt - kein zweites Verzinken noetig. Pulverbeschichten geht.',7.850,'DX51D+Z',0x01,0x00,'Verzinkt','EN 10346'),(5,'Edelstahl 1.4404','Nicht feuerverzinken. Molybdaenlegiert, bestaendiger als 1.4301 - fuer Aussen- und Poolbereich.',8.000,'1.4404',0x01,0x00,'V4A','EN 10088-2');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zaehlerstand` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `abrechnungs_jahr` int NOT NULL,
  `erfasst_am` datetime(6) DEFAULT NULL,
  `kommentar` varchar(255) DEFAULT NULL,
  `stand` decimal(19,4) NOT NULL,
  `stichtag` date NOT NULL,
  `verbrauch` decimal(19,4) DEFAULT NULL,
  `verbrauchsgegenstand_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhw0px46vdocwr59xpot9x0hty` (`verbrauchsgegenstand_id`,`abrechnungs_jahr`),
  CONSTRAINT `FK8hi5ig6jb6uqsli155pwep3lh` FOREIGN KEY (`verbrauchsgegenstand_id`) REFERENCES `verbrauchsgegenstand` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zahlungsart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bezeichnung` varchar(60) NOT NULL,
  `aktiv` tinyint(1) NOT NULL DEFAULT '1',
  `sortierung` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zahlungsart_bezeichnung` (`bezeichnung`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `zahlungsart` VALUES (1,'Bar',1,10),(2,'EC-Karte',1,20),(3,'Überweisung',1,30),(4,'Lastschrift',1,40),(5,'Kreditkarte',1,50),(6,'PayPal',1,60),(7,'Scheck',1,70),(8,'Rechnung',1,80),(9,'Online-Zahlung',1,65);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zeitbuchung` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anzahl_in_stunden` decimal(10,2) DEFAULT NULL,
  `automatisch_beendet` bit(1) NOT NULL DEFAULT b'0',
  `ende_zeit` datetime(6) DEFAULT NULL,
  `erfasst_am` datetime(6) DEFAULT NULL,
  `erfasst_via` enum('MOBILE_APP','DESKTOP','ADMIN_KORREKTUR','IMPORT','SYSTEM') DEFAULT NULL,
  `idempotency_key` varchar(36) DEFAULT NULL,
  `notiz` varchar(500) DEFAULT NULL,
  `start_zeit` datetime(6) DEFAULT NULL,
  `stop_idempotency_key` varchar(36) DEFAULT NULL,
  `typ` enum('ARBEIT','PAUSE') DEFAULT NULL,
  `version` int NOT NULL,
  `zuletzt_geaendert_am` datetime(6) DEFAULT NULL,
  `arbeitsgang_id` bigint DEFAULT NULL,
  `arbeitsgang_stundensatz_id` bigint DEFAULT NULL,
  `erfasst_von_mitarbeiter_id` bigint DEFAULT NULL,
  `mitarbeiter_id` bigint DEFAULT NULL,
  `projekt_id` bigint DEFAULT NULL,
  `projekt_produktkategorie_id` bigint DEFAULT NULL,
  `zuletzt_geaendert_von` bigint DEFAULT NULL,
  `aktiver_mitarbeiter` bigint GENERATED ALWAYS AS ((case when (`ende_zeit` is null) then `mitarbeiter_id` else NULL end)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zeitbuchung_mitarbeiter_start` (`mitarbeiter_id`,`start_zeit`),
  UNIQUE KEY `UK_4tfqlsa53kra2t8mewsll8vx5` (`idempotency_key`),
  UNIQUE KEY `UK_g5g9ov6psv6yk0vgygkl1cbw4` (`stop_idempotency_key`),
  UNIQUE KEY `uk_zeitbuchung_aktiv_pro_mitarbeiter` (`aktiver_mitarbeiter`),
  KEY `FKfcgnpiwlh9u9ot845i7p48tmr` (`arbeitsgang_id`),
  KEY `FK4wicif5gtd7can3ge6kxtqe23` (`arbeitsgang_stundensatz_id`),
  KEY `FK81s7ie1gywxfoti8ro5s9x1hx` (`erfasst_von_mitarbeiter_id`),
  KEY `FKc9bxldv2wcr3efhbt2ouymlxg` (`projekt_id`),
  KEY `FKc1e8i75jf31h6296qsrea9ync` (`projekt_produktkategorie_id`),
  KEY `FKaqk8irsk4nbauh3dhpr9qj4g6` (`zuletzt_geaendert_von`),
  KEY `idx_zeitbuchung_automatisch_beendet` (`automatisch_beendet`,`start_zeit`),
  CONSTRAINT `FK4wicif5gtd7can3ge6kxtqe23` FOREIGN KEY (`arbeitsgang_stundensatz_id`) REFERENCES `arbeitsgang_stundensatz` (`id`),
  CONSTRAINT `FK81s7ie1gywxfoti8ro5s9x1hx` FOREIGN KEY (`erfasst_von_mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKaqk8irsk4nbauh3dhpr9qj4g6` FOREIGN KEY (`zuletzt_geaendert_von`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKc1e8i75jf31h6296qsrea9ync` FOREIGN KEY (`projekt_produktkategorie_id`) REFERENCES `projekt_produktkategorie` (`id`),
  CONSTRAINT `FKc9bxldv2wcr3efhbt2ouymlxg` FOREIGN KEY (`projekt_id`) REFERENCES `projekt` (`id`),
  CONSTRAINT `FKfcgnpiwlh9u9ot845i7p48tmr` FOREIGN KEY (`arbeitsgang_id`) REFERENCES `arbeitsgang` (`id`),
  CONSTRAINT `FKt0fhw59yfj8sx7sy8jcacs13y` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zeitbuchung_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aenderungsgrund` text,
  `aktion` enum('ERSTELLT','GEAENDERT','STORNIERT') NOT NULL,
  `anzahl_in_stunden` decimal(10,2) DEFAULT NULL,
  `arbeitsgang_id` bigint DEFAULT NULL,
  `arbeitsgang_stundensatz_id` bigint DEFAULT NULL,
  `ende_zeit` datetime(6) DEFAULT NULL,
  `geaendert_am` datetime(6) NOT NULL,
  `geaendert_via` enum('MOBILE_APP','DESKTOP','ADMIN_KORREKTUR','IMPORT','SYSTEM') NOT NULL,
  `mitarbeiter_id` bigint NOT NULL,
  `notiz` text,
  `projekt_id` bigint DEFAULT NULL,
  `projekt_produktkategorie_id` bigint DEFAULT NULL,
  `start_zeit` datetime(6) NOT NULL,
  `version` int NOT NULL,
  `zeitbuchung_id` bigint NOT NULL,
  `geaendert_von_mitarbeiter_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zeitbuchung_audit_version` (`zeitbuchung_id`,`version`),
  KEY `FKs9oi4hhr8g5di5jp6sw8srtb7` (`geaendert_von_mitarbeiter_id`),
  CONSTRAINT `FKs9oi4hhr8g5di5jp6sw8srtb7` FOREIGN KEY (`geaendert_von_mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zeitkontenmodell` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` bigint NOT NULL DEFAULT '0',
  `bezeichnung` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `montag_stunden` decimal(4,2) DEFAULT NULL,
  `dienstag_stunden` decimal(4,2) DEFAULT NULL,
  `mittwoch_stunden` decimal(4,2) DEFAULT NULL,
  `donnerstag_stunden` decimal(4,2) DEFAULT NULL,
  `freitag_stunden` decimal(4,2) DEFAULT NULL,
  `samstag_stunden` decimal(4,2) DEFAULT NULL,
  `sonntag_stunden` decimal(4,2) DEFAULT NULL,
  `buchung_start_zeit` time(6) DEFAULT NULL,
  `buchung_ende_zeit` time(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zeitkonto_korrektur` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `datum` date NOT NULL,
  `erstellt_am` datetime(6) NOT NULL,
  `grund` varchar(500) NOT NULL,
  `storniert` bit(1) NOT NULL,
  `storniert_am` datetime(6) DEFAULT NULL,
  `stornierungsgrund` varchar(500) DEFAULT NULL,
  `stunden` decimal(10,2) NOT NULL,
  `typ` enum('STUNDEN','URLAUB') NOT NULL,
  `version` int NOT NULL,
  `erstellt_von_id` bigint DEFAULT NULL,
  `mitarbeiter_id` bigint NOT NULL,
  `storniert_von_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfcrle0vb7ixmxfkapac3uf45i` (`erstellt_von_id`),
  KEY `FKammh0xpx84b1wrom73awsqm7o` (`mitarbeiter_id`),
  KEY `FK4302496ih5ju8b76ki504bjbg` (`storniert_von_id`),
  CONSTRAINT `FK4302496ih5ju8b76ki504bjbg` FOREIGN KEY (`storniert_von_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKammh0xpx84b1wrom73awsqm7o` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `FKfcrle0vb7ixmxfkapac3uf45i` FOREIGN KEY (`erstellt_von_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zeitkonto_korrektur_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aenderungsgrund` text,
  `aktion` enum('ERSTELLT','GEAENDERT','STORNIERT') NOT NULL,
  `datum` date NOT NULL,
  `geaendert_am` datetime(6) NOT NULL,
  `geaendert_via` enum('MOBILE_APP','DESKTOP','ADMIN_KORREKTUR','IMPORT','SYSTEM') NOT NULL,
  `grund` text,
  `mitarbeiter_id` bigint NOT NULL,
  `stunden` decimal(10,2) NOT NULL,
  `version` int NOT NULL,
  `zeitkonto_korrektur_id` bigint NOT NULL,
  `geaendert_von_mitarbeiter_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zeitkonto_korrektur_audit_version` (`zeitkonto_korrektur_id`,`version`),
  KEY `FK94ffmogll87bhfj6g3ufwpjxy` (`geaendert_von_mitarbeiter_id`),
  CONSTRAINT `FK94ffmogll87bhfj6g3ufwpjxy` FOREIGN KEY (`geaendert_von_mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zeitkonto_pause` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` bigint NOT NULL DEFAULT '0',
  `mitarbeiter_id` bigint NOT NULL,
  `gueltig_von` date NOT NULL,
  `gueltig_bis` date DEFAULT NULL,
  `offene_pause_mitarbeiter_id` bigint GENERATED ALWAYS AS ((case when (`gueltig_bis` is null) then `mitarbeiter_id` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zeitkonto_pause_mitarbeiter_von` (`mitarbeiter_id`,`gueltig_von`),
  UNIQUE KEY `uk_zeitkonto_pause_offen` (`offene_pause_mitarbeiter_id`),
  CONSTRAINT `fk_zeitkonto_pause_mitarbeiter` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `ck_zeitkonto_pause_zeitraum` CHECK (((`gueltig_bis` is null) or (`gueltig_bis` >= `gueltig_von`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zeitkonto_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` bigint NOT NULL DEFAULT '0',
  `mitarbeiter_id` bigint NOT NULL,
  `gueltig_von` date NOT NULL,
  `gueltig_bis` date DEFAULT NULL,
  `vorlage_id` bigint DEFAULT NULL,
  `montag_stunden` decimal(4,2) DEFAULT NULL,
  `dienstag_stunden` decimal(4,2) DEFAULT NULL,
  `mittwoch_stunden` decimal(4,2) DEFAULT NULL,
  `donnerstag_stunden` decimal(4,2) DEFAULT NULL,
  `freitag_stunden` decimal(4,2) DEFAULT NULL,
  `samstag_stunden` decimal(4,2) DEFAULT NULL,
  `sonntag_stunden` decimal(4,2) DEFAULT NULL,
  `buchung_start_zeit` time(6) DEFAULT NULL,
  `buchung_ende_zeit` time(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zeitkonto_version_mitarbeiter_von` (`mitarbeiter_id`,`gueltig_von`),
  KEY `fk_zeitkonto_version_vorlage` (`vorlage_id`),
  CONSTRAINT `fk_zeitkonto_version_mitarbeiter` FOREIGN KEY (`mitarbeiter_id`) REFERENCES `mitarbeiter` (`id`),
  CONSTRAINT `fk_zeitkonto_version_vorlage` FOREIGN KEY (`vorlage_id`) REFERENCES `zeitkontenmodell` (`id`),
  CONSTRAINT `chk_zeitkonto_version_zeitraum` CHECK (((`gueltig_bis` is null) or (`gueltig_bis` >= `gueltig_von`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
SET FOREIGN_KEY_CHECKS=1;
