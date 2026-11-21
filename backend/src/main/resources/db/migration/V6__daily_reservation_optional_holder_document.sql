-- Reservas de Booking sin documento ni nacionalidad del titular (acordado con Módulo 2 el 20-11-2026).
-- En el check-in, el huésped marcado como titular aporta su documento a la estadía.
ALTER TABLE daily_reservation ALTER COLUMN guest_document_type DROP NOT NULL;
ALTER TABLE daily_reservation ALTER COLUMN guest_document_number DROP NOT NULL;
ALTER TABLE daily_reservation ALTER COLUMN guest_nationality DROP NOT NULL;
