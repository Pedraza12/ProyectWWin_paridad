package com.example.proyectwin.data.repository

import com.example.proyectwin.data.api.dto.KnowledgeNetworkRequest
import com.example.proyectwin.data.api.dto.TrainingProgramRequest
import com.example.proyectwin.data.api.safeApiCall
import com.example.proyectwin.data.api.service.CatalogsApi
import com.example.proyectwin.data.mapper.toDomain
import com.example.proyectwin.data.model.KnowledgeNetwork
import com.example.proyectwin.data.model.TrainingProgram
import com.example.proyectwin.domain.repository.CatalogsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogsRepositoryImpl @Inject constructor(
    private val api: CatalogsApi,
) : CatalogsRepository {

    override suspend fun listarRedes(): Result<List<KnowledgeNetwork>> =
        safeApiCall { api.listarRedes() }.map { lista -> lista.map { it.toDomain() } }

    override suspend fun listarProgramas(): Result<List<TrainingProgram>> =
        safeApiCall { api.listarProgramas(INCLUDE) }.map { lista -> lista.map { it.toDomain() } }

    override suspend fun crearRed(nombre: String): Result<KnowledgeNetwork> =
        safeApiCall { api.crearRed(KnowledgeNetworkRequest(nombre)) }.map { it.toDomain() }

    override suspend fun actualizarRed(id: Int, nombre: String): Result<KnowledgeNetwork> =
        safeApiCall { api.actualizarRed(id, KnowledgeNetworkRequest(nombre)) }.map { it.toDomain() }

    override suspend fun eliminarRed(id: Int): Result<Unit> =
        safeApiCall { api.eliminarRed(id) }.map {}

    override suspend fun crearPrograma(
        nombre: String,
        nivel: String,
        redId: Int,
    ): Result<TrainingProgram> = safeApiCall {
        api.crearPrograma(
            TrainingProgramRequest(
                nombre = nombre,
                nivel = nivel,
                knowledgeNetworkId = redId,
            ),
        )
    }.map { it.toDomain() }

    override suspend fun actualizarPrograma(
        id: Int,
        nombre: String,
        nivel: String,
        redId: Int,
    ): Result<TrainingProgram> = safeApiCall {
        api.actualizarPrograma(
            id,
            TrainingProgramRequest(
                nombre = nombre,
                nivel = nivel,
                knowledgeNetworkId = redId,
            ),
        )
    }.map { it.toDomain() }

    override suspend fun eliminarPrograma(id: Int): Result<Unit> =
        safeApiCall { api.eliminarPrograma(id) }.map {}

    companion object {
        private const val INCLUDE = "knowledgeNetwork"
    }
}
