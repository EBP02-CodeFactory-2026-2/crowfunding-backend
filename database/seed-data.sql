-- SPRINT 1: DATOS SEMILLA PARA PRUEBAS
-- Contraseña global para pruebas: Password123
-- Hash BCrypt asociado: $2a$10$5Auj9daTFegCajpd4u7Za.kIlWRn.9alG99rxx2cSLO8LtJQcl8S6

-- 1. Poblar Usuarios
INSERT INTO users (full_name, email, password_hash) VALUES
('Camila Torres', 'camila@example.com', '$2a$10$5Auj9daTFegCajpd4u7Za.kIlWRn.9alG99rxx2cSLO8LtJQcl8S6'),
('Ana Gómez', 'ana.gomez@example.com', '$2a$10$5Auj9daTFegCajpd4u7Za.kIlWRn.9alG99rxx2cSLO8LtJQcl8S6'),
('Carlos Dev', 'carlos@example.com', '$2a$10$5Auj9daTFegCajpd4u7Za.kIlWRn.9alG99rxx2cSLO8LtJQcl8S6');

-- 2. Poblar Proyectos (Asumiendo IDs 1 y 2 para los usuarios creados arriba)
INSERT INTO projects (user_id, title, description, image_url, funding_goal, current_amount, deadline, status) VALUES
(1, 'HoneyMoney App', 'Aplicación y API REST para la gestión integral de finanzas personales con filtros paginados.', 'https://placehold.co/600x400/png?text=HoneyMoney', 2500.00, 500.00, '2026-12-31 23:59:59', 'ACTIVE'),
(1, 'Energy Hands', 'Sistema de interacción gestual en tiempo real para renderizado dinámico de partículas.', 'https://placehold.co/600x400/png?text=Energy+Hands', 4000.00, 0.00, '2027-01-15 23:59:59', 'ACTIVE'),
(2, 'EcoBici Comunitaria', 'Proyecto para fabricar bicicletas con materiales reciclados para comunidades rurales.', 'https://placehold.co/600x400/png?text=EcoBici', 5000.00, 1250.00, '2026-11-30 23:59:59', 'ACTIVE');

-- 3. Poblar Contribuciones
-- Nota: Usamos subconsultas porque los IDs de proyectos son UUIDs autogenerados
INSERT INTO contributions (user_id, project_id, amount, status) VALUES
(2, (SELECT id FROM projects WHERE title = 'HoneyMoney App' LIMIT 1), 500.00, 'COMPLETED'),
(3, (SELECT id FROM projects WHERE title = 'EcoBici Comunitaria' LIMIT 1), 1250.00, 'COMPLETED');