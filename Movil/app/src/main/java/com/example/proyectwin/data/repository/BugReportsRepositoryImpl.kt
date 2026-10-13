package com.example.proyectwin.data.repository

import com.example.proyectwin.data.api.safeApiCall
import com.example.proyectwin.data.api.service.BugReportsApi
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.mapper.hoy
import com.example.proyectwin.data.mapper.toCreateRequest
import com.example.proyectwin.data.mapper.toDomain
import com.example.proyectwin.data.mapper.toUpdateRequest
import com.example.proyectwin.data.model.BugReport
import com.example.proyectwin.domain.repository.BugReportsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BugReportsRepositoryImpl @Inject constructor(
    private val api: BugReportsApi,
    private val sessionManager: SessionManager,
) : BugReportsRepository {

    override suspend fun listar(): Result<List<BugReport>> =
        safeApiCall { api.listar(INCLUDE) }.map { lista -> lista.map { it.toDomain() } }

    override suspend fun obtener(id: Int): Result<BugReport> =
        safeApiCall { api.obtener(id, INCLUDE) }.map { it.toDomain() }

    override suspend fun crear(
        titulo: String?,
        descripcion: String,
        tipo: String,
        numeroFicha: String?,
        motivo: String?,
    ): Result<BugReport> =
        safeApiCall {
            api.crear(
                BugReport(
                    id = 0,
                    titulo = titulo.orEmpty(),
                    numeroFicha = numeroFicha,
                    motivo = motivo,
                    descripcion = descripcion,
                    tipo = tipo,
                ).toCreateRequest(),
            )
        }.map { it.toDomain() }

    override suspend fun actualizar(id: Int, reporte: BugReport): Result<BugReport> {
        val idUsuario = reporte.reporterId
            ?: sessionManager.currentUser.first()?.id
            ?: return Result.failure(IllegalStateException("No hay sesión activa."))
        val fecha = reporte.fecha ?: reporte.createdAt?.take(10) ?: hoy()
        return safeApiCall { api.actualizar(id, reporte.toUpdateRequest(fecha, idUsuario)) }
            .map { it.toDomain() }
    }

    override suspend fun eliminar(id: Int): Result<Unit> =
        safeApiCall { api.eliminar(id) }.map {}

    companion object {
        private const val INCLUDE = "generalUser"
    }
}
