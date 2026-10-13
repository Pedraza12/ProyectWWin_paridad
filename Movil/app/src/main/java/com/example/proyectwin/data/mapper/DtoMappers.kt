package com.example.proyectwin.data.mapper

import com.example.proyectwin.data.api.dto.ApprenticeDto
import com.example.proyectwin.data.api.dto.ApprenticeProjectDto
import com.example.proyectwin.data.api.dto.AuditLogDto
import com.example.proyectwin.data.api.dto.BugReportCreateRequest
import com.example.proyectwin.data.api.dto.BugReportDto
import com.example.proyectwin.data.api.dto.BugReportUpdateRequest
import com.example.proyectwin.data.api.dto.ClassGroupDto
import com.example.proyectwin.data.api.dto.CommentDto
import com.example.proyectwin.data.api.dto.DetallesSimilitud
import com.example.proyectwin.data.api.dto.GeneralUserDto
import com.example.proyectwin.data.api.dto.InstructorDto
import com.example.proyectwin.data.api.dto.KnowledgeNetworkDto
import com.example.proyectwin.data.api.dto.MotorConfigDto
import com.example.proyectwin.data.api.dto.NotificationDto
import com.example.proyectwin.data.api.dto.PasajeSimilitud
import com.example.proyectwin.data.api.dto.ProjectCreateRequest
import com.example.proyectwin.data.api.dto.ProjectDto
import com.example.proyectwin.data.api.dto.ProjectHistoryDto
import com.example.proyectwin.data.api.dto.ProjectUpdateRequest
import com.example.proyectwin.data.api.dto.PublicResumenDto
import com.example.proyectwin.data.api.dto.SimilarityDto
import com.example.proyectwin.data.api.dto.TrainingProgramDto
import com.example.proyectwin.data.api.dto.UserUpdateRequest
import com.example.proyectwin.data.model.AuditLog
import com.example.proyectwin.data.model.BugReport
import com.example.proyectwin.data.model.Comment
import com.example.proyectwin.data.model.DetalleSimilitud
import com.example.proyectwin.data.model.DemoCoincidencia
import com.example.proyectwin.data.model.DemoResultado
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.data.model.KnowledgeNetwork
import com.example.proyectwin.data.model.MotorConfig
import com.example.proyectwin.data.model.Notification
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.ProjectDraft
import com.example.proyectwin.data.model.ProjectHistory
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.data.model.ResumenPublico
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.data.model.TeamMember
import com.example.proyectwin.data.model.TrainingProgram
import com.example.proyectwin.data.model.UserRole
import java.time.LocalDate

/** Fecha de hoy en formato `yyyy-MM-dd` (lo exigen `fecha` en reportes/notificaciones). */
fun hoy(): String = LocalDate.now().toString()

// ------------------------------------------------------------ Usuarios

fun GeneralUserDto.nombreCompleto(): String = "$nombre $apellido".trim()

fun GeneralUserDto.toDomain(): GeneralUser = GeneralUser(
    id = id,
    name = nombreCompleto().ifEmpty { correo },
    email = correo,
    role = rol,
    fotoPerfil = fotoUrl,
    fichaId = apprentice?.idClassGroup,
    documentoIdentidad = numeroDocumento,
    tipoDocumento = tipoDocumento,
    nombre = nombre,
    apellido = apellido,
    estado = estado,
    username = username,
    mustChangePassword = mustChangePassword,
    credencialesEnviadasEn = credencialesEnviadasEn,
    credencialesError = credencialesError,
)

/** Usuario anidado en otra relación: conserva el vínculo con su ficha si lo trae. */
fun GeneralUserDto.toDomain(fichaId: Int?): GeneralUser = toDomain().copy(
    fichaId = fichaId ?: apprentice?.idClassGroup,
)

fun GeneralUser.toUpdateRequest(): UserUpdateRequest = UserUpdateRequest(
    nombre = nombre ?: name.substringBefore(' ').ifEmpty { name },
    apellido = apellido ?: name.substringAfter(' ', "").ifEmpty { nombre ?: name },
    correo = email,
    rol = role,
    estado = estado,
)

/** Divide un nombre completo en (nombre, apellido) como fallback de cuentas antiguas. */
fun dividirNombre(name: String): Pair<String, String> {
    val limpio = name.trim()
    val nombre = limpio.substringBefore(' ').ifEmpty { limpio }
    val apellido = limpio.substringAfter(' ', "").trim().ifEmpty { nombre }
    return nombre to apellido
}

fun InstructorDto.nombreUsuario(): String? = generalUser?.nombreCompleto()

fun InstructorDto.toProfile(): com.example.proyectwin.data.model.InstructorProfile =
    com.example.proyectwin.data.model.InstructorProfile(
        id = id,
        idUsuario = idUsuario,
        fechaIngreso = fechaIngreso,
        nombre = generalUser?.nombreCompleto().orEmpty(),
        correo = generalUser?.correo.orEmpty(),
        fotoUrl = generalUser?.fotoUrl,
    )

fun ApprenticeDto.toProfile(): com.example.proyectwin.data.model.ApprenticeProfile =
    com.example.proyectwin.data.model.ApprenticeProfile(
        id = id,
        codigo = codigo,
        idUsuario = idUsuario,
        idClassGroup = idClassGroup,
        idPrograma = idPrograma,
        nombre = generalUser?.nombreCompleto().orEmpty(),
        correo = generalUser?.correo.orEmpty(),
        fotoUrl = generalUser?.fotoUrl,
    )

fun ApprenticeDto.toGeneralUser(): GeneralUser? =
    generalUser?.toDomain(idClassGroup)

// ------------------------------------------------------------ Fichas

fun ClassGroupDto.toDomain(): Ficha = Ficha(
    id = id,
    codigo = codigo,
    programa = program?.nombre ?: "",
    estado = estado,
    instructorId = idInstructor.takeIf { it != 0 },
    instructorName = instructor?.nombreUsuario(),
    createdAt = createdAt,
    estudiantes = apprentices?.mapNotNull { it.toGeneralUser() } ?: emptyList(),
    nombre = nombre,
    numero = numero,
    idPrograma = idPrograma.takeIf { it != 0 },
)

// ------------------------------------------------------------ Catálogos

fun KnowledgeNetworkDto.toDomain(): KnowledgeNetwork = KnowledgeNetwork(
    id = id,
    nombre = nombre,
)

fun TrainingProgramDto.toDomain(): TrainingProgram = TrainingProgram(
    id = id,
    nombre = nombre,
    nivel = nivel,
    redId = knowledgeNetworkId.takeIf { it != 0 },
    redNombre = knowledgeNetwork?.nombre,
)

// ------------------------------------------------------------ Propuestas

fun ProjectDto.toDomain(): Project = Project(
    id = id,
    title = titulo,
    description = resumen,
    estado = estado,
    studentId = idCreador.takeIf { it != 0 },
    instructorId = idInstructorAsignado,
    fichaId = idClassGroup,
    createdAt = createdAt,
    updatedAt = updatedAt,
    studentName = creator?.nombreCompleto() ?: apprentices?.firstNotNullOfOrNull { it.toGeneralUser()?.name },
    instructorName = instructor?.nombreUsuario(),
    palabrasClave = palabrasClave,
    areaAplicacion = areaAplicacion,
    objetivoGeneral = objetivoGeneral,
    objetivosEspecificos = objetivosEspecificos ?: emptyList(),
    programa = classGroup?.program?.nombre,
    fichaEstado = classGroup?.estado,
    equipo = apprentices?.mapNotNull { it.toGeneralUser() } ?: emptyList(),
)

fun Project.toDraft(): ProjectDraft = ProjectDraft(
    titulo = title,
    resumen = description,
    palabrasClave = palabrasClave,
    areaAplicacion = areaAplicacion,
    objetivoGeneral = objetivoGeneral,
    objetivosEspecificos = objetivosEspecificos,
    estado = estado,
    idCreador = studentId ?: 0,
    idInstructorAsignado = instructorId,
    idClassGroup = fichaId,
)

fun ProjectDraft.toCreateRequest(): ProjectCreateRequest = ProjectCreateRequest(
    titulo = titulo,
    resumen = resumen,
    palabrasClave = palabrasClave,
    areaAplicacion = areaAplicacion,
    objetivoGeneral = objetivoGeneral,
    objetivosEspecificos = objetivosEspecificos,
    estado = estado,
    idCreador = idCreador,
    idInstructorAsignado = idInstructorAsignado,
    idClassGroup = idClassGroup,
)

fun ProjectDraft.toUpdateRequest(): ProjectUpdateRequest = ProjectUpdateRequest(
    titulo = titulo,
    resumen = resumen,
    palabrasClave = palabrasClave,
    areaAplicacion = areaAplicacion,
    objetivoGeneral = objetivoGeneral,
    objetivosEspecificos = objetivosEspecificos,
    estado = estado,
    idCreador = idCreador,
    idInstructorAsignado = idInstructorAsignado,
    idClassGroup = idClassGroup,
    observacion = observacion,
)

/** Equipo: fila del pivote + usuario embebido (incluye `apprentice.generalUser`). */
fun ApprenticeProjectDto.toTeamMember(): TeamMember? {
    val usuario = apprentice?.toGeneralUser() ?: return null
    return TeamMember(idPivote = id, idAprendiz = idAprendiz, usuario = usuario)
}

fun ProjectHistoryDto.toDomain(): ProjectHistory {
    val mapa = detalle?.entries?.associate { (clave, valor) ->
        clave to valor.toString().trim('"')
    }.orEmpty()
    return ProjectHistory(
        id = id,
        accion = accion,
        detalle = mapa.entries.joinToString(" · ") { "${it.key}: ${it.value}" }.ifEmpty { null },
        detalleMap = mapa,
        usuarioNombre = user?.nombreCompleto(),
        createdAt = createdAt,
    )
}

// ------------------------------------------------------------ Similitudes

fun DetallesSimilitud.toDomain(): DetalleSimilitud = DetalleSimilitud(
    palabras = palabras,
    caracteres = caracteres,
    // `tema` puede venir como número (porcentaje del motor anterior) o texto.
    tema = tema?.let { elemento ->
        (elemento as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeUnless { it is kotlinx.serialization.json.JsonNull }
            ?.content
    },
    cobertura = cobertura,
    terminos = terminos,
    pasajes = pasajes.map { it.toDomain() },
)

fun PasajeSimilitud.toDomain(): com.example.proyectwin.data.model.PasajeSimilitud =
    com.example.proyectwin.data.model.PasajeSimilitud(a = a, b = b, score = score)

fun SimilarityDto.toDomain(): Similarity {
    val creador1 = project1?.creator?.nombreCompleto()
    val creador2 = project2?.creator?.nombreCompleto()
    return Similarity(
        id = id,
        projectId1 = idProyecto1,
        projectId2 = idProyecto2,
        project1 = project1?.toDomain(),
        project2 = project2?.toDomain(),
        project1Title = project1?.titulo,
        project2Title = project2?.titulo,
        project1Student = creador1 ?: project1?.apprentices?.firstNotNullOfOrNull { it.toGeneralUser()?.name },
        project2Student = creador2 ?: project2?.apprentices?.firstNotNullOfOrNull { it.toGeneralUser()?.name },
        // El backend guarda porcentaje 0–100; el dominio usa fracción 0–1.
        similitud = porcentaje / 100.0,
        vigente = vigente,
        createdAt = createdAt,
        fecha = fecha,
        detalles = detalles?.toDomain(),
    )
}

// ------------------------------------------------------------ Notificaciones

fun NotificationDto.toDomain(): Notification = Notification(
    id = id,
    mensaje = titulo.ifEmpty { descripcion ?: "" },
    tipo = tipo,
    userId = idUsuario.takeIf { it != 0 },
    leido = leida != 0,
    createdAt = fecha ?: createdAt,
    titulo = titulo,
    descripcion = descripcion,
    enlace = enlace,
    fecha = fecha,
)

// ------------------------------------------------------------ Reportes de falla

fun BugReportDto.toDomain(): BugReport = BugReport(
    id = id,
    titulo = titulo ?: "",
    numeroFicha = numeroFicha,
    motivo = motivo,
    descripcion = descripcion,
    tipo = tipo,
    estado = estado,
    respuesta = respuesta,
    reporterId = idUsuario.takeIf { it != 0 },
    reporterName = generalUser?.nombreCompleto(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    fecha = fecha,
)

fun BugReport.toCreateRequest(fecha: String = hoy()): BugReportCreateRequest =
    BugReportCreateRequest(
        titulo = titulo.ifEmpty { null },
        numeroFicha = numeroFicha,
        motivo = motivo,
        descripcion = descripcion,
        tipo = tipo,
        fecha = fecha,
    )

fun BugReport.toUpdateRequest(fecha: String, idUsuario: Int): BugReportUpdateRequest =
    BugReportUpdateRequest(
        titulo = titulo.ifEmpty { null },
        numeroFicha = numeroFicha,
        motivo = motivo,
        descripcion = descripcion,
        tipo = tipo,
        estado = estado,
        respuesta = respuesta,
        fecha = fecha,
        idUsuario = idUsuario,
    )

// ------------------------------------------------------------ Observaciones

fun CommentDto.toDomain(): Comment = Comment(
    id = id,
    texto = texto,
    proyectoId = idProyecto,
    usuarioId = idUsuario,
    respuestaA = respuestaA,
    autorNombre = user?.nombreCompleto(),
    autorFoto = user?.fotoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    respuestas = replies?.map { it.toDomain() } ?: emptyList(),
)

// ------------------------------------------------------------ Motor y demos

fun MotorConfigDto.toDomain(): MotorConfig = MotorConfig(id = id, umbral = umbral, meses = meses)

fun PublicResumenDto.toDomain(): ResumenPublico = ResumenPublico(
    umbral = umbral,
    meses = meses,
    totalPropuestas = totalPropuestas,
    totalProgramas = totalProgramas,
)

fun com.example.proyectwin.data.api.dto.DemoSimilitudResponse.toDomain(): DemoResultado =
    DemoResultado(
        umbral = umbral,
        meses = meses,
        total = total,
        sobre = sobre,
        coincidencias = coincidencias.map { DemoCoincidencia(it.id, it.titulo, it.porcentaje) },
    )

// ------------------------------------------------------------ Bitácora

fun AuditLogDto.toDomain(): AuditLog = AuditLog(
    id = id,
    accion = accion,
    entidad = entidad,
    entidadId = entidadId,
    usuarioId = idUsuario,
    usuarioNombre = user?.nombreCompleto(),
    detalle = detalle?.toString(),
    ip = ip,
    createdAt = createdAt,
)

// ------------------------------------------------------------ Sesión

/** Usuario de login + token: lo que se persiste en la sesión local. */
fun com.example.proyectwin.data.api.dto.LoginResponse.toSessionUser(): GeneralUser =
    user.toDomain().copy(token = token)
