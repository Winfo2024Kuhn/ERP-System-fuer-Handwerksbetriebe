-- Demo seed data for ERP System
-- All data is fictional. For demonstration purposes only.

USE kalkulationsprogramm_db;

-- Demo admin user
-- Username: admin, Password: demo
-- WARNING: Change before any production use!

CREATE TABLE IF NOT EXISTS demo_meta (
    id INT PRIMARY KEY AUTO_INCREMENT,
    key_name VARCHAR(100) NOT NULL,
    value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO demo_meta (key_name, value) VALUES
('demo_mode', 'true'),
('demo_warning', 'This is a demo instance with fictional data. Do not use in production.');

-- Demo company
CREATE TABLE IF NOT EXISTS company_demo (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    address TEXT,
    tax_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO company_demo (name, address, tax_id) VALUES
('Muster GmbH', 'Hauptstrasse 1, 12345 Musterstadt', 'DE123456789'),
('Baumeister Schmidt', 'Weg 42, 54321 Baumstadt', 'DE987654321'),
('Elektro Meister GmbH', 'Steindamm 7, 67890 Elektrohausen', 'DE456789123');
