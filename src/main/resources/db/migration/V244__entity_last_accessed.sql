-- Speichert pro Frontend-User und Entity-Typ (z. B. PROJEKT, ANFRAGE), wann eine
-- konkrete Entity zuletzt geöffnet wurde. Wird verwendet, um Listen geräteübergreifend
-- nach "zuletzt aufgerufen" (Stack) zu sortieren.
CREATE TABLE IF NOT EXISTS entity_last_accessed (
    user_id BIGINT NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT NOT NULL,
    zugegriffen_am DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, entity_type, entity_id),
    CONSTRAINT fk_entity_last_accessed_user
        FOREIGN KEY (user_id) REFERENCES frontend_user_profile(id)
        ON DELETE CASCADE,
    INDEX idx_entity_last_accessed_lookup (user_id, entity_type, zugegriffen_am)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
