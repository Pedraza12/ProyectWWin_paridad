# ProyecTwin — Recorrido de una petición (GET)

Flujo representativo: **el aprendiz abre “Mis Propuestas”** → `GET /v1/projects`.

## 1. Diagrama de secuencia (recomendado)

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario (aprendiz)
    participant C as Componente React
    participant D as Capa de datos (useApi + recursos.js)
    participant H as Cliente HTTP (api.js)
    participant API as API Laravel /v1
    participant M as Modelo Eloquent
    participant DB as MySQL

    U->>C: abre "Mis Propuestas"
    C->>D: useApi(() => proyectos.listar())
    D->>H: apiFetch('/projects')
    H->>API: GET /v1/projects  (Authorization: Bearer)
    API->>M: Project::included()->paraUsuario()->get()
    M->>DB: SELECT ... FROM projects
    DB-->>M: filas
    M-->>API: objetos
    API-->>H: JSON 200
    H-->>D: data
    D-->>C: { data, cargando, error }
    C-->>U: render HTML (lista de propuestas)
```

## 2. Diagrama por capas (carriles)

```mermaid
flowchart LR
  subgraph FE["FRONTEND"]
    direction LR
    C[Componente React] --> H[Hook useApi] --> R[Recurso recursos.js] --> A[Cliente HTTP api.js] --> X[Proxy Vite]
  end
  subgraph BE["BACKEND (Laravel /v1)"]
    direction LR
    RT[Ruta] --> MW[Middleware] --> K[Controlador] --> M[Modelo Eloquent]
  end
  subgraph DA["DATOS"]
    DB[(MySQL)]
  end
  X --> RT
  M --> DB
  DB -. filas .-> M
  M -. JSON 200 .-> A
  A -. data .-> C
```

## 3. Código de cada capa

| Capa | Archivo | Fragmento |
|---|---|---|
| Componente | `frontend/src/pages/aprendiz/Propuestas/Propuestas.jsx:98` | `useApi(() => proyectos.listar({ included: INCLUDE_PROYECTOS }), [], { inicial: [] })` |
| Hook | `frontend/src/lib/useApi.js:10` | `{ data, cargando, error, recargar }` |
| Recurso | `frontend/src/lib/recursos.js:69` | `apiFetch(`/projects${qs({ included: '...', ...filtros })}`).then(lista)` |
| Cliente HTTP | `frontend/src/lib/api.js:80` | `headers.Authorization = `Bearer ${token}` · await fetch(url, { method, headers })` |
| Proxy | `frontend/vite.config.js:10` | `'/v1': { target: 'http://127.0.0.1:8000', changeOrigin: true }` |
| Ruta | `backend/routes/api-v1.php:107` | `Route::get('projects', [ProjectController::class, 'index'])` |
| Middleware | `backend/app/Http/Kernel.php:42` | `'cuenta.activa' => CuentaActiva::class` · `'rol' => RolMiddleware::class` |
| Controlador | `backend/app/Http/Controllers/Api/ProjectController.php:18` | `Project::included()->paraUsuario($user)->get()` |
| Modelo | `backend/app/Models/Project.php:74` | `scopeParaUsuario(Builder $q, $user)` |
| Base de datos | — | `SELECT ... FROM projects` (MySQL) |

## Leyenda
- **Flechas continuas** → ida de la petición.
- **Flechas punteadas** → respuesta (vuelta).
- Colores (en la versión draw.io): frontend azul · backend verde · datos naranja.
