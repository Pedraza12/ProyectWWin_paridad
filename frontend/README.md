# ProyecTwin — Frontend (React + Vite)

Frontend del **MVP** de ProyecTwin, plataforma de análisis de similitud para proyectos
formativos de aprendices del SENA. Permite registrar propuestas académicas, detectar
coincidencias contra un corpus aprobado, gestionar fichas y revisar el ciclo completo
de cada proyecto con tres roles: **Aprendiz**, **Instructor** y **Administrador**.

> El frontend consume la **API real** de Laravel (`MVP/backend`, rutas en
> `routes/api-v1.php`) con sesión por cookie httpOnly; no hay datos mock ni
> registro público (las cuentas las crea el administrador).

---

## Stack

- **React 19** + **Vite 8**
- **React Router 7**
- **CSS Modules** con tokens de diseño centralizados (`src/tokens/index.css`)
- **phosphor-react** (iconos)
- **Playwright** (@playwright/test) para E2E
- **ESLint 10** (flat config) + React Compiler (`react-hooks` v7)
- **@axe-core/playwright** para auditoría de accesibilidad

---

## Roles y credenciales de prueba

El acceso es por **nombre de usuario** (no por correo): las cuentas las crea y
configura el administrador y no existe registro público.

| Rol | Usuario | Contraseña |
|-----|---------|------------|
| Aprendiz | `mgonzalez` | `123456` |
| Instructor | `cruiz` | `123456` |
| Admin | `a` | `admin123` |

---

## Scripts

```bash
npm install        # instalar dependencias
npm run dev        # arrancar servidor de desarrollo
npm run build      # build de producción
npm run lint       # ESLint (flat config)
npm run preview    # servir el build (usa Playwright)
npm test           # suite E2E de Playwright (levanta el preview automáticamente)
npm run test:e2e   # alias de npm test
npm run test:unit  # tests unitarios (Vitest + React Testing Library)
```

---

## Arquitectura

```
src/
├── components/   # componentes reutilizables (Button, Badge, Modal, Dashboard, …)
│   └── *Base/    # implementaciones únicas compartidas entre roles
│                 # (DetalleProyectoBase, DetalleFichaBase, ListaBase,
│                 #  FormularioBase, PersonaDetalleBase, DetalleSimilitudBase, …)
├── pages/        # páginas por rol:
│   ├── aprendiz/ instructor/ admin/ public/
├── layouts/      # DashboardLayout, LandingLayout, AuthLayout
├── contexts/     # AuthContext (sesión real por cookie), TemaContext, MotorContext
├── constants/    # etiquetas y variantes compartidas (estados, roles, rutas)
├── tokens/       # tokens de diseño CSS (escala de color SENA, sombras, radios…)
├── utils/        # helpers (agruparObservaciones, …)
├── App.jsx       # enrutado + SafeRoute (control de acceso por rol)
```

### Refactor CSS

Las páginas ya no duplican estilos: cada familia comparte uno o varios módulos
`*Base` (`ListaBase`, `DetalleProyectoBase`, `DetalleFichaBase`, `DetalleSimilitudBase`,
`FormularioBase`, `PersonaDetalleBase`, `AlertasBase`, `Dashboard`). Las excepciones
realmente propias de cada página viven en módulos locales pequeños.

### React Compiler

El proyecto usa la verificación del React Compiler vía `eslint-plugin-react-hooks` v7.
Las memorizaciones manuales (`useMemo`) deben declarar dependencias que el compilador
pueda **inferir** (p. ej. `[perfil]` en lugar de `[perfil?.fichaId]`) para no provocar
`Compilation Skipped: Existing memoization could not be preserved`.

---

## Calidad

### Cobertura E2E (Playwright) — `e2e/`

| Spec | Qué cubre |
|------|-----------|
| `auth.spec.js` | Login, logout, seguridad de cuenta (cambio de contraseña) |
| `ficha.spec.js` | Ciclo de ficha: unirse → persistir → salir |
| `instructor.spec.js` | Revisión de propuestas, detección de similitudes al enviar, crear ficha |
| `intrusion.spec.js` | Autorización por rol: recursos ajenos en modo lectura |
| `privacidad.spec.js` | Privacidad de propuestas y observaciones por equipos |
| `propuestas.spec.js` | Listado, validaciones y creación de propuestas (individual y en equipo) |
| `perfiles.spec.js` | Perfiles de otros usuarios (solo lectura) |
| `notificaciones-tema.spec.js` | Alertas sin leer, modo oscuro persistente, tablas en vista móvil |
| `admin.spec.js` | Gestión de usuarios/proyectos/similitudes del administrador |
| `auditoria-cobertura.spec.js` | 39 rutas recorridas verificando shell, sin fallback de error ni errores de consola |
| `accesibilidad.spec.js` | Axe (WCAG A/AA): **39 rutas por rol + 3 de foco visible + 4 públicas** (46 verificaciones), modo claro |
| `accesibilidad-oscuro.spec.js` | Axe (WCAG A/AA): **20 rutas en modo oscuro** (`html[data-theme="dark"]`): 16 autenticadas + 4 públicas |
| `responsive.spec.js` | Desbordamiento horizontal en **375px y 768px** sobre todas las rutas (por rol y públicas) |

**Estado actual (18-oct-2026):** 60 specs E2E (53 raíz + 7 de agentes), **todos ejecutados** en el gate 5/5 del 18-oct-2026 (**347 pruebas, 0 fallos**; aislados + agentes/cobertura/negativos/volumen).

> `e2e/rutas.js` centraliza `RUTAS_POR_ROL` + `RUTAS_PUBLICAS`, compartido por los
> specs de accesibilidad, modo oscuro, responsive y cobertura.

### Tests unitarios (Vitest + React Testing Library)

`npm run test:unit` → **63 tests en 18 archivos** (`*.test.{js,jsx}` junto a su fuente):

| Archivo | Qué cubre |
|---------|-----------|
| `src/utils/helpers.test.js` | `iniciales`, `formatearFecha`, `parseFecha`, `agruparObservaciones` |
| `src/utils/validation.test.js` | regex histórica y mínimo 6, `esEmailValido`, `esPasswordValida` |
| `src/lib/api.test.js` | `apiDescargar` avisa sesión expirada (401) igual que `apiFetch` |
| `src/hooks/useFocusTrap.test.jsx` | foco inicial, ciclo Tab/Shift+Tab, `onEscape` |
| `src/constants/routes.test.js` | rutas por rol (`RUTA_POR_ROL`, `RUTA_ALERTAS_POR_ROL`, `RUTA_SOPORTE_POR_ROL`) |
| `src/components/Badge/Badge.test.jsx` | render de children y variantes |
| `src/components/Button/Button.test.jsx` | tipo de elemento, variantes, propagación de props |
| `src/components/Avatar/Avatar.test.jsx` | iniciales, imagen vs texto, `title` |
| `src/components/FormField/FormField.test.jsx` | asociación label↔control (a11y), error/help, requerido |
| `src/components/MetricCard/MetricCard.test.jsx` | valor, etiqueta, tendencia, variante |
| `src/components/Pagination/Pagination.test.jsx` | info de rango, navegación y estados deshabilitados |
| `src/components/ConsoleCard/Console.test.jsx` | kit consola: `ConsoleCard`, `StatChip`, `SectionHeader` |
| `src/components/CountUp/CountUp.test.jsx` | valor final accesible, prefijo/sufijo, no numéricos |
| `src/components/DataTable/DataTable.test.jsx` | estado vacío y `data-label` para modo tarjetas |
| `src/components/GradeBadge/GradeBadge.test.jsx` | `gradeForScore` por umbral y badge accesible |
| `src/components/Input/Input.test.jsx` | `PasswordInput`: visibilidad, alternar y props |
| `src/components/ScoreDial/ScoreDial.test.jsx` | rol `img` con etiqueta de grado; normaliza 0-1 y recorta a 100 |
| `src/components/TextType/TextType.test.jsx` | escritura progresiva, texto completo para lectores y bucle |

Config en `vitest.config.js` (entorno `jsdom`, setup `src/test/setup.js` con
`@testing-library/jest-dom`). Los tests de Playwright y de Vitest no se solapan.

### Accesibilidad (WCAG AA)

- Contraste de texto terciario: `--c-text-3` en modo claro es `#666666` y en oscuro
  `#949494` (≥4.5:1).
- El verde SENA de acento (botones primarios, sidebar activo) usa `g-700 (#2a7c00)`
  en claro y tonos claros en oscuro para que el texto sobre él cumpla AA.
- Tokens de estado (success/info/danger/warning) **separados** por uso: `--c-*-text`
  para texto sobre fondos translúcidos (badges/alertas) y `--c-*`/`--c-*-hover` con
  `--c-on-*` para superficies de botones. Así cada modo (claro/oscuro) usa la
  luminosidad adecuada a su fondo y el texto sobre botones conserva contraste AA.
- Badges, Alert, MetricCard, Tag, avatares y porcentajes medios (`--c-warning-text`)
  cumplen contraste AA en ambos temas: `e2e/accesibilidad*.spec.js`.

### Seguridad de dependencias

`npm audit` → **0 vulnerabilidades**. `package-lock.json` sincronizado con versiones
parcheadas (`react-router-dom 7.18.3`, `vite 8.2.2`, `postcss`, `nanoid`, etc.).

### Lint

Sin errores: `npm run lint` → `0 problemas`.

---

## PWA / cabecera

- `index.html` incluye favicon, meta description, Open Graph/Twitter, `theme-color`
  y `lang="es"`.
- `public/manifest.webmanifest` habilita instalación de PWA básica.

---

## Integración

El frontend consume la API Laravel real (proxy `/v1` y `/sanctum` en
`vite.config.js`) con sesión por cookie httpOnly y CSRF. No hay capa mock.
