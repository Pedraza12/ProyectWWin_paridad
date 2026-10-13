package com.example.proyectwin.data.repository

import com.example.proyectwin.data.api.dto.AgregarAprendizRequest
import com.example.proyectwin.data.api.dto.ClassGroupCreateRequest
import com.example.proyectwin.data.api.dto.ClassGroupUpdateRequest
import com.example.proyectwin.data.api.dto.JoinFichaRequest
import com.example.proyectwin.data.api.safeApiCall
import com.example.proyectwin.data.api.service.ApprenticesApi
import com.example.proyectwin.data.api.service.ClassGroupsApi
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.mapper.toDomain
import com.example.proyectwin.data.model.CredencialesUsuario
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.data.model.ResultadoFichaEliminada
import com.example.proyectwin.domain.repository.FichasRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FichasRepositoryImpl @Inject constructor(
    private val api: ClassGroupsApi,
    private val apprenticesApi: ApprenticesApi,
    private val sessionManager: SessionManager,
) : FichasRepository {

    override suspend fun listar(): Result<List<Ficha>> =
        safeApiCall { api.listar(INCLUDE_LISTA) }.map { lista -> lista.map { it.toDomain() } }

    override suspend fun obtener(id: Int): Result<Ficha> =
        safeApiCall { api.obtener(id, INCLUDE_DETALLE) }.map { it.toDomain() }

    override suspend fun crear(
        nombre: String,
        numero: String,
        estado: String,
        idPrograma: Int,
        idInstructor: Int,
        aprendices: List<Int>?,
    ): Result<Ficha> = safeApiCall {
        api.crear(
            ClassGroupCreateRequest(
                numero = numero,
                nombre = nombre,
                estado = estado,
                idPrograma = idPrograma,
                idInstructor = idInstructor,
                aprendices = aprendices,
            ),
        )
    }.map { it.toDomain() }

    override suspend fun actualizar(
        id: Int,
        nombre: String,
        numero: String,
        estado: String,
        idPrograma: Int,
        idInstructor: Int,
        aprendices: List<Int>?,
    ): Result<Ficha> = safeApiCall {
        api.actualizar(
            id,
            ClassGroupUpdateRequest(
                numero = numero,
                nombre = nombre,
                estado = estado,
                idPrograma = idPrograma,
                idInstructor = idInstructor,
                aprendices = aprendices,
            ),
        )
    }.map { it.toDomain() }

    override suspend fun eliminar(id: Int): Result<ResultadoFichaEliminada> =
        safeApiCall { api.eliminar(id) }.map { respuesta ->
            ResultadoFichaEliminada(accion = respuesta.accion, ficha = respuesta.ficha?.toDomain())
        }

    override suspend fun agregarAprendiz(fichaId: Int, usuarioId: Int): Result<Unit> =
        safeApiCall { api.agregarAprendiz(fichaId, AgregarAprendizRequest(usuarioId = usuarioId)) }.map {}

    override suspend fun crearAprendizEnFicha(
        fichaId: Int,
        nombre: String,
        apellido: String,
        tipoDocumento: String,
        numeroDocumento: String,
        correo: String,
    ): Result<CredencialesUsuario?> = safeApiCall {
        api.agregarAprendiz(
            fichaId,
            AgregarAprendizRequest(
                nombre = nombre,
                apellido = apellido,
                tipoDocumento = tipoDocumento,
                numeroDocumento = numeroDocumento,
                correo = correo.trim().lowercase(),
            ),
        )
    }.map { respuesta ->
        respuesta.credenciales?.let { credenciales ->
            CredencialesUsuario(
                usuario = respuesta.usuario?.toDomain()
                    ?: throw IllegalStateException("El backend no devolvió el usuario creado."),
                username = credenciales.username,
                passwordTemporal = credenciales.passwordTemporal,
                enviadas = credenciales.enviadas,
            )
        }
    }

    override suspend fun descargarCredencialesPdf(fichaId: Int): Result<ByteArray> =
        safeApiCall { api.credencialesPdf(fichaId).bytes() }

    override suspend fun fichaPorCodigo(codigo: String): Result<Ficha> =
        safeApiCall { apprenticesApi.fichaPorCodigo(normalizar(codigo)) }.map { it.toDomain() }

    override suspend fun unirmeAFicha(codigo: String): Result<Ficha> {
        val codigoNorm = normalizar(codigo)
        val aprendiz = safeApiCall { apprenticesApi.unirmeAFicha(JoinFichaRequest(codigoNorm)) }
            .getOrElse { return Result.failure(it) }
        val fichaId = aprendiz.idClassGroup
            ?: return Result.failure(IllegalStateException("La ficha no indicó su identificador."))
        sessionManager.joinFicha(fichaId)

        // Devuelve la ficha completa; si la lectura posterior falla, al menos
        // queda la mínima con el id para que la UI continue.
        val ficha: Ficha = safeApiCall { apprenticesApi.fichaPorCodigo(codigoNorm) }
            .map { it.toDomain() }
            .getOrElse { Ficha(id = fichaId, codigo = codigoNorm, programa = "") }
        return Result.success(ficha)
    }

    override suspend fun salirDeFicha(): Result<Unit> =
        safeApiCall { apprenticesApi.salirDeFicha() }.map { sessionManager.clearFicha() }

    private fun normalizar(codigo: String): String = codigo.trim().lowercase()

    companion object {
        // El roster viaja también en el listado: evita una llamada por ficha y
        // permite mostrar integrantes/counts de inmediato.
        private const val INCLUDE_LISTA = "program,instructor.generalUser,apprentices.generalUser"
        private const val INCLUDE_DETALLE = "program,instructor.generalUser,apprentices.generalUser"
    }
}
