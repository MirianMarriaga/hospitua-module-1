-- ============================================================================
-- HOSPITUA - Módulo 1 (Habitaciones, Inventario, Check-In y Check-Out)
-- Migración V4: Índices de consulta del inventario (plan-consultar-inventario-habitaciones.md, T001)
-- ============================================================================

CREATE INDEX ix_room_status ON room(status);
CREATE INDEX ix_room_category_room ON room(category_room);
CREATE INDEX ix_room_floor ON room(floor);
