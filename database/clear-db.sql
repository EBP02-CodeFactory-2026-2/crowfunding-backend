-- Script de limpieza (Ejecutar antes del deploy a producción)
-- IMPORTANTE: Esto eliminará absolutamente todos los datos de prueba.

TRUNCATE TABLE transactions, contributions, projects, users RESTART IDENTITY CASCADE;