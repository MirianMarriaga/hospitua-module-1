# Diagrama Entidad-Relación — Módulo 1

**Fuente canónica**: `documentos/PLAN/base/plan.md` (Modelo de Datos Relacional).
**Última actualización**: 2026-10-21.

> Tipos simplificados: `uuid`, `date`, `decimal`, `json`, `boolean`, `int`, `string`.  
> **Línea continua** = FK real en BD · **Línea punteada** = relación lógica (campo cadena, sin FK declarada).  
> Los campos de usuario (`actor_id`, `decommissioned_by`, `reactivated_by`, `cleaning_staff_member_id`, `maintenance_staff_member_id`, `author_id`) guardan el `username` de `user_account` sin FK.

---

## 1. Inventario físico — `room` y sus registros asociados

```mermaid
erDiagram
  ROOM ||--o{ ROOM_STATE_HISTORY : "registra (room_id)"
  ROOM ||--o{ ROOM_AUDIT_LOG     : "audita (room_id)"
  ROOM ||--o{ ROOM_DECOMMISSION  : "da_de_baja (room_id)"
  USER_ACCOUNT ||..o{ ROOM_STATE_HISTORY : "cambia (actor_id)"
  USER_ACCOUNT ||..o{ ROOM_AUDIT_LOG     : "registra (actor_id)"
  USER_ACCOUNT ||..o{ ROOM_DECOMMISSION  : "da_de_baja / reactiva (decommissioned_by, reactivated_by)"

  ROOM {
    uuid    id                          PK
    string  room_number                 UK
    int     floor
    string  category_room
    int     max_capacity
    decimal base_rate
    string  status
    string  reserved_by_reservation_ref
    int     version
  }

  ROOM_STATE_HISTORY {
    uuid      id               PK
    uuid      room_id          FK
    string    status
    string    previous_status
    timestamp start_date_time
    timestamp end_date_time
    string    actor_id
    string    source_flow
    string    reservation_ref
  }

  ROOM_AUDIT_LOG {
    uuid      id              PK
    uuid      room_id         FK
    string    event_type
    string    actor_id
    json      details
    timestamp event_date_time
  }

  ROOM_DECOMMISSION {
    uuid      id                      PK
    uuid      room_id                 FK
    string    reason_category
    string    reason_detail
    string    decommissioned_by
    timestamp decommission_date_time
    string    reactivated_by
    timestamp reactivation_date_time
  }

  USER_ACCOUNT {
    string  username  PK
    string  full_name
    string  area
    boolean active
  }
```

---

## 2. Check-In / Check-Out — `stay` y `room_guest`

```mermaid
erDiagram
  ROOM ||--o{ STAY       : "aloja (room_id)"
  STAY ||--o{ ROOM_GUEST : "tiene (stay_id)"

  ROOM {
    uuid    id           PK
    string  room_number  UK
    string  status
  }

  STAY {
    uuid   id                       PK
    string reservation_ref
    uuid   room_id                  FK
    string source
    date   check_in_date
    date   check_out_date
    date   expected_checkin_date
    date   expected_checkout_date
    string titular_first_name
    string titular_last_name
    string titular_document_number
    int    version
  }

  ROOM_GUEST {
    uuid    id                   PK
    uuid    stay_id              FK
    string  first_name
    string  last_name
    string  document_type
    string  document_number
    string  nationality
    date    birth_date
    string  origin_place
    string  destination_place
    boolean is_reservation_guest
  }
```

---

## 3. Limpieza — `cleaning_task` y `damage_report`

```mermaid
erDiagram
  ROOM ||--o{ CLEANING_TASK : "limpia (room_id)"
  ROOM ||--o{ DAMAGE_REPORT : "reporta_daño (room_id)"
  USER_ACCOUNT ||..o{ CLEANING_TASK : "limpia (cleaning_staff_member_id)"
  USER_ACCOUNT ||..o{ DAMAGE_REPORT : "reporta (author_id)"

  ROOM {
    uuid    id           PK
    string  room_number  UK
    string  status
  }

  CLEANING_TASK {
    uuid      id                       PK
    uuid      room_id                  FK
    string    cleaning_staff_member_id
    timestamp start_date_time
    timestamp end_date_time
    string    outcome
    int       version
  }

  DAMAGE_REPORT {
    uuid      id                  PK
    uuid      room_id             FK
    string    author_id
    string    damage_description
    timestamp report_date_time
  }

  USER_ACCOUNT {
    string  username  PK
    string  full_name
    string  area
    boolean active
  }
```

---

## 4. Mantenimiento — `reparation_task` y `technical_block_report`

```mermaid
erDiagram
  ROOM ||--o{ REPARATION_TASK        : "repara (room_id)"
  ROOM ||--o{ TECHNICAL_BLOCK_REPORT : "bloquea (room_id)"
  USER_ACCOUNT ||..o{ REPARATION_TASK        : "repara (maintenance_staff_member_id)"
  USER_ACCOUNT ||..o{ TECHNICAL_BLOCK_REPORT : "programa (maintenance_staff_member_id)"

  ROOM {
    uuid    id           PK
    string  room_number  UK
    string  status
  }

  REPARATION_TASK {
    uuid      id                           PK
    uuid      room_id                      FK
    string    maintenance_staff_member_id
    timestamp start_date_time
    timestamp end_date_time
    string    outcome
    int       version
  }

  TECHNICAL_BLOCK_REPORT {
    uuid      id                               PK
    uuid      room_id                          FK
    string    maintenance_staff_member_id
    string    technical_block_reason
    date      technical_block_start_date
    date      estimated_technical_block_end_date
    timestamp report_date_time
    string    status
    int       version
  }

  USER_ACCOUNT {
    string  username  PK
    string  full_name
    string  area
    boolean active
  }
```

---

## 5. Copia local de reservas — `daily_reservation`, `daily_reservation_room` y log

```mermaid
erDiagram
  DAILY_RESERVATION ||--o{ DAILY_RESERVATION_ROOM : "incluye (reservation_ref)"
  ROOM ||..o{ DAILY_RESERVATION_ROOM              : "copia_local (room_id, sin FK)"

  DAILY_RESERVATION {
    string    reservation_ref     PK
    string    guest_first_name
    string    guest_last_name
    string    guest_document_type
    string    guest_document_number
    string    guest_nationality
    string    source
    date      start_date
    date      end_date
    int       guest_count
    timestamp updated_at
  }

  DAILY_RESERVATION_ROOM {
    string reservation_ref  PK_FK
    uuid   room_id          PK_FK
    string room_number
    string category_room
    int    guest_count
  }

  DAILY_RESERVATION_MESSAGE_LOG {
    string    message_id      PK
    bigint    sequence_number
    date      operational_date
    string    message_type
    timestamp received_at
  }

  ROOM {
    uuid    id           PK
    string  room_number  UK
    string  status
  }
```

---

## 6. Mensajería asíncrona — `outbox_notification`

> Sin FK declarada. Se vincula con la estancia y habitación a través del campo `payload` (JSONB).  
> Se inserta en la misma transacción ACID que el cambio físico de estado.

```mermaid
erDiagram
  OUTBOX_NOTIFICATION {
    uuid      id              PK
    string    event_type
    string    routing_key
    json      payload
    uuid      message_id      UK
    bigint    sequence_number
    string    status
    int       retry_count
    timestamp next_attempt_at
    string    last_error
    timestamp created_at
    timestamp published_at
  }
```

---

## 7. Personal y seguridad — `user_account`

```mermaid
erDiagram
  USER_ACCOUNT {
    string    username       PK
    string    password_hash
    string    full_name
    string    area
    boolean   active
    timestamp created_at
    string    created_by
    timestamp updated_at
    string    updated_by
    timestamp deactivated_at
    string    deactivated_by
    int       version
  }
```

---

## Resumen de tablas por grupo

| # | Grupo | Tablas |
|---|---|---|
| 1 | **Inventario físico** | `room`, `room_state_history`, `room_audit_log`, `room_decommission` |
| 2 | **Check-In / Check-Out** | `stay`, `room_guest` |
| 3 | **Limpieza** | `cleaning_task`, `damage_report` |
| 4 | **Mantenimiento** | `reparation_task`, `technical_block_report` |
| 5 | **Copia local de reservas** | `daily_reservation`, `daily_reservation_room`, `daily_reservation_message_log` |
| 6 | **Mensajería asíncrona** | `outbox_notification` |
| 7 | **Personal / Seguridad** | `user_account` |
