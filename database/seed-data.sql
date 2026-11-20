-- ============================================================
-- DATOS SEMILLA PARA PRUEBAS
-- ============================================================
-- Contraseña global para pruebas: Password123
-- Hash BCrypt asociado:
-- $2a$10$5Auj9daTFegCajpd4u7Za.kIlWRn.9alG99rxx2cSLO8LtJQcl8S6
--
-- IMPORTANTE:
-- Ejecute primero database/clear-db.sql para evitar duplicados
-- antes de cargar este archivo.
-- ============================================================


-- ==========================================
-- SPRINT 1: USUARIOS
-- ==========================================

-- 1. Poblar Usuarios
INSERT INTO users (full_name, email, password_hash) VALUES
(
    'Camila Torres',
    'camila@example.com',
    '$2a$10$5Auj9daTFegCajpd4u7Za.kIlWRn.9alG99rxx2cSLO8LtJQcl8S6'
),
(
    'Ana Gómez',
    'ana.gomez@example.com',
    '$2a$10$5Auj9daTFegCajpd4u7Za.kIlWRn.9alG99rxx2cSLO8LtJQcl8S6'
),
(
    'Carlos Dev',
    'carlos@example.com',
    '$2a$10$5Auj9daTFegCajpd4u7Za.kIlWRn.9alG99rxx2cSLO8LtJQcl8S6'
);


-- ==========================================
-- SPRINT 1 Y SPRINT 2: PROYECTOS
-- ==========================================

-- 2. Poblar Proyectos
-- Se usan subconsultas para obtener el ID del usuario creador.
-- Los proyectos DRAFT permiten funding_goal y deadline nulos.
-- Los proyectos ACTIVE incluyen el riesgo calculado en risk_score.
INSERT INTO projects (
    user_id,
    title,
    description,
    image_url,
    funding_goal,
    current_amount,
    deadline,
    status,
    risk_score
) VALUES
(
    (SELECT id FROM users WHERE email = 'camila@example.com'),
    'HoneyMoney App',
    'Aplicación y API REST para la gestión integral de finanzas personales con filtros paginados.',
    'https://placehold.co/600x400/png?text=HoneyMoney',
    2500.00,
    2500.00,
    '2026-12-31 23:59:59',
    'FUNDED',
    7
),
(
    (SELECT id FROM users WHERE email = 'camila@example.com'),
    'Energy Hands',
    'Sistema de interacción gestual en tiempo real para renderizado dinámico de partículas.',
    'https://placehold.co/600x400/png?text=Energy+Hands',
    4000.00,
    0.00,
    '2027-01-15 23:59:59',
    'ACTIVE',
    4
),
(
    (SELECT id FROM users WHERE email = 'ana.gomez@example.com'),
    'EcoBici Comunitaria',
    'Proyecto para fabricar bicicletas con materiales reciclados para comunidades rurales.',
    'https://placehold.co/600x400/png?text=EcoBici',
    5000.00,
    1250.00,
    '2026-11-30 23:59:59',
    'ACTIVE',
    8
),
(
    (SELECT id FROM users WHERE email = 'carlos@example.com'),
    'Juego Indie RPG',
    'Aún definiendo los detalles del arte y la historia principal.',
    'https://placehold.co/600x400/png?text=Borrador',
    NULL,
    0.00,
    NULL,
    'DRAFT',
    NULL
);


-- ==========================================
-- SPRINT 1 Y SPRINT 2: CONTRIBUCIONES
-- ==========================================

-- 3. Poblar Contribuciones
-- Se vinculan los aportes con los usuarios y proyectos existentes
-- mediante subconsultas para evitar depender de IDs fijos.
INSERT INTO contributions (user_id, project_id, amount, status) VALUES
(
    (SELECT id FROM users WHERE email = 'ana.gomez@example.com'),
    (SELECT id FROM projects WHERE title = 'HoneyMoney App' LIMIT 1),
    2500.00,
    'COMPLETED'
),
(
    (SELECT id FROM users WHERE email = 'carlos@example.com'),
    (SELECT id FROM projects WHERE title = 'EcoBici Comunitaria' LIMIT 1),
    1250.00,
    'COMPLETED'
),
(
    (SELECT id FROM users WHERE email = 'carlos@example.com'),
    (SELECT id FROM projects WHERE title = 'Energy Hands' LIMIT 1),
    100.00,
    'PENDING'
);


-- ==========================================
-- SPRINT 2: TRANSACCIONES
-- ==========================================

-- 4. Poblar Transacciones
-- Cada transacción se vincula a una contribución.
-- El campo nonce debe contener valores únicos para prevenir replay attacks.
INSERT INTO transactions (
    contribution_id,
    reference_code,
    payment_method,
    amount,
    status,
    nonce
) VALUES
(
    (
        SELECT id
        FROM contributions
        WHERE amount = 2500.00
        LIMIT 1
    ),
    'MOCK-REF-001',
    'SIMULATED_CARD',
    2500.00,
    'SUCCESS',
    'seed_nonce_001'
),
(
    (
        SELECT id
        FROM contributions
        WHERE amount = 1250.00
        LIMIT 1
    ),
    'MOCK-REF-002',
    'SIMULATED_CARD',
    1250.00,
    'SUCCESS',
    'seed_nonce_002'
);


-- ==========================================
-- SPRINT 3: NOTIFICACIONES
-- ==========================================

-- 5. Poblar Notificaciones
-- Se incluyen ejemplos para los tipos utilizados por la aplicación:
-- PROJECT_FUNDED, PROJECT_FAILED y REFUND_PROCESSED.
INSERT INTO notifications (
    user_id,
    project_id,
    type,
    message,
    is_read
) VALUES
(
    (SELECT id FROM users WHERE email = 'camila@example.com'),
    (SELECT id FROM projects WHERE title = 'HoneyMoney App' LIMIT 1),
    'PROJECT_FUNDED',
    'Tu proyecto HoneyMoney App ha alcanzado su meta de financiación.',
    FALSE
),
(
    (SELECT id FROM users WHERE email = 'ana.gomez@example.com'),
    (SELECT id FROM projects WHERE title = 'EcoBici Comunitaria' LIMIT 1),
    'REFUND_PROCESSED',
    'Se procesó el reembolso asociado a EcoBici Comunitaria.',
    FALSE
),
(
    (SELECT id FROM users WHERE email = 'carlos@example.com'),
    (SELECT id FROM projects WHERE title = 'Energy Hands' LIMIT 1),
    'PROJECT_FAILED',
    'El proyecto Energy Hands no alcanzó su meta antes del vencimiento.',
    TRUE
);