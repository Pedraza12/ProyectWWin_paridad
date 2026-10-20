// Capa de acceso a la API.
//
// Cada recurso expone funciones que devuelven directamente los datos del
// servidor (columnas del backend). No hay mocks ni transformaciones ocultas:
// las páginas piden lo que necesitan y resuelven las relaciones por `included`.
import { apiFetch, apiDescargar, qs } from "./api";

const lista = (data) => (Array.isArray(data) ? data : (data?.data ?? []));

// Includes compartidos: mismo shape de propuesta en todas las vistas.
export const INCLUDE_PROYECTOS =
  "creator,instructor.generalUser,classGroup.program,apprentices.generalUser";

// ---------------------------------------------------------------- Usuarios
export const usuarios = {
  listar: (filtros = {}) =>
    apiFetch(`/general-users${qs(filtros)}`).then(lista),
  // Listado paginado del servidor (panel de administración): devuelve el
  // paginador completo (data, total, last_page…) para no cargar todo.
  listarPaginado: (filtros = {}) =>
    apiFetch(`/general-users${qs({ paginado: 1, ...filtros })}`),
  obtener: (id, included = "apprentice,instructor,admin") =>
    apiFetch(`/general-users/${id}${qs({ included })}`),
  // Perfil público (vistas entre usuarios: compañero/instructor): solo datos básicos.
  perfil: (id) => apiFetch(`/general-users/${id}/perfil`),
  crear: (body) => apiFetch("/general-users", { method: "POST", body }),
  actualizar: (id, body) =>
    apiFetch(`/general-users/${id}`, { method: "PUT", body }),
  // Credenciales iniciales en PDF de los usuarios seleccionados (solo admin).
  exportarCredenciales: (ids) =>
    apiDescargar("/general-users/credenciales", {
      method: "POST",
      body: { ids },
      nombre: "credenciales-usuarios.pdf",
    }),
  // Reenvía las credenciales temporales al correo personal (solo admin).
  reenviarCredenciales: (id) =>
    apiFetch(`/general-users/${id}/credenciales/reenviar`, { method: "POST" }),
  // Restablece la contraseña temporal: la genera el servidor (solo admin).
  restablecerCredenciales: (id) =>
    apiFetch(`/general-users/${id}/credenciales/restablecer`, { method: "POST" }),
  // Cambio del propio correo: el backend exige la contraseña actual.
  cambiarCorreo: (correo, passwordActual) =>
    apiFetch("/auth/email", {
      method: "PUT",
      body: { correo, password_actual: passwordActual },
    }),
  eliminar: (id) => apiFetch(`/general-users/${id}`, { method: "DELETE" }),
};

// ---------------------------------------------------------------- Catálogos
export const redes = {
  listar: () => apiFetch("/knowledge-networks").then(lista),
  crear: (body) => apiFetch("/knowledge-networks", { method: "POST", body }),
  actualizar: (id, body) =>
    apiFetch(`/knowledge-networks/${id}`, { method: "PUT", body }),
  eliminar: (id) => apiFetch(`/knowledge-networks/${id}`, { method: "DELETE" }),
};

export const programas = {
  listar: (included = "knowledgeNetwork") =>
    apiFetch(`/training-programs${qs({ included })}`).then(lista),
  crear: (body) => apiFetch("/training-programs", { method: "POST", body }),
  actualizar: (id, body) =>
    apiFetch(`/training-programs/${id}`, { method: "PUT", body }),
  eliminar: (id) => apiFetch(`/training-programs/${id}`, { method: "DELETE" }),
};

// ---------------------------------------------------------------- Perfiles
export const instructores = {
  listar: (included = "generalUser") =>
    apiFetch(`/instructors${qs({ included })}`).then(lista),
};

export const aprendices = {
  listar: (included = "generalUser,classGroup") =>
    apiFetch(`/apprentices${qs({ included })}`).then(lista),
  actualizar: (id, body) =>
    apiFetch(`/apprentices/${id}`, { method: "PUT", body }),
  // Mi ficha (aprendiz): siempre sobre el usuario del token.
  previsualizarCodigo: (codigo) =>
    apiFetch(
      `/apprentices/me/ficha/codigo/${encodeURIComponent(codigo.trim().toLowerCase())}`,
    ),
  unirmeAlCodigo: (codigo) =>
    apiFetch("/apprentices/me/ficha", {
      method: "POST",
      body: { codigo: codigo.trim().toLowerCase() },
    }),
  salirDeFicha: () => apiFetch("/apprentices/me/ficha", { method: "DELETE" }),
};

// ---------------------------------------------------------------- Fichas
export const fichas = {
  listar: (included = "program,instructor.generalUser", filtros = {}) =>
    apiFetch(`/class-groups${qs({ included, ...filtros })}`).then(lista),
  obtener: (
    id,
    included = "program,instructor.generalUser,apprentices.generalUser",
  ) => apiFetch(`/class-groups/${id}${qs({ included })}`),
  crear: (body) => apiFetch("/class-groups", { method: "POST", body }),
  actualizar: (id, body) =>
    apiFetch(`/class-groups/${id}`, { method: "PUT", body }),
  eliminar: (id) => apiFetch(`/class-groups/${id}`, { method: "DELETE" }),
  // Credenciales iniciales de la ficha (aprendices + instructor) en PDF.
  exportarCredenciales: (id) =>
    apiDescargar(`/class-groups/${id}/credenciales`, {
      nombre: `credenciales-ficha-${id}.pdf`,
    }),
  // Alta/asociación atómica de un aprendiz: usuario_id (existente) o datos.
  agregarAprendiz: (id, body) =>
    apiFetch(`/class-groups/${id}/aprendices`, { method: "POST", body }),
};

// ---------------------------------------------------------------- Propuestas
export const proyectos = {
  listar: (filtros = {}) =>
    apiFetch(
      `/projects${qs({ included: "creator,instructor.generalUser,classGroup.program", ...filtros })}`,
    ).then(lista),
  obtener: (id) =>
    apiFetch(
      `/projects/${id}${qs({ included: "creator,instructor.generalUser,classGroup.program,apprentices.generalUser" })}`,
    ),
  crear: (body) => apiFetch("/projects", { method: "POST", body }),
  // Envío explícito del borrador (valida, detecta similitudes y avisa al instructor).
  enviar: (id) => apiFetch(`/projects/${id}/enviar`, { method: "POST" }),
  // Evolución de la propuesta (visible para participantes e instructor).
  historial: (id) =>
    apiFetch(`/projects/${id}/historial${qs({ included: "user" })}`).then(lista),
  actualizar: (id, body) =>
    apiFetch(`/projects/${id}`, { method: "PUT", body }),
  eliminar: (id) => apiFetch(`/projects/${id}`, { method: "DELETE" }),
  // Equipo (pivote aprendiz ↔ proyecto). Se incluye el aprendiz y su usuario
  // para resolver nombres sin depender del roster (acotado por ficha).
  equipo: (idProyecto) =>
    apiFetch(
      `/apprentice-projects${qs({ included: "apprentice.generalUser" })}`,
    )
      .then(lista)
      .then((filas) =>
        filas.filter((f) => Number(f.id_proyecto) === Number(idProyecto)),
      ),
  agregarAlEquipo: (idAprendiz, idProyecto) =>
    apiFetch("/apprentice-projects", {
      method: "POST",
      body: { id_aprendiz: idAprendiz, id_proyecto: idProyecto },
    }),
  quitarDelEquipo: (idPivote) =>
    apiFetch(`/apprentice-projects/${idPivote}`, { method: "DELETE" }),
};

// ---------------------------------------------------------------- Similitudes
// Se incluyen los proyectos del par (con su equipo) para resolver títulos y
// autoría sin depender del listado global de propuestas.
export const INCLUDE_SIMILITUD =
  "project1.classGroup.program,project2.classGroup.program,project1.creator,project2.creator,project1.apprentices.generalUser,project2.apprentices.generalUser";

export const similitudes = {
  listar: (filtros = {}) =>
    apiFetch(
      `/similarities${qs({ included: INCLUDE_SIMILITUD, ...filtros })}`,
    ).then(lista),
  obtener: (id) =>
    apiFetch(`/similarities/${id}${qs({ included: INCLUDE_SIMILITUD })}`),
  recalcular: () =>
    apiFetch("/similarities/recalculate", { method: "POST", body: {} }),
  eliminar: (id) => apiFetch(`/similarities/${id}`, { method: "DELETE" }),
};

// ---------------------------------------------------------------- Motor
export const motor = {
  // Lectura pública de la configuración global (landing, barra de gobierno y admin).
  obtener: () => apiFetch("/config-similitud"),
  actualizar: (umbral, meses) =>
    apiFetch("/config-similitud", { method: "PUT", body: { umbral, meses } }),
};

// ---------------------------------------------------------------- Demo pública
// Endpoints abiertos que usa la landing: cualquier visitante puede probar el
// motor sin iniciar sesión. Devuelven solo datos agregados o títulos y %.
export const demo = {
  resumen: () => apiFetch("/public/resumen", { auth: false }),
  comparar: (texto) =>
    apiFetch("/public/demo-similitud", {
      method: "POST",
      body: { texto },
      auth: false,
    }),
};

// ---------------------------------------------------------------- Notificaciones
export const notificaciones = {
  listar: (filtros = {}) =>
    apiFetch(`/notifications${qs(filtros)}`).then(lista),
  // Recibe la notificación completa (el backend exige varios campos en el PUT).
  marcarLeida: (notificacion, leida = true) =>
    apiFetch(`/notifications/${notificacion.id}`, {
      method: "PUT",
      body: { ...notificacion, leida },
    }),
  eliminar: (id) => apiFetch(`/notifications/${id}`, { method: "DELETE" }),
};

// ---------------------------------------------------------------- Reportes
export const reportes = {
  listar: (included = "generalUser") =>
    apiFetch(`/bug-reports${qs({ included })}`).then(lista),
  obtener: (id) =>
    apiFetch(`/bug-reports/${id}${qs({ included: "generalUser" })}`),
  crear: (body) => apiFetch("/bug-reports", { method: "POST", body }),
  actualizar: (id, body) =>
    apiFetch(`/bug-reports/${id}`, { method: "PUT", body }),
  eliminar: (id) => apiFetch(`/bug-reports/${id}`, { method: "DELETE" }),
};

// ---------------------------------------------------------------- Observaciones
export const observaciones = {
  listar: (included = "user", filtros = {}) =>
    apiFetch(`/comments${qs({ included, ...filtros })}`).then(lista),
  crear: (body) => apiFetch("/comments", { method: "POST", body }),
  eliminar: (id) => apiFetch(`/comments/${id}`, { method: "DELETE" }),
};

// ---------------------------------------------------------------- Bitácora (solo admin, inmutable)
export const auditoria = {
  listar: (filtros = {}) => apiFetch(`/audit-logs${qs(filtros)}`).then(lista),
  // Consulta paginada del servidor (tabla de bitácora): devuelve el paginador.
  listarPaginado: (filtros = {}) =>
    apiFetch(`/audit-logs${qs({ paginado: 1, ...filtros })}`),
};

// ---------------------------------------------------------------- Métricas
// Agregados reales por rol (el backend deriva el rol del token).
export const metricas = {
  obtener: () => apiFetch("/dashboard-metrics"),
};
