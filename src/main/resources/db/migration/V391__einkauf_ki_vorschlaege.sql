CREATE TABLE IF NOT EXISTS einkauf_analyse_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    angebot_id BIGINT NOT NULL,
    email_id BIGINT NOT NULL,
    email_attachment_id BIGINT NOT NULL,
    anlagen_hash VARCHAR(64) NOT NULL,
    parser_version VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    fehler_hinweis VARCHAR(500) NULL,
    erstellt_am TIMESTAMP(6) NOT NULL,
    beendet_am TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_einkauf_analyse_job_quelle (email_id, anlagen_hash, parser_version),
    KEY idx_einkauf_analyse_job_angebot (angebot_id, erstellt_am),
    CONSTRAINT fk_einkauf_analyse_job_angebot FOREIGN KEY (angebot_id) REFERENCES einkauf_angebot(id),
    CONSTRAINT fk_einkauf_analyse_job_email FOREIGN KEY (email_id) REFERENCES email(id),
    CONSTRAINT fk_einkauf_analyse_job_attachment FOREIGN KEY (email_attachment_id) REFERENCES email_attachment(id)
);

CREATE TABLE IF NOT EXISTS einkauf_analyse_vorschlag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    feldpfad VARCHAR(180) NOT NULL,
    wert JSON NOT NULL,
    seite INT NULL,
    zitat VARCHAR(1000) NULL,
    text_start INT NULL,
    text_ende INT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    hinweis VARCHAR(500) NOT NULL,
    quelle_belegt BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    KEY idx_einkauf_analyse_vorschlag_job (job_id),
    CONSTRAINT fk_einkauf_analyse_vorschlag_job FOREIGN KEY (job_id) REFERENCES einkauf_analyse_job(id) ON DELETE CASCADE
);
