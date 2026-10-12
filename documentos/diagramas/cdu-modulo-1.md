# Diagrama de casos de uso — Módulo 1

Versión en Mermaid de `cdu-modulo-1-d.drawio`, con los casos de uso que tienen SPEC en `documentos/SPEC/`:

- Agrega los tres paneles (recepción, limpieza y mantenimiento) y *Gestionar personal*, que no estaban en el diagrama original (en verde). Los casos se agrupan por área solo para que el diagrama se lea mejor.
- Corrige "Personal de manteniento".

Las líneas continuas unen a cada actor con los casos de uso que usa; las punteadas son `<<includes>>`.

```mermaid
flowchart LR
    %% Actores del hotel
    ADM["👤 Administrador"]
    GER["👤 Gerente"]
    LIM["👤 Personal de limpieza"]
    MAN["👤 Personal de mantenimiento"]
    REC["👤 Recepcionista"]

    %% Sistemas externos
    M2[["Módulo 2"]]
    M3[["Módulo 3"]]

    subgraph M1["Módulo 1: Gestión de habitaciones e inventario"]
        subgraph G1["Habitaciones e inventario"]
            RH(["Registrar habitación"])
            EH(["Editar habitación"])
            DB(["Dar de baja habitación"])
            MD(["Marcar habitación como disponible"])
            CI(["Consultar inventario de habitaciones"])
            HE(["Consultar historial de estados"])
            GP(["Gestionar personal"])
        end
        subgraph G2["Limpieza"]
            PL(["Consultar panel de limpieza"])
            ML(["Marcar habitación en limpieza"])
            FL(["Confirmar fin de limpieza de habitación"])
            IR(["Marcar habitación inhabilitada por reparaciones"])
        end
        subgraph G3["Mantenimiento"]
            PM(["Consultar panel de mantenimiento"])
            BT(["Programar bloqueo técnico para habitación"])
            FR(["Confirmar reparación finalizada"])
            PE(["Marcar pendiente a limpieza"])
            IM(["Consultar información de mantenimientos"])
        end
        subgraph G4["Recepción y reservas"]
            PR(["Consultar panel de recepción"])
            CHI(["Registrar check-in"])
            CHO(["Registrar check-out"])
            CR(["Consultar reservas"])
            LQ(["Consultar liquidación"])
            TAR(["Consultar tarifa base"])
            MR(["Marcar habitación como reservada"])
        end
    end

    %% Administrador
    ADM --- RH
    ADM --- EH
    ADM --- DB
    ADM --- MD
    ADM --- GP
    ADM --- CI

    %% Gerente
    GER --- HE
    GER --- CI

    %% Personal de limpieza
    LIM --- PL
    LIM --- ML
    LIM --- FL
    LIM --- IR

    %% Personal de mantenimiento
    MAN --- PM
    MAN --- IR
    MAN --- BT
    MAN --- FR
    MAN --- IM
    MAN --- CR

    %% Recepcionista
    REC --- PR
    REC --- CHI
    REC --- CHO

    %% Módulo 2
    CR --- M2
    CI --- M2
    IM --- M2
    MR --- M2
    CHI --> M2
    CHO --> M2

    %% Módulo 3
    LQ --- M3
    TAR --- M3

    %% <<includes>>
    DB -. "«includes»" .-> CR
    BT -. "«includes»" .-> CR
    CHI -. "«includes»" .-> CR
    CHO -. "«includes»" .-> LQ
    CHO -. "«includes»" .-> PE
    FR -. "«includes»" .-> PE
    FL -. "«includes»" .-> MD

    classDef actor fill:#ffffff,stroke:#152B4D,stroke-width:2px,color:#152B4D
    classDef externo fill:#F1F5F9,stroke:#6B7280,color:#1E2433
    classDef cu fill:#ffffff,stroke:#1C3A63,color:#1E2433
    classDef nuevo fill:#E7F7EE,stroke:#16A34A,stroke-width:2px,color:#14532D
    class ADM,GER,LIM,MAN,REC actor
    class M2,M3 externo
    class RH,EH,DB,MD,CI,HE,ML,FL,IR,BT,FR,PE,IM,CHI,CHO,CR,LQ,TAR,MR cu
    class GP,PL,PM,PR nuevo
    style M1 fill:#EAF1FF,stroke:#2F6FED
    style G1 fill:#F8FAFF,stroke:#94A3B8
    style G2 fill:#F8FAFF,stroke:#94A3B8
    style G3 fill:#F8FAFF,stroke:#94A3B8
    style G4 fill:#F8FAFF,stroke:#94A3B8
```
