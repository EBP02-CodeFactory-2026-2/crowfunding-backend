-- ==========================================
-- SPRINT 2: ACTUALIZACIÓN DE ESQUEMA
-- ==========================================

-- 1. Agregar score de riesgo a los proyectos
ALTER TABLE projects ADD COLUMN risk_score INTEGER;

-- 2. Permitir nulos en meta y fecha límite para los proyectos en DRAFT (HU-2.1.6)
ALTER TABLE projects ALTER COLUMN funding_goal DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN deadline DROP NOT NULL;

-- 3. Agregar nonce a las transacciones para prevenir Replay Attacks (HU-2.2.1)
ALTER TABLE transactions ADD COLUMN nonce VARCHAR(255) UNIQUE;