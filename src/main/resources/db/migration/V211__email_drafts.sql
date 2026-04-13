-- Tabelle für automatisch gespeicherte E-Mail-Entwürfe
CREATE TABLE email_draft (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient VARCHAR(1000),
    cc VARCHAR(1000),
    subject VARCHAR(1000),
    body LONGTEXT,
    from_address VARCHAR(255),
    reply_email_id BIGINT,
    projekt_id BIGINT,
    anfrage_id BIGINT,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT fk_draft_reply_email FOREIGN KEY (reply_email_id) REFERENCES email(id) ON DELETE SET NULL,
    CONSTRAINT fk_draft_projekt FOREIGN KEY (projekt_id) REFERENCES projekt(id) ON DELETE SET NULL,
    CONSTRAINT fk_draft_anfrage FOREIGN KEY (anfrage_id) REFERENCES anfrage(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
