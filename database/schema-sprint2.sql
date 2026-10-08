-- ==========================================
-- SPRINT 2: ACTUALIZACIÓN DE ESQUEMA
-- ==========================================

-- Agregar score de riesgo a los proyectos
ALTER TABLE projects ADD COLUMN risk_score INTEGER;

-- Agregar nonce a las transacciones para prevenir Replay Attacks en HU-2.2.1
ALTER TABLE transactions ADD COLUMN nonce VARCHAR(255) UNIQUE;