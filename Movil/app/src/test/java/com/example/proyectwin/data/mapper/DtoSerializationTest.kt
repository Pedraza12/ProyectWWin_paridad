package com.example.proyectwin.data.mapper

import com.example.proyectwin.data.api.dto.BugReportCreateRequest
import com.example.proyectwin.data.api.dto.ClassGroupCreateRequest
import com.example.proyectwin.data.api.dto.ClassGroupUpdateRequest
import com.example.proyectwin.data.api.dto.GeneralUserDto
import com.example.proyectwin.data.api.dto.DashboardMetricsDto
import com.example.proyectwin.data.api.dto.NotificationDto
import com.example.proyectwin.data.api.dto.NotificationReadRequest
import com.example.proyectwin.data.api.dto.NotificationRequest
import com.example.proyectwin.data.api.dto.ProjectDto
import com.example.proyectwin.data.api.dto.SimilarityDto
import com.example.proyectwin.data.api.dto.TrainingProgramRequest
import com.example.proyectwin.data.model.NotificationType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Contratos de deserialización contra las respuestas reales de Laravel. */
class DtoSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun notification_parsesLeidaAsInt() {
        val dto = json.decodeFromString<NotificationDto>(
            """{"id":1,"titulo":"Nuevo","tipo":"sistema","enlace":"proyecto:5","leida":0,"fecha":"2026-10-01"}""",
        )
        assertEquals(0, dto.leida)
        assertFalse(dto.toDomain().leido)
        assertEquals("proyecto", dto.toDomain().enlaceModulo)
        assertEquals(5, dto.toDomain().enlaceId)
    }

    @Test
    fun notification_tipoDesconocido_noRompeYUsaElGenerico() {
        // El backend no emite `info` (valor legado): no debe romper ni quedar
        // como un tipo inexistente.
        val dto = json.decodeFromString<NotificationDto>(
            """{"id":2,"titulo":"Aviso","tipo":"info","leida":1,"fecha":"2026-10-02"}""",
        )
        assertEquals(NotificationType.SISTEMA, dto.toDomain().notifType)
        assertTrue(dto.toDomain().leido)
    }

    @Test
    fun similarity_parsesVigenteAndNestedProjects() {
        val cuerpo = """
            {
              "id": 9,
              "porcentaje": 42.5,
              "vigente": false,
              "id_proyecto_1": 3,
              "id_proyecto_2": 7,
              "project1": {"id":3,"titulo":"Uno","estado":"aprobado"},
              "project2": {"id":7,"titulo":"Dos","estado":"rechazado"}
            }
        """.trimIndent()
        val dto = json.decodeFromString<SimilarityDto>(cuerpo)
        assertFalse(dto.vigente)
        val dominio = dto.toDomain()
        assertEquals("Histórica", dominio.estadoDisplay)
        assertEquals("Uno", dominio.project1?.title)
        assertEquals("Dos", dominio.project2?.title)
    }

    @Test
    fun similarity_parsesTemaNumericoYTexto() {
        // El motor guardó `detalles.tema` como número en pares antiguos y como
        // null/texto en los nuevos. Antes, el par completo fallaba al
        // deserializar y quedaba oculto en los listados.
        val numerico = """
            {
              "id": 1,
              "porcentaje": 32,
              "detalles": {"palabras":21,"caracteres":43,"tema":40,"cobertura":30,"pasajes":[]},
              "id_proyecto_1": 1,
              "id_proyecto_2": 7
            }
        """.trimIndent()
        val dto = json.decodeFromString<SimilarityDto>(numerico)
        assertEquals("40", dto.toDomain().detalles?.tema)

        val texto = """
            {
              "id": 2,
              "porcentaje": 12,
              "detalles": {"palabras":1,"caracteres":2,"tema":"inventario","cobertura":3},
              "id_proyecto_1": 2,
              "id_proyecto_2": 5
            }
        """.trimIndent()
        assertEquals("inventario", json.decodeFromString<SimilarityDto>(texto).toDomain().detalles?.tema)
    }

    @Test
    fun project_parsesBackendScalars() {
        val cuerpo = """
            {
              "id": 12,
              "titulo": "Propuesta",
              "resumen": "Resumen",
              "palabras_clave": "a,b",
              "area_aplicacion": "Web",
              "objetivo_general": "Objetivo",
              "objetivos_especificos": ["uno","dos"],
              "estado": "pendiente",
              "huella_envio": "abc",
              "id_creador": 4,
              "id_instructor_asignado": 2,
              "id_class_group": 8,
              "classGroup": {"id":8,"codigo":"abc-defg","estado":"activo","program":{"id":1,"nombre":"ADSO"}}
            }
        """.trimIndent()
        val dominio = json.decodeFromString<ProjectDto>(cuerpo).toDomain()
        assertEquals("pendiente", dominio.estado)
        assertEquals("En revisión", dominio.statusDisplay)
        assertEquals("ADSO", dominio.programa)
        assertEquals("activo", dominio.fichaEstado)
        assertEquals(2, dominio.instructorId)
	    assertTrue(dominio.esEditable)
    }

    @Test
    fun generalUser_parsesAdminCredentialFields() {
        val cuerpo = """
            {
              "id": 5,
              "nombre": "Ana",
              "apellido": "Gómez",
              "correo": "ana@sena.edu.co",
              "username": "agomezr_12345",
              "rol": "aprendiz",
              "estado": true,
              "must_change_password": true,
              "tipo_documento": "CC",
              "numero_documento": "1098",
              "credenciales_enviadas_en": "2026-10-01T12:00:00Z"
            }
        """.trimIndent()
        val dominio = json.decodeFromString<GeneralUserDto>(cuerpo).toDomain()
        assertEquals("agomezr_12345", dominio.username)
        assertTrue(dominio.mustChangePassword)
        assertEquals("CC", dominio.tipoDocumento)
        assertEquals("1098", dominio.documentoIdentidad)
        assertNotNull(dominio.credencialesEnviadasEn)
    }

    @Test
    fun dashboardMetrics_parseaElContratoAdmin() {
        val cuerpo = """
            {
              "rol": "admin",
              "periodo": "30d",
              "usuarios": {"total": 10, "aprendices": 7, "instructores": 2, "admins": 1},
              "fichas": {"total": 3},
              "aprendices": {"total": 7},
              "propuestas": {"total": 5, "borrador": 1, "pendiente": 2, "aprobado": 2, "rechazado": 0},
              "evolucion": [
                {"clave":"2026-10-20","label":"20/10","fecha":"martes 20 de octubre","total":2,"propuestas":["A","B"],"restantes":0}
              ],
              "actividad": [
                {"id":9,"titulo":"revisar_propuesta","meta":"projects #12","usuario":"Ana Pérez","proyecto_id":12,"fecha":"2026-10-20T10:00:00-05:00"}
              ]
            }
        """.trimIndent()
        val dominio = json.decodeFromString<DashboardMetricsDto>(cuerpo).toDomain()
        assertEquals("admin", dominio.rol)
        assertEquals(10, dominio.usuarios.total)
        assertEquals(3, dominio.fichasTotal)
        assertEquals(5, dominio.propuestas.total)
        assertEquals(1, dominio.evolucion.size)
        assertEquals(2, dominio.evolucion.first().total)
        assertEquals(12, dominio.actividad.first().proyectoId)
    }

    // ------------------------------------------------- Contratos de request (Fase 1)

    @Test
    fun trainingProgramRequest_incluyeNivelObligatorio() {
        // El backend exige `nivel => required`; el request no debe omitirlo.
        val texto = json.encodeToString(
            TrainingProgramRequest(nombre = "ADSO", nivel = "Tecnólogo", knowledgeNetworkId = 1),
        )
        assertTrue(texto.contains("\"nivel\":\"Tecnólogo\""))
    }

    @Test
    fun classGroupCreateRequest_enviaNumeroYSinCodigo() {
        // El backend genera `codigo`; el cliente solo envía `numero`.
        val texto = json.encodeToString(
            ClassGroupCreateRequest(
                numero = "3067030",
                nombre = "Ficha ADSO",
                estado = "activo",
                idPrograma = 1,
                idInstructor = 2,
            ),
        )
        assertTrue(texto.contains("\"numero\":\"3067030\""))
        assertFalse(texto.contains("codigo"))
    }

    @Test
    fun classGroupUpdateRequest_enviaNumeroYSinCodigo() {
        val texto = json.encodeToString(
            ClassGroupUpdateRequest(
                numero = "3067030",
                nombre = "Ficha ADSO",
                estado = "finalizado",
                idPrograma = 1,
                idInstructor = 2,
            ),
        )
        assertTrue(texto.contains("\"numero\":\"3067030\""))
        assertFalse(texto.contains("codigo"))
    }

    @Test
    fun notificationReadRequest_soloEnviaLeida() {
        // El PUT del dueño solo cambia `leida`: sin destinatario ni 0.
        val texto = json.encodeToString(NotificationReadRequest(leida = true))
        assertEquals("{\"leida\":true}", texto)
        assertFalse(texto.contains("id_usuario"))
    }

    @Test
    fun notificationRequest_sinDestinatarioNoSerializaCero() {
        val texto = json.encodeToString(
            NotificationRequest(
                titulo = "Aviso",
                descripcion = null,
                tipo = "sistema",
                enlace = null,
                leida = false,
                fecha = "2026-10-07",
            ),
        )
        assertFalse(texto.contains("id_usuario"))
        assertFalse(texto.contains(":0"))
    }

    @Test
    fun bugReportCreateRequest_noEnviaCamposDelServidor() {
        // `estado` e `id_usuario` los controla el backend (pendiente + token).
        val texto = json.encodeToString(
            BugReportCreateRequest(descripcion = "No carga la lista", tipo = "bug_ui", fecha = "2026-10-07"),
        )
        assertFalse(texto.contains("estado"))
        assertFalse(texto.contains("id_usuario"))
    }
}
