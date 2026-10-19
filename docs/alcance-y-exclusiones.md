# Alcance y exclusiones del MVP — ProyecTWIN

Registro de decisiones de alcance vigentes. No reescribe documentos históricos
(bitácoras de sesión, auditorías o changelogs): solo fija lo que aplica al MVP
actual.

## Modelo predictivo — EXCLUIDO del alcance

- **Decisión (18-oct-2026, por indicación del instructor):** el modelo
  predictivo, el aprendizaje automático y cualquier servicio de predicción
  quedan **fuera del alcance del MVP**. No se implementan ni se planifican.
- **No hay trabajo pendiente asociado:** las referencias de conversación a
  predictivo no constituyen tareas del MVP.
- **El motor de similitud existente se conserva tal cual:** es el único
  mecanismo de comparación del sistema (4 señales locales —palabras,
  caracteres, tema y cobertura— sobre el propio corpus; sin IA ni librerías
  externas). Nada de este motor debe retirarse ni reescribirse por confundirlo
  con el trabajo predictivo.
- Las menciones a "IA" en material histórico de sesiones o mockups antiguos no
  se modifican (falsificarían decisiones anteriores); la interfaz actual no
  expone capacidades predictivas.

## Otras decisiones vigentes relevantes

- **Borrado de usuarios (D-01, implementado en el Lote 6):** el borrado de
  usuarios **no administradores** protege la evidencia de similitud y responde
  **409** cuando destruiría pares (propuestas creadas o fichas del instructor);
  la **exención administrativa** se mantiene implementada. Queda documentado
  como decisión residual el "callejón 409" del aprendiz creador (sin endpoint
  de reasignación de `id_creador`).
- **Perfiles de aprendices al borrar programas/redes:** se mantiene la política
  actual (eliminación de la fila) hasta decisión específica.
- **Cascadas de catálogo (instructor/programa/red):** protegidas contra la
  destrucción indirecta de pares de similitud (409), según la decisión
  BUG-007 × D-2b.
