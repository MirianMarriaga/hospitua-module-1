-- ============================================================================
-- HOSPITUA - Módulo 1 (Habitaciones, Inventario, Check-In y Check-Out)
-- Migración V3: Tareas de limpieza y mantenimiento, reportes de daño y bloqueos técnicos
-- ============================================================================
-- Sin datos: los registros solo los crean los casos de uso. Los datos sintéticos de desarrollo
-- van en classpath:db/dev-data (solo perfil dev).
-- Los identificadores del personal guardan el username sin FK a user_account, como actor_id.

-- 1. Tareas de limpieza
CREATE TABLE cleaning_task (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES room(id),
    cleaning_staff_member_id VARCHAR(100) NOT NULL,
    start_date_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date_time TIMESTAMP WITH TIME ZONE,
    outcome VARCHAR(50),
    CONSTRAINT ck_cleaning_task_end_after_start CHECK (end_date_time IS NULL OR end_date_time >= start_date_time),
    CONSTRAINT ck_cleaning_task_outcome_when_closed CHECK ((outcome IS NULL) = (end_date_time IS NULL))
);

-- Regla 9: como máximo una tarea abierta por miembro y por habitación
CREATE UNIQUE INDEX uk_cleaning_task_open_by_member ON cleaning_task(cleaning_staff_member_id)
    WHERE end_date_time IS NULL;
CREATE UNIQUE INDEX uk_cleaning_task_open_by_room ON cleaning_task(room_id)
    WHERE end_date_time IS NULL;

-- Última limpieza de cada habitación. El predicado repite los outcomes de
-- CleaningTaskOutcome.countsAsCleaning(): si cambian allí, hay que cambiarlos aquí.
CREATE INDEX idx_cleaning_task_last_cleaning ON cleaning_task(room_id, end_date_time DESC)
    WHERE outcome IN ('Completed', 'DamageReported');

-- 2. Tareas de reparación (sin version: se escriben con la fila de room bloqueada, regla 13)
CREATE TABLE reparation_task (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES room(id),
    maintenance_staff_member_id VARCHAR(100) NOT NULL,
    start_date_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date_time TIMESTAMP WITH TIME ZONE,
    outcome VARCHAR(50),
    CONSTRAINT ck_reparation_task_end_after_start CHECK (end_date_time IS NULL OR end_date_time >= start_date_time),
    CONSTRAINT ck_reparation_task_outcome_when_closed CHECK ((outcome IS NULL) = (end_date_time IS NULL))
);

CREATE UNIQUE INDEX uk_reparation_task_open_by_member ON reparation_task(maintenance_staff_member_id)
    WHERE end_date_time IS NULL;
CREATE UNIQUE INDEX uk_reparation_task_open_by_room ON reparation_task(room_id)
    WHERE end_date_time IS NULL;

-- 3. Reportes de daño (inmutables)
CREATE TABLE damage_report (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES room(id),
    author_id VARCHAR(100) NOT NULL,
    damage_description VARCHAR(500) NOT NULL,
    report_date_time TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_damage_report_room_date ON damage_report(room_id, report_date_time DESC);

-- 4. Informes de bloqueo técnico (sin version: se aplican y cierran con la fila de room bloqueada,
-- regla 13, y la caducidad es un UPDATE en lote)
CREATE TABLE technical_block_report (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES room(id),
    maintenance_staff_member_id VARCHAR(100) NOT NULL,
    technical_block_reason VARCHAR(500) NOT NULL,
    technical_block_start_date DATE NOT NULL,
    estimated_technical_block_end_date DATE NOT NULL,
    report_date_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT ck_technical_block_report_date_range
        CHECK (estimated_technical_block_end_date >= technical_block_start_date),
    CONSTRAINT ck_technical_block_report_status
        CHECK (status IN ('Scheduled', 'Applied', 'Completed', 'Expired'))
);

CREATE INDEX idx_technical_block_report_room_status ON technical_block_report(room_id, status);
-- Un solo bloqueo aplicado por habitación
CREATE UNIQUE INDEX uk_technical_block_report_applied_by_room ON technical_block_report(room_id)
    WHERE status = 'Applied';
-- Trabajo diario de bloqueos (regla 10)
CREATE INDEX idx_technical_block_report_scheduled_start ON technical_block_report(technical_block_start_date)
    WHERE status = 'Scheduled';
