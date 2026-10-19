package com.example.proyectwin.domain.repository

import com.example.proyectwin.data.model.CredencialesUsuario
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.data.model.PaginaUsuarios

/** Cuentas de usuario (`general-users`): listado admin, gestión y credenciales. */
interface UsersRepository {

    suspend fun listar(
        search: String? = null,
        role: String? = null,
        estado: String? = null,
        fichaId: Int? = null,
    ): Result<List<GeneralUser>>

    /**
     * Listado paginado del servidor (panel admin). `fichaId` acepta el id de la
     * ficha como texto o `sin` (usuarios sin ficha); `null` = sin filtrar.
     */
    suspend fun listarPaginado(
        page: Int,
        porPagina: Int,
        search: String? = null,
        role: String? = null,
        estado: String? = null,
        fichaId: String? = null,
        programa: String? = null,
    ): Result<PaginaUsuarios>

    suspend fun obtener(id: Int): Result<GeneralUser>

    /** Perfil público (solo datos básicos de contacto). */
    suspend fun perfil(id: Int): Result<GeneralUser>

    /** Cuenta del token con ficha/perfil incluidos; refresca la sesión local. */
    suspend fun miCuenta(): Result<GeneralUser>

    /** Alta (admin): el backend genera username y contraseña temporal. */
    suspend fun crear(
        nombre: String,
        apellido: String,
        tipoDocumento: String,
        numeroDocumento: String,
        correo: String,
        rol: String,
    ): Result<CredencialesUsuario>

    /** PUT parcial: [datos] aporta nombre/apellido/correo/rol/estado. */
    suspend fun actualizar(id: Int, datos: GeneralUser): Result<GeneralUser>

    suspend fun eliminar(id: Int): Result<Unit>

    /** Nuevas credenciales temporales (no envía correo; lo reenvía el admin). */
    suspend fun restablecerCredenciales(id: Int): Result<CredencialesUsuario>

    /** Reenvía por correo la contraseña temporal vigente. */
    suspend fun reenviarCredenciales(id: Int): Result<GeneralUser>

    /** PDF binario con las credenciales de los usuarios indicados. */
    suspend fun descargarCredencialesPdf(ids: List<Int>): Result<ByteArray>
}
