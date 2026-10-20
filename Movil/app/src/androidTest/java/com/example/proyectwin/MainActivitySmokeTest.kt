package com.example.proyectwin

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke de arranque (M6): la actividad principal llega a RESUMED y Compose
 * muestra contenido. No asume sesión, rol ni backend: el primer pantallazo
 * depende de la sesión local del dispositivo (splash, login o inicio del rol),
 * por lo que solo se comprueba que exista el árbol de Compose con algún texto.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_arranca_yMuestraContenido() {
        assertTrue(
            "MainActivity no llegó a RESUMED",
            rule.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
        )

        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
