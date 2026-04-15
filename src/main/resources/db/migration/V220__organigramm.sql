-- Organigramm: Benannte Diagramme mit JSON-Content
CREATE TABLE if not exists organigramm (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    content     MEDIUMTEXT   NOT NULL COMMENT 'JSON: { nodes: [...], edges: [...] }',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_organigramm_name UNIQUE (name)
);
