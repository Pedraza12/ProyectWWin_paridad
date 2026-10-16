# Manual Técnico de ProyecTwin — carpeta de entrega

Esta carpeta contiene el **Manual Técnico de ProyecTwin** (MVP) generado a partir de la
auditoría técnica del repositorio realizada el **16 de octubre de 2026**.

## Archivos entregables

| Archivo | Contenido |
|---|---|
| `Manual_Tecnico_ProyecTwin.docx` | **Entregable principal.** Manual completo (108 páginas, 8 capítulos, referencias APA y anexos A–F), con estilos institucionales, tabla de contenido automática, listas de figuras y tablas, encabezados/pies con numeración romana (preliminares) y árabe (cuerpo). El campo de actualización está activo (`w:updateFields`) y **los campos ya fueron actualizados con Microsoft Word**. |
| `Manual_Tecnico_ProyecTwin_verificacion.pdf` | Copia PDF generada con Word para verificación visual (no es un entregable obligatorio; sirve de vista rápida). |
| `figuras/` | 8 diagramas técnicos generados con Python (arquitectura, secuencia, modelo ER, motor de similitud, estados, despliegue, autenticación y casos de uso). |
| `capturas/` | 24 capturas reales de la interfaz en ejecución (portal, tres roles, modo oscuro y vista móvil), tomadas el 16/10/2026 con Playwright sobre el sistema real. |
| `generacion/` | Scripts reproducibles de generación documental (ver abajo). |

## Regeneración del documento

Requisitos: Python 3.x con `python-docx`, `matplotlib` y `pillow` (figuras); Node con el
`node_modules` del frontend (capturas); opcionalmente Microsoft Word (actualización de campos).

```bash
# 1) Diagramas
python generacion/figuras.py

# 2) Capturas (requiere API en 127.0.0.1:8000 y SPA en localhost:5173 con la BD sembrada)
node generacion/capturas.mjs

# 3) Documento
python generacion/build_manual.py

# 4) Actualizar campos con Word y validar
powershell -ExecutionPolicy Bypass -File generacion/actualizar_campos.ps1
powershell -ExecutionPolicy Bypass -File generacion/exportar_pdf.ps1   # opcional (PDF de verificación)
python generacion/validar_docx.py
```

## Estado de verificación (16/10/2026)

- Documento abierto y actualizado con **Microsoft Word**: 108 páginas, 25.672 palabras.
- Validación estructural automática: **20 comprobaciones correctas, 0 avisos**
  (8 capítulos, TOC diligenciado, listas de figuras/tablas diligenciadas, 32 figuras y 66 tablas numeradas).
- Pruebas del sistema ejecutadas durante la auditoría: **244 pruebas backend** (1.156 aserciones),
  **60 unitarias de frontend** y **ESLint sin hallazgos** (detalle en el capítulo 7 del manual).

## Limitaciones declaradas (resumen; completo en el Anexo F del manual)

- El PDF de referencia «M.U- PopayanAllTour -SdeI.pdf» pertenece a **otro proyecto** y no se usó
  como fuente funcional.
- La suite E2E completa (54 especificaciones) y la compilación Android **no se re-ejecutaron** en
  esta auditoría; el despliegue Docker/Railway se verificó por configuración (Docker no disponible
  en el equipo de auditoría).
- Los datos institucionales (ficha, centro, nómina completa del equipo) no constan en el
  repositorio y se declaran como no confirmados, sin inventarlos.
- La auditoría usó una base de datos separada (`proyectwin_mvp_manual`) para no alterar los datos
  locales del equipo; no se modificó ningún archivo del código de la aplicación.
