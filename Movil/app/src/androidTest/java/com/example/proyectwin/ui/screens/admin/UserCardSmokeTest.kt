package com.example.proyectwin.ui.screens.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.proyectwin.ui.theme.ProyecTwinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests instrumentados de [UserCard] (M6): modo normal (acciones
 * visibles) y modo selección (checkbox visible, acciones ocultas y alternado
 * de la selección con checkbox o con la propia tarjeta). El estado de
 * selección es local al test: no depende de sesión, red ni datos reales.
 */
@RunWith(AndroidJUnit4::class)
class UserCardSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    private val usuario = UserItem(
        id = 1,
        name = "Ana Prueba",
        document = "1234567890",
        email = "ana.prueba@example.com",
        role = "aprendiz",
        status = "activo",
    )

    @Test
    fun userCard_normal_muestraDatosYAccionesSinCheckbox() {
        rule.setContent {
            ProyecTwinTheme(darkTheme = false) {
                UserCard(user = usuario, onEdit = {}, onDelete = {})
            }
        }

        rule.onNodeWithText("Ana Prueba").assertIsDisplayed()
        rule.onNodeWithText("ana.prueba@example.com").assertIsDisplayed()
        rule.onNodeWithText("ACTIVO").assertIsDisplayed()
        rule.onNodeWithContentDescription("Editar").assertIsDisplayed()
        rule.onNodeWithContentDescription("Eliminar").assertIsDisplayed()
        rule.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test
    fun userCard_modoSeleccion_alternaConCheckboxYConLaTarjeta() {
        var seleccionado by mutableStateOf(false)
        rule.setContent {
            ProyecTwinTheme(darkTheme = false) {
                UserCard(
                    user = usuario,
                    onEdit = {},
                    onDelete = {},
                    selectionMode = true,
                    selected = seleccionado,
                    onToggleSelection = { seleccionado = !seleccionado },
                )
            }
        }

        // En modo selección no se ofrecen Editar/Eliminar y el checkbox aparece apagado.
        rule.onNodeWithContentDescription("Editar").assertDoesNotExist()
        rule.onNodeWithContentDescription("Eliminar").assertDoesNotExist()
        rule.onNode(isToggleable()).assertIsOff()

        // Tocar el checkbox selecciona la tarjeta...
        rule.onNode(isToggleable()).performClick()
        rule.onNode(isToggleable()).assertIsOn()

        // ...y tocar la tarjeta vuelve a alternar la selección.
        rule.onNodeWithText("Ana Prueba").performClick()
        rule.onNode(isToggleable()).assertIsOff()
    }
}
