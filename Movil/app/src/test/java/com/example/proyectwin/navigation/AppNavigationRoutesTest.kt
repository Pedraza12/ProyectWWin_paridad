package com.example.proyectwin.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rutas construidas y patrones con argumentos (Fase 4). */
class AppNavigationRoutesTest {

    @Test
    fun rutaNuevoProyecto_porDefecto_vuelveAlAprendiz() {
        assertEquals("aprendiz_new_project/?retorno=aprendiz", AppNavigation.rutaNuevoProyecto())
    }

    @Test
    fun rutaNuevoProyecto_edicionConRetornoAdmin() {
        assertEquals(
            "aprendiz_new_project/5?retorno=admin",
            AppNavigation.rutaNuevoProyecto("5", "admin"),
        )
    }

    @Test
    fun patronesDeSimilitudes_conservanElFiltroPorProyecto() {
        assertTrue(AppNavigation.APRENDIZ_SIMILITUDES.contains("{proyectoId}"))
        assertTrue(AppNavigation.INSTRUCTOR_SIMILITUDES.contains("{proyectoId}"))
        assertTrue(AppNavigation.ADMIN_SIMILARITY_LIST_FILTRO.contains("{proyectoId}"))
    }

    @Test
    fun edicionDePropuesta_declaraElRetorno() {
        assertTrue(AppNavigation.APRENDIZ_NEW_PROJECT.contains("{retorno}"))
    }
}
