package com.example.proyectwin.domain.repository

import com.example.proyectwin.data.model.KnowledgeNetwork
import com.example.proyectwin.data.model.TrainingProgram

/** Catálogos institucionales: redes de conocimiento y programas de formación. */
interface CatalogsRepository {

    suspend fun listarRedes(): Result<List<KnowledgeNetwork>>

    suspend fun listarProgramas(): Result<List<TrainingProgram>>

    suspend fun crearRed(nombre: String): Result<KnowledgeNetwork>

    suspend fun actualizarRed(id: Int, nombre: String): Result<KnowledgeNetwork>

    suspend fun eliminarRed(id: Int): Result<Unit>

    suspend fun crearPrograma(
        nombre: String,
        nivel: String,
        redId: Int,
    ): Result<TrainingProgram>

    suspend fun actualizarPrograma(
        id: Int,
        nombre: String,
        nivel: String,
        redId: Int,
    ): Result<TrainingProgram>

    suspend fun eliminarPrograma(id: Int): Result<Unit>
}
