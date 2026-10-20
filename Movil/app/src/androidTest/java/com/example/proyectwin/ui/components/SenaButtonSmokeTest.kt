package com.example.proyectwin.ui.components

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.proyectwin.ui.theme.ProyecTwinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests instrumentados de [SenaButton] (M6): los tres estados que
 * gobiernan la interacción (habilitado, deshabilitado y cargando) sobre un
 * composable puro. Sin sesión, red ni datos reales: resultado determinista en
 * cualquier dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class SenaButtonSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun montarBoton(
        enabled: Boolean = true,
        isLoading: Boolean = false,
        onClick: () -> Unit = {},
    ) {
        rule.setContent {
            ProyecTwinTheme(darkTheme = false) {
                SenaButton(
                    text = "Guardar",
                    onClick = onClick,
                    enabled = enabled,
                    isLoading = isLoading,
                )
            }
        }
    }

    @Test
    fun senaButton_habilitado_muestraTextoYNotificaElClick() {
        var clicks = 0
        montarBoton(onClick = { clicks++ })

        rule.onNodeWithText("Guardar").assertIsDisplayed()
        rule.onNode(hasClickAction()).assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun senaButton_deshabilitado_estaNoHabilitadoYNoNotificaClick() {
        var clicks = 0
        montarBoton(enabled = false, onClick = { clicks++ })

        rule.onNodeWithText("Guardar").assertIsDisplayed()
        rule.onNode(hasClickAction()).assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun senaButton_cargando_ocultaTextoYNoNotificaClick() {
        var clicks = 0
        montarBoton(isLoading = true, onClick = { clicks++ })

        rule.onNodeWithText("Guardar").assertDoesNotExist()
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        rule.onNode(hasClickAction()).assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }
}
