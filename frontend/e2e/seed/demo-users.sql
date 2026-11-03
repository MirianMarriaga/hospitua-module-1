-- Cuentas de Gerencia y Recepción para los E2E, con la misma contraseña del admin del bootstrap
-- (admin123 en perfil dev). Solo para desarrollo local: no hay endpoint de personal todavía.
INSERT INTO user_account (username, password_hash, full_name, area, active, created_at, created_by, updated_at, updated_by)
SELECT 'gerente', password_hash, 'Andrés Rojas', 'GERENCIA', TRUE, now(), 'admin', now(), 'admin'
FROM user_account WHERE username = 'admin'
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_account (username, password_hash, full_name, area, active, created_at, created_by, updated_at, updated_by)
SELECT 'recepcion', password_hash, 'Ana Ramírez', 'RECEPCION', TRUE, now(), 'admin', now(), 'admin'
FROM user_account WHERE username = 'admin'
ON CONFLICT (username) DO NOTHING;
