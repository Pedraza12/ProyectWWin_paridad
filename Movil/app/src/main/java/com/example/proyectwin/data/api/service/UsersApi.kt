package com.example.proyectwin.data.api.service

import com.example.proyectwin.data.api.dto.CredencialesRequest
import com.example.proyectwin.data.api.dto.FotoUpdateRequest
import com.example.proyectwin.data.api.dto.GeneralUserDto
import com.example.proyectwin.data.api.dto.PaginaGeneralUsersDto
import com.example.proyectwin.data.api.dto.PerfilDto
import com.example.proyectwin.data.api.dto.ProfileUpdateRequest
import com.example.proyectwin.data.api.dto.UserCreateRequest
import com.example.proyectwin.data.api.dto.UserUpdateRequest
import com.example.proyectwin.data.api.dto.UsuarioCredencialesDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Cuentas de usuario (`/v1/general-users`) y sus credenciales. */
interface UsersApi {

    @GET("general-users")
    suspend fun listar(
        @Query("search") search: String? = null,
        @Query("role") role: String? = null,
        @Query("estado") estado: String? = null,
        @Query("ficha_id") fichaId: Int? = null,
        @Query("programa") programa: String? = null,
        @Query("included") included: String? = null,
    ): List<GeneralUserDto>

    /**
     * Listado paginado del servidor (`paginado=1`), usado por el panel admin.
     * `ficha_id` viaja como texto porque el contrato acepta id, `sin` y `todos`.
     */
    @GET("general-users")
    suspend fun listarPaginado(
        @Query("paginado") paginado: Int? = 1,
        @Query("page") page: Int? = null,
        @Query("por_pagina") porPagina: Int? = null,
        @Query("search") search: String? = null,
        @Query("role") role: String? = null,
        @Query("estado") estado: String? = null,
        @Query("ficha_id") fichaId: String? = null,
        @Query("programa") programa: String? = null,
        @Query("included") included: String? = null,
    ): PaginaGeneralUsersDto

    @GET("general-users/{id}")
    suspend fun obtener(
        @Path("id") id: Int,
        @Query("included") included: String? = null,
    ): GeneralUserDto

    @GET("general-users/{id}/perfil")
    suspend fun perfil(@Path("id") id: Int): PerfilDto

    /** Alta (admin): responde `{usuario, credenciales}`. */
    @POST("general-users")
    suspend fun crear(@Body body: UserCreateRequest): UsuarioCredencialesDto

    @PUT("general-users/{id}")
    suspend fun actualizar(@Path("id") id: Int, @Body body: UserUpdateRequest): GeneralUserDto

    /** Edición del propio perfil: nombre/apellido (el correo va por /auth/email). */
    @PUT("general-users/{id}")
    suspend fun actualizarPerfil(@Path("id") id: Int, @Body body: ProfileUpdateRequest): GeneralUserDto

    /** Sube una foto (base64) o la quita enviando `foto_url: null`. */
    @PUT("general-users/{id}")
    suspend fun actualizarFoto(@Path("id") id: Int, @Body body: FotoUpdateRequest): GeneralUserDto

    @DELETE("general-users/{id}")
    suspend fun eliminar(@Path("id") id: Int): GeneralUserDto

    /** PDF de credenciales por lote (binario). */
    @POST("general-users/credenciales")
    suspend fun credencialesPdf(@Body body: CredencialesRequest): ResponseBody

    @POST("general-users/{id}/credenciales/reenviar")
    suspend fun reenviarCredenciales(@Path("id") id: Int): GeneralUserDto

    @POST("general-users/{id}/credenciales/restablecer")
    suspend fun restablecerCredenciales(@Path("id") id: Int): UsuarioCredencialesDto
}
