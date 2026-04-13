-- ============================================================
-- V208: Naive Bayes Spam-Modell Tabellen
-- ============================================================

-- Token-Frequenzen für Multinomial Naive Bayes
CREATE TABLE IF NOT EXISTS spam_token_count (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(100) NOT NULL,
    spam_count INT NOT NULL DEFAULT 0,
    ham_count INT NOT NULL DEFAULT 0,
    UNIQUE INDEX idx_spam_token_unique (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Globale Modell-Statistiken (Anzahl trainierter Spam/Ham Dokumente)
CREATE TABLE IF NOT EXISTS spam_model_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_key VARCHAR(50) NOT NULL,
    stat_value BIGINT NOT NULL DEFAULT 0,
    UNIQUE INDEX idx_spam_model_stats_key (stat_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO spam_model_stats (stat_key, stat_value) VALUES ('total_spam', 0);
INSERT INTO spam_model_stats (stat_key, stat_value) VALUES ('total_ham', 0);

-- Neue Spalten auf email-Tabelle für User-Feedback und Bayes-Score
ALTER TABLE email ADD COLUMN user_spam_verdict VARCHAR(20) DEFAULT NULL;
ALTER TABLE email ADD COLUMN bayes_score DOUBLE DEFAULT NULL;
