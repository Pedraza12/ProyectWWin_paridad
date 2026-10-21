package com.example.proyectwin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.view.WindowCompat
import com.example.proyectwin.data.api.SessionEvents
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.navigation.AppNavigation.AUTH_GRAPH
import com.example.proyectwin.navigation.AppNavigation.authGraph
import com.example.proyectwin.navigation.AppNavigation.aprendizGraph
import com.example.proyectwin.navigation.AppNavigation.instructorGraph
import com.example.proyectwin.navigation.AppNavigation.adminGraph
import com.example.proyectwin.navigation.NavIntent
import com.example.proyectwin.navigation.NavIntents
import com.example.proyectwin.ui.components.AppUpdateHost
import com.example.proyectwin.ui.screens.AppUpdatesScreen
import com.example.proyectwin.ui.screens.EditProfileScreen
import com.example.proyectwin.ui.screens.ReportIssueScreen
import com.example.proyectwin.ui.screens.SplashScreen
import com.example.proyectwin.ui.screens.auth.CambioObligatorioScreen
import com.example.proyectwin.ui.screens.auth.ChangeEmailScreen
import com.example.proyectwin.ui.theme.LocalThemeIsDark
import com.example.proyectwin.ui.theme.ProyecTwinTheme
import com.example.proyectwin.ui.viewmodel.AppUpdateViewModel
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProyecTwinTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = hiltViewModel()
                val uiState by authViewModel.uiState.collectAsState()

                // Verificación de actualización (una vez por proceso) y aviso.
                val appUpdateViewModel: AppUpdateViewModel = hiltViewModel()
                val estadoActualizacion by appUpdateViewModel.uiState.collectAsState()

                val isDark = LocalThemeIsDark.current.value
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window
                    if (window != null) {
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                    }
                }

                LaunchedEffect(Unit) {
                    SessionEvents.expired.collect {
                        // Solo con una sesión activa: si el evento viene del
                        // login (p. ej. 403 de cuenta suspendida), no debe
                        // borrar el mensaje de error que ve el usuario.
                        if (authViewModel.uiState.value is AuthUiState.LoggedIn) {
                            authViewModel.logout()
                        }
                    }
                }

                // El backend bloquea la API (403) hasta cambiar la temporal.
                LaunchedEffect(Unit) {
                    SessionEvents.mustChangePassword.collect {
                        authViewModel.markMustChangePassword()
                    }
                }

                // Destino inicial real de cada rol (para comparar con la ruta actual).
                fun destinoDeRol(rol: String): String = when (rol) {
                    "instructor" -> AppNavigation.INSTRUCTOR_DASHBOARD
                    "admin" -> AppNavigation.ADMIN_DASHBOARD
                    else -> AppNavigation.APRENDIZ_DASHBOARD
                }

                fun grafoDeRol(rol: String): String = when (rol) {
                    "instructor" -> AppNavigation.INSTRUCTOR_GRAPH
                    "admin" -> AppNavigation.ADMIN_GRAPH
                    else -> AppNavigation.APRENDIZ_GRAPH
                }

                fun rutaAlertas(rol: String): String = when (rol) {
                    "instructor" -> AppNavigation.INSTRUCTOR_ALERTS
                    "admin" -> AppNavigation.ADMIN_ALERTS
                    else -> AppNavigation.APRENDIZ_ALERTS
                }

                fun rutaPerfil(rol: String): String = when (rol) {
                    "instructor" -> AppNavigation.INSTRUCTOR_PROFILE
                    "admin" -> AppNavigation.ADMIN_PROFILE
                    else -> AppNavigation.APRENDIZ_PROFILE
                }

                // --- Splash de apertura ---
                // Se muestra mientras la sesión local se hidrata, con un mínimo
                // corto para apreciar la animación del logo. Nunca atrapa al
                // usuario: en cuanto el estado deja de ser Loading, continúa al
                // destino que corresponda (login o el inicio de su rol).
                var splashMinimoListo by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(1100)
                    splashMinimoListo = true
                }
                val arranqueListo = splashMinimoListo && uiState !is AuthUiState.Loading

                val startDestination = when (val estado = uiState) {
                    is AuthUiState.LoggedIn ->
                        if (estado.user.mustChangePassword) AppNavigation.CAMBIO_OBLIGATORIO
                        else grafoDeRol(estado.user.role)
                    else -> AUTH_GRAPH
                }

                // Clave del "flujo" de sesión: solo cambia cuando corresponde ir
                // a otro destino raíz (login, cambio obligatorio, inicio del
                // rol). Guardar nombre/foto u otras actualizaciones del usuario
                // NO cambia la clave: antes cada guardado expulsaba al usuario a
                // Inicio (p. ej. al cambiar la foto desde el perfil).
                val claveNavegacion: String? = when (val estado = uiState) {
                    is AuthUiState.LoggedOut -> "auth"
                    is AuthUiState.LoggedIn ->
                        if (estado.user.mustChangePassword) "cambio" else "rol:${estado.user.role}"
                    else -> null
                }
                var claveAplicada by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(claveNavegacion, arranqueListo) {
                    val clave = claveNavegacion ?: return@LaunchedEffect
                    if (!arranqueListo || clave == claveAplicada) return@LaunchedEffect
                    claveAplicada = clave
                    when (clave) {
                        "auth" -> {
                            if (navController.currentDestination?.route != AppNavigation.LOGIN) {
                                navController.navigate(AppNavigation.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                        "cambio" -> {
                            if (navController.currentDestination?.route != AppNavigation.CAMBIO_OBLIGATORIO) {
                                navController.navigate(AppNavigation.CAMBIO_OBLIGATORIO) {
                                    launchSingleTop = true
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                        else -> {
                            val rol = clave.removePrefix("rol:")
                            val destino = destinoDeRol(rol)
                            if (navController.currentDestination?.route != destino) {
                                navController.navigate(destino) {
                                    launchSingleTop = true
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                    }
                }

                // Campana/avatar del top bar en pantallas que no pasan callback:
                // se traduce aquí a la ruta del rol autenticado.
                LaunchedEffect(arranqueListo) {
                    if (!arranqueListo) return@LaunchedEffect
                    NavIntents.destinos.collect { intent ->
                        val rol = (authViewModel.uiState.value as? AuthUiState.LoggedIn)?.user?.role
                            ?: return@collect
                        val ruta = when (intent) {
                            NavIntent.NOTIFICACIONES -> rutaAlertas(rol)
                            NavIntent.PERFIL -> rutaPerfil(rol)
                        }
                        if (navController.currentDestination?.route != ruta) {
                            navController.navigate(ruta) { launchSingleTop = true }
                        }
                    }
                }

                // "Sesión activa" = logueado y sin cambio de contraseña pendiente
                // (ese flujo ya es bloqueante por sí mismo).
                val estadoSesion = uiState as? AuthUiState.LoggedIn
                val sesionActiva = estadoSesion != null && !estadoSesion.user.mustChangePassword
                LaunchedEffect(sesionActiva) {
                    if (sesionActiva) appUpdateViewModel.verificar(BuildConfig.VERSION_NAME)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!arranqueListo) {
                        SplashScreen()
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                        authGraph(navController)
                        aprendizGraph(navController)
                        instructorGraph(navController)
                        adminGraph(navController)

                        // Pantallas compartidas por los tres roles: una sola ruta.
                        composable(AppNavigation.EDIT_PROFILE) {
                            EditProfileScreen(
                                onBack = { navController.popBackStack() },
                                onNavigate = { route -> navController.navigate(route) },
                            )
                        }
                        composable(AppNavigation.CHANGE_EMAIL) {
                            ChangeEmailScreen(onBack = { navController.popBackStack() })
                        }
                        composable(AppNavigation.REPORT_ISSUE) {
                            ReportIssueScreen(
                                onBack = { navController.popBackStack() },
                                onNavigate = { route -> navController.navigate(route) },
                            )
                        }
                        composable(AppNavigation.APP_UPDATES) {
                            AppUpdatesScreen(onBack = { navController.popBackStack() })
                        }

                        composable(AppNavigation.CAMBIO_OBLIGATORIO) {
                            CambioObligatorioScreen(
                                onCompleted = { rol ->
                                    val destino = destinoDeRol(rol)
                                    if (navController.currentDestination?.route != destino) {
                                        navController.navigate(destino) {
                                            launchSingleTop = true
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                    // Banner de actualización: flota sobre la pantalla actual sin
                    // bloquear la navegación (solo con sesión activa).
                    if (arranqueListo && sesionActiva) {
                        AppUpdateHost(
                            estado = estadoActualizacion,
                            onActualizar = {
                                // Abre el centro de actualizaciones (descarga e
                                // instalación verificadas), no un navegador externo.
                                appUpdateViewModel.actualizacionAtendida()
                                if (navController.currentDestination?.route != AppNavigation.APP_UPDATES) {
                                    navController.navigate(AppNavigation.APP_UPDATES) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onMasTarde = { appUpdateViewModel.masTarde() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
                        )
                    }
                }
            }
        }
    }
}
