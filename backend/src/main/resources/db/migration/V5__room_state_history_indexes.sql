-- ============================================================================
-- HOSPITUA - Módulo 1 (Habitaciones, Inventario, Check-In y Check-Out)
-- Migración V5: Índices de consulta del historial de estados (plan-consultar-historial-de-estados.md, T001)
-- ============================================================================

CREATE INDEX ix_room_state_history_room_start ON room_state_history(room_id, start_date_time);
CREATE INDEX ix_room_state_history_status_start ON room_state_history(status, start_date_time);
