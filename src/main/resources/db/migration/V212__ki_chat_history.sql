-- Chat-Verlauf für KI-Assistent (pro Benutzer)
CREATE TABLE ki_chat (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(300) NOT NULL DEFAULT 'Neuer Chat',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ki_chat_user FOREIGN KEY (user_id) REFERENCES frontend_user_profile(id) ON DELETE CASCADE,
    INDEX idx_ki_chat_user_updated (user_id, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ki_chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_id     BIGINT        NOT NULL,
    role        VARCHAR(20)   NOT NULL,
    content     MEDIUMTEXT    NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ki_msg_chat FOREIGN KEY (chat_id) REFERENCES ki_chat(id) ON DELETE CASCADE,
    INDEX idx_ki_msg_chat (chat_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
