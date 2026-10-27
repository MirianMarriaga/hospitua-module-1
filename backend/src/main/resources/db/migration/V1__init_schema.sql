-- ============================================================================
-- HOSPITUA - Módulo 1 (Habitaciones, Inventario, Check-In y Check-Out)
-- Migración V1: Esquema Inicial de Base de Datos
-- ============================================================================

-- 1. Tabla de inventario físico de habitaciones
CREATE TABLE room (
    id UUID PRIMARY KEY,
    room_number VARCHAR(50) NOT NULL,
    floor INT NOT NULL CHECK (floor > 0),
    category_room VARCHAR(50) NOT NULL,
    max_capacity INT NOT NULL CHECK (max_capacity > 0),
    base_rate NUMERIC(12, 2) NOT NULL CHECK (base_rate >= 0),
    status VARCHAR(50) NOT NULL,
    reserved_by_reservation_ref VARCHAR(100),
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_room_room_number UNIQUE (room_number)
);

-- 2. Historial de estados por periodos de una habitación
CREATE TABLE room_state_history (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES room(id),
    status VARCHAR(50) NOT NULL,
    previous_status VARCHAR(50),
    start_date_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date_time TIMESTAMP WITH TIME ZONE,
    actor_id VARCHAR(100),
    source_flow VARCHAR(50) NOT NULL,
    reservation_ref VARCHAR(100)
);

CREATE INDEX idx_room_state_history_room_id ON room_state_history(room_id);

-- 3. Bitácora de auditoría de eventos de habitación
CREATE TABLE room_audit_log (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES room(id),
    event_type VARCHAR(50) NOT NULL,
    actor_id VARCHAR(100),
    details JSONB,
    event_date_time TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_room_audit_log_room_id ON room_audit_log(room_id);

-- 4. Bajas de habitaciones
CREATE TABLE room_decommission (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES room(id),
    reason_category VARCHAR(50) NOT NULL,
    reason_detail VARCHAR(500),
    decommissioned_by VARCHAR(100),
    decommission_date_time TIMESTAMP WITH TIME ZONE NOT NULL,
    reactivated_by VARCHAR(100),
    reactivation_date_time TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_room_decommission_room_date ON room_decommission(room_id, decommission_date_time DESC);

-- 5. Estancias físicas reales (Check-In formalizado)
CREATE TABLE stay (
    id UUID PRIMARY KEY,
    reservation_ref VARCHAR(100) NOT NULL,
    room_id UUID NOT NULL REFERENCES room(id),
    source VARCHAR(50) NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE,
    expected_checkin_date DATE NOT NULL,
    expected_checkout_date DATE NOT NULL,
    titular_first_name VARCHAR(100) NOT NULL,
    titular_last_name VARCHAR(100) NOT NULL,
    titular_document_number VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_stay_room_id ON stay(room_id);
CREATE INDEX idx_stay_reservation_ref ON stay(reservation_ref);

-- 6. Ocupantes físicos inmutables de una estancia
CREATE TABLE room_guest (
    id UUID PRIMARY KEY,
    stay_id UUID NOT NULL REFERENCES stay(id),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    document_number VARCHAR(50) NOT NULL,
    nationality VARCHAR(100) NOT NULL,
    birth_date DATE NOT NULL,
    origin_place VARCHAR(255),
    destination_place VARCHAR(255),
    is_reservation_guest BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_room_guest_stay_id ON room_guest(stay_id);

-- 7. Copia local de llegadas del día
CREATE TABLE daily_reservation (
    reservation_ref VARCHAR(100) PRIMARY KEY,
    guest_first_name VARCHAR(100) NOT NULL,
    guest_last_name VARCHAR(100) NOT NULL,
    guest_document_type VARCHAR(20) NOT NULL,
    guest_document_number VARCHAR(50) NOT NULL,
    guest_nationality VARCHAR(100) NOT NULL,
    source VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    guest_count INT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 8. Habitaciones asociadas a cada reserva diaria (SIN FK a room por desacoplamiento con Módulo 2)
CREATE TABLE daily_reservation_room (
    reservation_ref VARCHAR(100) NOT NULL REFERENCES daily_reservation(reservation_ref) ON DELETE CASCADE,
    room_id UUID NOT NULL,
    room_number VARCHAR(50) NOT NULL,
    category_room VARCHAR(50) NOT NULL,
    guest_count INT NOT NULL,
    PRIMARY KEY (reservation_ref, room_id)
);

-- 9. Bitácora de idempotencia para la cola de reservas diarias
CREATE TABLE daily_reservation_message_log (
    message_id VARCHAR(100) PRIMARY KEY,
    sequence_number BIGINT NOT NULL,
    operational_date DATE NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    reservation_ref VARCHAR(100),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_drml_op_date_msg_type ON daily_reservation_message_log(operational_date, message_type);
CREATE INDEX idx_drml_op_date_res_ref_seq ON daily_reservation_message_log(operational_date, reservation_ref, sequence_number);

-- 10. Mensajes asíncronos pendientes de envío vía Outbox
CREATE TABLE outbox_notification (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    message_id UUID NOT NULL,
    sequence_number BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_outbox_notification_message_id UNIQUE (message_id)
);

CREATE INDEX idx_outbox_status_next_attempt ON outbox_notification(status, next_attempt_at);

-- 11. Cuentas de personal de Módulo 1 (sin FK referenciando a esta tabla; usuarios referenciados por username)
CREATE TABLE user_account (
    username VARCHAR(50) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    area VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(50),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(50),
    deactivated_at TIMESTAMP WITH TIME ZONE,
    deactivated_by VARCHAR(50),
    version INT NOT NULL DEFAULT 0
);

-- 12. Secuencias de PostgreSQL para el Outbox (una por routing key según T012)
CREATE SEQUENCE outbox_seq_habitacion_checkin START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE outbox_seq_habitacion_checkout START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE outbox_seq_facturacion_checkout START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE outbox_seq_habitacion_mantenimiento START WITH 1 INCREMENT BY 1;
