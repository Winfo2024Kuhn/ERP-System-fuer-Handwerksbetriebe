-- Push-Subscription Tabelle für Web Push Notifications (VAPID)
-- Speichert Browser Push-Subscriptions pro Mitarbeiter für iOS Lock-Screen Benachrichtigungen
CREATE TABLE IF NOT EXISTS push_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mitarbeiter_id BIGINT NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    p256dh VARCHAR(512) NOT NULL,
    auth VARCHAR(512) NOT NULL,
    erstellt_am DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_push_endpoint (endpoint(500)),
    CONSTRAINT fk_push_sub_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
