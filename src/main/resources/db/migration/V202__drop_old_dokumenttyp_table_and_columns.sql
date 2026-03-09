-- V202: Drop legacy dokumenttyp table and old FK columns after enum migration (V201).

-- 1. Drop FK constraints referencing dokumenttyp table
ALTER TABLE formular_template_assignment DROP FOREIGN KEY FKmtjvpcn0xqgvr2uao9b78m28a;
ALTER TABLE geschaeftsdokument           DROP FOREIGN KEY FK4mxv41c38ng7yrt8twyeoh491;

-- 2. Drop old dokumenttyp_id columns
ALTER TABLE formular_template_assignment DROP COLUMN dokumenttyp_id;
ALTER TABLE geschaeftsdokument           DROP COLUMN dokumenttyp_id;

-- 3. Drop old join table textbaustein_dokumenttyp
DROP TABLE IF EXISTS textbaustein_dokumenttyp;

-- 4. Drop old dokumenttyp entity table
DROP TABLE IF EXISTS dokumenttyp;
