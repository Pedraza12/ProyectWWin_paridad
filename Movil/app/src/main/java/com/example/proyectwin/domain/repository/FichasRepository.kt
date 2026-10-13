package com.example.proyectwin.domain.repository

import com.example.proyectwin.data.model.CredencialesUsuario
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.data.model.ResultadoFichaEliminada

/** Fichas de formación (`class-groups`) y membresía personal del aprendiz. */
interface FichasRepository {

    suspend fun listar(): Result<List<Ficha>>

    suspend fun obtener(id: Int): Result<Ficha>

    suspend fun crear(
        nombre: String,
        numero: String,
        estado: String,
        idPrograma: Int,
        idInstructor: Int,
        /** Usuarios a matricular (ids de `general_users`). */
        aprendices: List<Int>? = null,
    ): Result<Ficha>

    suspend fun actualizar(
        id: Int,
        nombre: String,
        numero: String,
        estado: String,
        idPrograma: Int,
        idInstructor: Int,
        /** Roster completo; el backend sincroniza altas y bajas. */
        aprendices: List<Int>? = null,
    ): Result<Ficha>

    /** Elimina la ficha; si tenía dependencias el backend la anula. */
    suspend fun eliminar(id: Int): Result<ResultadoFichaEliminada>

    /** Asocia una cuenta existente a la ficha (admin/instructor). */
    suspend fun agregarAprendiz(fichaId: Int, usuarioId: Int): Result<Unit>

    /** Crea la cuenta y la matricula; devuelve credenciales si hubo alta. */
    suspend fun crearAprendizEnFicha(
        fichaId: Int,
        nombre: String,
        apellido: String,
        tipoDocumento: String,
        numeroDocumento: String,
        correo: String,
    ): Result<CredencialesUsuario?>

    /** PDF de credenciales de la ficha (solo admin). */
    suspend fun descargarCredencialesPdf(fichaId: Int): Result<ByteArray>

    /** Previsualiza una ficha por su código (flujo "unirme con código"). */
    suspend fun fichaPorCodigo(codigo: String): Result<Ficha>

    /** Se une a la ficha del código y refresca la sesión local. */
    suspend fun unirmeAFicha(codigo: String): Result<Ficha>

    /** Sale de la ficha actual y limpia la sesión local. */
    suspend fun salirDeFicha(): Result<Unit>
}
