-- ==========================================
-- SPRINT 3: ACTUALIZACIÓN DE ESQUEMA
-- ==========================================

-- 1. Tabla de Notificaciones (HU-4.1.1, HU-4.1.2, HU-4.1.4)
CREATE TABLE notifications (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL, -- 'PROJECT_FUNDED', 'PROJECT_FAILED', 'REFUND_PROCESSED'
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 2. Índices para mejorar rendimiento de consultas del Sprint 3
-- Acelera HU-2.2.2 (Mis aportes)
CREATE INDEX IF NOT EXISTS idx_contributions_backer_status 
    ON contributions(user_id, status);

-- Acelera HU-4.1.3 (Búsqueda de proyectos vencidos)
CREATE INDEX IF NOT EXISTS idx_projects_status_deadline 
    ON projects(status, deadline);

-- Acelera HU-4.1.1/4.1.2 (Notificaciones no leídas de un usuario)
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread 
    ON notifications(user_id, is_read);

-- Acelera HU-4.1.4 (Aportes de un proyecto para reembolsar)
CREATE INDEX IF NOT EXISTS idx_contributions_project_status 
    ON contributions(project_id, status);