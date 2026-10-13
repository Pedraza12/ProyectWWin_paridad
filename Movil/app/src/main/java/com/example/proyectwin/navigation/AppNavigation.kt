package com.example.proyectwin.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.screens.aprendiz.*
import com.example.proyectwin.ui.screens.instructor.*
import com.example.proyectwin.ui.screens.admin.*
import com.example.proyectwin.ui.screens.auth.*
import com.example.proyectwin.ui.screens.AlertsScreen
import com.example.proyectwin.ui.screens.EditProfileScreen
import com.example.proyectwin.ui.screens.FichaDetailScreen
import com.example.proyectwin.ui.screens.ReportIssueScreen

object AppNavigation {
    // Auth Graph
    const val AUTH_GRAPH = "auth_graph"
    const val LOGIN = "login"
    const val FORGOT_PASSWORD = "forgot_password"

    /** Token/correo pueden venir del `reset_url` (modo local) o pegarse a mano. */
    const val RESET_PASSWORD = "reset_password?correo={correo}&token={token}"
    const val CONFIRMATION = "confirmation?correo={correo}&resetUrl={resetUrl}"

    /** Cambio normal de contraseña desde el perfil. */
    const val CHANGE_PASSWORD = "change_password"

    /** Cambio obligatorio (must_change_password): destino de nivel raíz. */
    const val CAMBIO_OBLIGATORIO = "cambio_obligatorio"
    const val NOT_FOUND = "not_found"

    fun rutaResetPassword(correo: String, token: String): String =
        "reset_password?correo=${android.net.Uri.encode(correo)}&token=${android.net.Uri.encode(token)}"

    fun rutaConfirmacion(correo: String, resetUrl: String?): String =
        "confirmation?correo=${android.net.Uri.encode(correo)}&resetUrl=${android.net.Uri.encode(resetUrl ?: "")}"

    /**
     * Alta/edición de propuesta. `projectId` vacío = alta; `retorno` decide a
     * qué detalle se vuelve al guardar (el admin edita desde su propio detalle).
     */
    fun rutaNuevoProyecto(projectId: String = "", retorno: String = "aprendiz"): String =
        "aprendiz_new_project/$projectId?retorno=$retorno"

    // Aprendiz Graph
    const val APRENDIZ_GRAPH = "aprendiz_graph"
    const val APRENDIZ_DASHBOARD = "aprendiz_dashboard"
    const val APRENDIZ_PROJECTS = "aprendiz_projects"
    const val APRENDIZ_NEW_PROJECT = "aprendiz_new_project/{projectId}?retorno={retorno}"
    const val APRENDIZ_DETAIL = "aprendiz_detail/{projectId}"
    const val APRENDIZ_SIMILARITY = "aprendiz_similarity/{similarityId}"
    /** Lista completa; [APRENDIZ_SIMILITUDES] agrega el filtro opcional. */
    const val APRENDIZ_SIMILITUDES_BASE = "aprendiz/similitudes"
    const val APRENDIZ_SIMILITUDES = "aprendiz/similitudes?proyectoId={proyectoId}"
    const val APRENDIZ_PROFILE = "aprendiz_profile"
    const val APRENDIZ_ALERTS = "aprendiz/alerts"
    const val APRENDIZ_FICHA_DETAIL = "aprendiz/ficha"
    const val APRENDIZ_JOIN_FICHA = "aprendiz/join-ficha"
    const val APRENDIZ_COMPANERO_DETAIL = "aprendiz/companero/{userId}"

    // Instructor Graph
    const val INSTRUCTOR_GRAPH = "instructor_graph"
    const val INSTRUCTOR_DASHBOARD = "instructor_dashboard"
    const val INSTRUCTOR_FICHAS = "instructor_fichas"
    /** Alta y edición comparten pantalla; `fichaId` vacío = alta. */
    const val INSTRUCTOR_CREAR_FICHA_BASE = "instructor_crear_ficha"
    const val INSTRUCTOR_CREAR_FICHA = "instructor_crear_ficha?fichaId={fichaId}"
    const val INSTRUCTOR_REVISION = "instructor/revision"
    const val INSTRUCTOR_DETAIL = "instructor_detail/{projectId}"
    const val INSTRUCTOR_PROFILE = "instructor/profile"
    const val INSTRUCTOR_SIMILARITY_DETAIL = "instructor/similarity-detail/{similarityId}"
    /** Lista completa; [INSTRUCTOR_SIMILITUDES] agrega el filtro opcional. */
    const val INSTRUCTOR_SIMILITUDES_BASE = "instructor/similitudes"
    const val INSTRUCTOR_SIMILITUDES = "instructor/similitudes?proyectoId={proyectoId}"
    const val INSTRUCTOR_MANAGE_FICHAS = "instructor/fichas/manage"
    const val INSTRUCTOR_ALERTS = "instructor/alerts"
    const val INSTRUCTOR_FICHA_DETAIL = "instructor_ficha_detail/{fichaId}"

    // Admin Graph
    const val ADMIN_GRAPH = "admin_graph"
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val ADMIN_USERS = "admin/users"
    const val ADMIN_BUGS = "admin/bugs"
    const val ADMIN_PROJECTS = "admin/projects"
    const val ADMIN_PROJECT_DETAIL = "admin/project/{projectId}"
    /** Ruta limpia del tab; [ADMIN_SIMILARITY_LIST_FILTRO] agrega el filtro. */
    const val ADMIN_SIMILARITY_LIST = "admin/similarities"
    const val ADMIN_SIMILARITY_LIST_FILTRO = "admin/similarities?proyectoId={proyectoId}"
    const val ADMIN_SIMILARITY_DETAIL = "admin/similarity/{similarityId}"
    const val ADMIN_NEW_USER = "admin/users/new"
    const val ADMIN_USER_DETAIL = "admin/user/{userId}"
    const val ADMIN_PROFILE = "admin/profile"
    const val ADMIN_BUG_DETAIL = "admin/bug/{bugId}"
    const val ADMIN_ALERTS = "admin/alerts"
    const val ADMIN_FICHAS = "admin/fichas"
    const val ADMIN_CATALOGOS = "admin/catalogos"
    const val ADMIN_BITACORA = "admin/bitacora"
    const val ADMIN_MOTOR = "admin/motor"

    // Shared screens
    const val EDIT_PROFILE = "edit_profile"
    const val REPORT_ISSUE = "report_issue"
    const val CHANGE_EMAIL = "change_email"

    // Containers
    const val ADMIN_MAIN = "admin_main"

    val aprendizTabs = listOf(
        SenaBottomNavItem(APRENDIZ_DASHBOARD, "Inicio", Icons.Default.Home, Icons.Default.Home),
        SenaBottomNavItem(APRENDIZ_PROJECTS, "Proyectos", Icons.Default.Folder, Icons.Default.Folder),
        SenaBottomNavItem(APRENDIZ_ALERTS, "Alertas", Icons.Default.Notifications, Icons.Default.Notifications),
        SenaBottomNavItem(APRENDIZ_PROFILE, "Perfil", Icons.Default.Person, Icons.Default.Person)
    )

    val instructorTabs = listOf(
        SenaBottomNavItem(INSTRUCTOR_DASHBOARD, "Inicio", Icons.Default.Home, Icons.Default.Home),
        SenaBottomNavItem(INSTRUCTOR_REVISION, "Revisiones", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Filled.List),
        SenaBottomNavItem(INSTRUCTOR_MANAGE_FICHAS, "Fichas", Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Filled.Assignment),
        SenaBottomNavItem(INSTRUCTOR_PROFILE, "Perfil", Icons.Default.Person, Icons.Default.Person)
    )

    val adminTabs = listOf(
        SenaBottomNavItem(ADMIN_DASHBOARD, "Inicio", Icons.Default.Home, Icons.Default.Home),
        SenaBottomNavItem(ADMIN_USERS, "Usuarios", Icons.Default.People, Icons.Default.People),
        SenaBottomNavItem(ADMIN_SIMILARITY_LIST, "Similitud", Icons.Default.Compare, Icons.Default.Compare),
        SenaBottomNavItem(ADMIN_PROFILE, "Perfil", Icons.Default.Person, Icons.Default.Person)
    )

    fun NavGraphBuilder.authGraph(navController: NavHostController) {
        navigation(startDestination = LOGIN, route = AUTH_GRAPH) {
            composable(LOGIN) { LoginScreen(
                onForgotPasswordClick = { navController.navigate(FORGOT_PASSWORD) }
            ) }
            composable(FORGOT_PASSWORD) { ForgotPasswordScreen(
                onBackToLogin = { navController.popBackStack() },
                onCodeSent = { correo, resetUrl ->
                    navController.navigate(rutaConfirmacion(correo, resetUrl))
                }
            ) }
            composable(
                route = CONFIRMATION,
                arguments = listOf(
                    navArgument("correo") { type = NavType.StringType; defaultValue = "" },
                    navArgument("resetUrl") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entrada ->
                ConfirmationScreen(
                    correo = entrada.arguments?.getString("correo").orEmpty(),
                    resetUrl = entrada.arguments?.getString("resetUrl").orEmpty().ifBlank { null },
                    onGoToLogin = { navController.navigate(LOGIN) { popUpTo(AUTH_GRAPH) { inclusive = true } } },
                    onEnterCode = { correo, token ->
                        navController.navigate(rutaResetPassword(correo, token))
                    },
                )
            }
            composable(
                route = RESET_PASSWORD,
                arguments = listOf(
                    navArgument("correo") { type = NavType.StringType; defaultValue = "" },
                    navArgument("token") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entrada ->
                ResetPasswordScreen(
                    correoInicial = entrada.arguments?.getString("correo").orEmpty(),
                    tokenInicial = entrada.arguments?.getString("token").orEmpty(),
                    onBackToLogin = { navController.popBackStack() },
                    onResetSuccess = { navController.navigate(LOGIN) { popUpTo(LOGIN) { inclusive = true } } },
                )
            }
            composable(CHANGE_PASSWORD) {
                ChangePasswordScreen(onBack = { navController.popBackStack() })
            }
            composable(NOT_FOUND) { NotFoundScreen(
                onGoHome = { navController.navigate(LOGIN) { popUpTo(AUTH_GRAPH) { inclusive = true } } },
                onGoLogin = { navController.navigate(LOGIN) }
            ) }
        }
    }

    fun NavGraphBuilder.aprendizGraph(navController: NavHostController) {
        val bottomBar = @Composable {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            SenaBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { route -> 
                    navController.navigate(route) {
                        popUpTo(APRENDIZ_DASHBOARD) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                items = aprendizTabs
            )
        }

        navigation(startDestination = APRENDIZ_DASHBOARD, route = APRENDIZ_GRAPH) {
            composable(APRENDIZ_DASHBOARD) { 
                DashboardScreen(onNavigate = { route -> navController.navigateTo(route) }, bottomBar = { bottomBar() }) 
            }
            composable(APRENDIZ_PROJECTS) { ProjectsScreen(
                onNavigate = { route -> navController.navigateTo(route) },
                onNewProject = { navController.navigate(rutaNuevoProyecto()) },
                onProjectDetail = { id -> navController.navigate("aprendiz_detail/$id") },
                bottomBar = { bottomBar() }
            ) }
            composable(
                route = APRENDIZ_NEW_PROJECT,
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("retorno") { type = NavType.StringType; defaultValue = "aprendiz" },
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val retorno = backStackEntry.arguments?.getString("retorno") ?: "aprendiz"
                NewProjectScreen(
                    projectId = projectId,
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) },
                    onSaved = { id ->
                        // El admin vuelve a SU detalle; el aprendiz al suyo.
                        val destino = if (retorno == "admin") {
                            ADMIN_PROJECT_DETAIL.replace("{projectId}", id.toString())
                        } else {
                            "aprendiz_detail/$id"
                        }
                        navController.navigate(destino) {
                            popUpTo(APRENDIZ_NEW_PROJECT) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = APRENDIZ_DETAIL,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                ProjectDetailScreen(
                    projectId = projectId, 
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) }
                )
            }
            composable(
                route = APRENDIZ_SIMILARITY,
                arguments = listOf(navArgument("similarityId") { type = NavType.StringType })
            ) { backStackEntry ->
                val similarityId = backStackEntry.arguments?.getString("similarityId") ?: ""
                SimilarityDetailScreen(
                    similarityId = similarityId,
                    detalleProyectoRoute = APRENDIZ_DETAIL,
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) },
                )
            }
            composable(
                route = APRENDIZ_SIMILITUDES,
                arguments = listOf(
                    navArgument("proyectoId") { type = NavType.IntType; defaultValue = 0 },
                )
            ) { backStackEntry ->
                val proyectoId = backStackEntry.arguments?.getInt("proyectoId") ?: 0
                SimilitudesScreen(
                    proyectoId = proyectoId.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) },
                )
            }
            composable(APRENDIZ_PROFILE) { ProfileScreen(onBack = { navController.popBackStack() }, onNavigate = { route -> navController.navigateTo(route) }, bottomBar = { bottomBar() }) }
            composable(APRENDIZ_ALERTS) { AlertsScreen(
                onNavigate = { route -> navController.navigateTo(route) },
                onBack = { navController.popBackStack() },
                profileRoute = APRENDIZ_PROFILE,
                similitudesRoute = APRENDIZ_SIMILITUDES,
                detailRoute = "aprendiz_detail/{id}",
                fichaRoute = APRENDIZ_FICHA_DETAIL,
                bottomBar = { bottomBar() },
            ) }
            composable(APRENDIZ_FICHA_DETAIL) { FichaDetailScreen(onBack = { navController.popBackStack() }, onNavigate = { route -> navController.navigateTo(route) }) }
            composable(APRENDIZ_JOIN_FICHA) { UnirseFichaAprendizScreen(onBack = { navController.popBackStack() }, onJoined = { navController.popBackStack() }) }
            composable(
                route = APRENDIZ_COMPANERO_DETAIL,
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                DetalleCompaneroScreen(
                    userId = userId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    fun NavGraphBuilder.instructorGraph(navController: NavHostController) {
        val bottomBar = @Composable {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            SenaBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { route -> 
                    navController.navigate(route) {
                        popUpTo(INSTRUCTOR_DASHBOARD) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                items = instructorTabs
            )
        }

        navigation(startDestination = INSTRUCTOR_DASHBOARD, route = INSTRUCTOR_GRAPH) {
            composable(INSTRUCTOR_DASHBOARD) { InstructorDashboardScreen(onNavigate = { route -> navController.navigateTo(route) }, bottomBar = { bottomBar() }) }
            composable(INSTRUCTOR_FICHAS) { FichaDirectoryScreen(
                onBack = { navController.popBackStack() },
                onCreateFicha = { navController.navigate(INSTRUCTOR_CREAR_FICHA_BASE) },
                onNavigate = { route -> navController.navigateTo(route) }
            ) }
            composable(
                route = INSTRUCTOR_CREAR_FICHA,
                arguments = listOf(
                    navArgument("fichaId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val fichaId = backStackEntry.arguments?.getString("fichaId") ?: ""
                CrearFichaScreen(
                    fichaId = fichaId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(INSTRUCTOR_REVISION) { RevisionPropuestasScreen(
                onBack = { navController.popBackStack() },
                onProjectDetail = { id -> navController.navigate("instructor_detail/$id") },
                bottomBar = { bottomBar() }
            ) }
            composable(
                route = INSTRUCTOR_DETAIL,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                ProjectDetailScreen(
                    projectId = projectId,
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) },
                    editarRoute = INSTRUCTOR_DETAIL,
                    similitudesRoute = INSTRUCTOR_SIMILITUDES,
                    similarityRoute = INSTRUCTOR_SIMILARITY_DETAIL,
                )
            }
            composable(INSTRUCTOR_PROFILE) { InstructorProfileScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigateTo(route) },
                bottomBar = { bottomBar() }
            ) }
            composable(
                route = INSTRUCTOR_SIMILARITY_DETAIL,
                arguments = listOf(navArgument("similarityId") { type = NavType.StringType })
            ) { backStackEntry ->
                val similarityId = backStackEntry.arguments?.getString("similarityId") ?: ""
                SimilarityDetailScreen(
                    similarityId = similarityId,
                    detalleProyectoRoute = INSTRUCTOR_DETAIL,
                    similarityRoute = INSTRUCTOR_SIMILARITY_DETAIL,
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) },
                )
            }
            composable(
                route = INSTRUCTOR_SIMILITUDES,
                arguments = listOf(
                    navArgument("proyectoId") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { backStackEntry ->
                val proyectoId = backStackEntry.arguments?.getInt("proyectoId") ?: 0
                SimilitudesInstructorScreen(
                    proyectoId = proyectoId.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) },
                )
            }
            composable(INSTRUCTOR_MANAGE_FICHAS) { ManageFichasScreen(
                onBack = { navController.popBackStack() },
                onViewDetail = { fichaId -> navController.navigate(INSTRUCTOR_FICHA_DETAIL.replace("{fichaId}", fichaId)) },
                onViewDirectory = { navController.navigate(INSTRUCTOR_FICHAS) },
                onCreateFicha = { navController.navigate(INSTRUCTOR_CREAR_FICHA_BASE) },
                onNavigate = { route -> navController.navigateTo(route) },
                bottomBar = { bottomBar() }
            ) }
            composable(INSTRUCTOR_ALERTS) { AlertsScreen(
                onNavigate = { route -> navController.navigateTo(route) },
                onBack = { navController.popBackStack() },
                profileRoute = INSTRUCTOR_PROFILE,
                similitudesRoute = INSTRUCTOR_SIMILITUDES,
                detailRoute = "instructor_detail/{id}",
                fichaRoute = INSTRUCTOR_FICHA_DETAIL,
            ) }
            composable(
                route = INSTRUCTOR_FICHA_DETAIL,
                arguments = listOf(navArgument("fichaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val fichaId = backStackEntry.arguments?.getString("fichaId") ?: ""
                DetalleFichaInstructorScreen(
                    fichaId = fichaId,
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) }
                )
            }
        }
    }

    fun NavGraphBuilder.adminGraph(navController: NavHostController) {
        val bottomBar = @Composable {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            SenaBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { route -> 
                    navController.navigate(route) {
                        popUpTo(ADMIN_DASHBOARD) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                items = adminTabs
            )
        }

        navigation(startDestination = ADMIN_DASHBOARD, route = ADMIN_GRAPH) {
            composable(ADMIN_DASHBOARD) { AdminDashboardScreen(onNavigate = { route -> navController.navigateTo(route) }, bottomBar = { bottomBar() }) }
            composable(ADMIN_USERS) { UserManagementScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigateTo(route) },
                bottomBar = { bottomBar() }
            ) }
            composable(ADMIN_BUGS) { BugReportsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigateTo(route) }
            ) }
            composable(ADMIN_PROJECTS) { AdminProjectsScreen(
                onBack = { navController.popBackStack() },
                onProjectDetail = { id -> navController.navigate(ADMIN_PROJECT_DETAIL.replace("{projectId}", id.toString())) },
            ) }
            composable(
                route = ADMIN_PROJECT_DETAIL,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                ProjectDetailScreen(
                    projectId = projectId,
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) },
                    // Intervención admin: editar contenido reutiliza el formulario;
                    // al guardar vuelve al detalle del admin (retorno=admin).
                    editarRoute = "aprendiz_new_project/{projectId}?retorno=admin",
                    similitudesRoute = ADMIN_SIMILARITY_LIST_FILTRO,
                    similarityRoute = ADMIN_SIMILARITY_DETAIL,
                )
            }
            composable(
                route = ADMIN_SIMILARITY_LIST_FILTRO,
                arguments = listOf(
                    navArgument("proyectoId") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { backStackEntry ->
                val proyectoId = backStackEntry.arguments?.getInt("proyectoId") ?: 0
                AdminSimilarityListScreen(
                    proyectoId = proyectoId.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) },
                    bottomBar = { bottomBar() }
                )
            }
            composable(
                route = ADMIN_SIMILARITY_DETAIL,
                arguments = listOf(
                    navArgument("similarityId") { type = NavType.StringType; defaultValue = "" },
                )
            ) { backStackEntry ->
                val similarityId = backStackEntry.arguments?.getString("similarityId") ?: ""
                AdminSimilarityDetailScreen(
                    similarityId = similarityId,
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) },
                )
            }
            composable(ADMIN_NEW_USER) { NewUserScreen(onBack = { navController.popBackStack() }) }
            composable(
                route = ADMIN_USER_DETAIL,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                UserDetailScreen(
                    userId = userId, 
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigateTo(route) }
                )
            }
            composable(ADMIN_PROFILE) { AdminProfileScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigateTo(route) },
                bottomBar = { bottomBar() }
            ) }
            composable(
                route = ADMIN_BUG_DETAIL,
                arguments = listOf(navArgument("bugId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bugId = backStackEntry.arguments?.getString("bugId") ?: ""
                BugReportDetailScreen(
                    bugId = bugId, 
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ADMIN_ALERTS) { AlertsScreen(
                onNavigate = { route -> navController.navigateTo(route) },
                onBack = { navController.popBackStack() },
                profileRoute = ADMIN_PROFILE,
                similitudesRoute = ADMIN_SIMILARITY_LIST_FILTRO,
                detailRoute = "admin/project/{id}",
                fichaRoute = ADMIN_FICHAS,
                reporteRoute = ADMIN_BUG_DETAIL,
            ) }
            composable(ADMIN_FICHAS) { AdminFichasScreen(
                onBack = { navController.popBackStack() },
                onCreateFicha = { navController.navigate(INSTRUCTOR_CREAR_FICHA_BASE) },
                onNavigate = { route -> navController.navigateTo(route) },
            ) }
            composable(ADMIN_CATALOGOS) { AdminCatalogosScreen(onBack = { navController.popBackStack() }) }
            composable(ADMIN_BITACORA) { AdminBitacoraScreen(onBack = { navController.popBackStack() }) }
            composable(ADMIN_MOTOR) { AdminMotorScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun NavHostController.navigateTo(route: String) {
    navigate(route)
}
