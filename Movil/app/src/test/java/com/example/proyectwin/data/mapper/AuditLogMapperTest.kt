package com.example.proyectwin.data.mapper

import com.example.proyectwin.data.api.dto.AuditLogDto
import com.example.proyectwin.data.api.dto.GeneralUserDto
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mapeo del `detalle` de la bitácora con el mismo formato que la web. */
class AuditLogMapperTest {

    @Test
    fun `detalle se formatea como clave valor y listas con comas`() {
        val dto = AuditLogDto(
            id = 1,
            idUsuario = 9,
            accion = "agregar_aprendiz",
            entidad = "class_groups",
            entidadId = 3,
            detalle = buildJsonObject {
                put("usuario", 5)
                put("creada", true)
                put("campos", buildJsonArray { add(JsonPrimitive("nombre")); add(JsonPrimitive("correo")) })
                put("nota", null as String?)
            },
            user = GeneralUserDto(id = 9, nombre = "Ana", apellido = "Martínez"),
        )

        val log = dto.toDomain()

        assertEquals("usuario: 5 · creada: true · campos: nombre, correo · nota: null", log.detalle)
        assertEquals("Ana Martínez", log.usuarioNombre)
        assertEquals(9, log.usuarioId)
        assertEquals("class_groups", log.entidad)
        assertEquals(3, log.entidadId)
    }

    @Test
    fun `detalle nulo y usuario ausente se conservan`() {
        val log = AuditLogDto(id = 2, accion = "recalibrar_motor").toDomain()

        assertNull(log.detalle)
        assertNull(log.usuarioNombre)
        assertNull(log.usuarioId)
    }
}
