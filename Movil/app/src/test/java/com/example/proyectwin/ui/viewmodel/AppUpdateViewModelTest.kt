package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.local.UpdatePreferences
import com.example.proyectwin.data.model.AppVersionInfo
import com.example.proyectwin.domain.repository.AppUpdatesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Política del aviso de actualización: detección, posposición (24 h), límite
 * de consultas (una por proceso, 6 h entre procesos), modo obligatorio y
 * tolerancia a fallos o datos inválidos (la app nunca se rompe).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<AppUpdatesRepository>()
    private val prefs = mockk<UpdatePreferences>()

    // Estado en memoria que actúa de DataStore para los tests.
    private var ultimaConsulta: Long? = null
    private var versionPospuesta: String? = null
    private var pospuestaEn: Long = 0L

    private val ahora = 1_000_000_000L

    private fun info(
        version: String = "1.1.0",
        minima: String = "1.0.0",
        obligatoria: Boolean = false,
        url: String? = "https://example.test/apk/proyecttwin.apk",
        notas: String? = "Mejoras y ajustes",
    ) = AppVersionInfo(
        versionPublicada = version,
        versionMinima = minima,
        obligatoria = obligatoria,
        urlDescarga = url,
        notas = notas,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { prefs.ultimaConsulta() } answers { ultimaConsulta }
        coEvery { prefs.marcarConsulta(any()) } answers { ultimaConsulta = firstArg() }
        coEvery { prefs.pospuesta() } answers { versionPospuesta to pospuestaEn }
        coEvery { prefs.posponer(any(), any()) } answers {
            versionPospuesta = firstArg()
            pospuestaEn = secondArg()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AppUpdateViewModel(repo, prefs)

    // 1. La aplicación está actualizada: no aparece ningún aviso.
    @Test
    fun `al dia no muestra aviso`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns Result.success(info(version = "1.0.0"))

        val vm = viewModel()
        vm.verificar("1.0.0", ahora)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.mostrar)
    }

    // 2. Hay una versión nueva: aparece el aviso (opcional por defecto).
    @Test
    fun `version nueva muestra el aviso opcional`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns Result.success(info(version = "1.1.0"))

        val vm = viewModel()
        vm.verificar("1.0", ahora)
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertTrue(estado.mostrar)
        assertFalse(estado.obligatoria)
        assertEquals("1.1.0", estado.versionNueva)
        assertEquals("https://example.test/apk/proyecttwin.apk", estado.urlDescarga)
    }

    // 9. Detección numérica: 1.10.0 supera a 1.9.0 aunque "1.9" gane alfabéticamente.
    @Test
    fun `comparacion numerica detecta 1_10 sobre 1_9`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns Result.success(info(version = "1.10.0"))

        val vm = viewModel()
        vm.verificar("1.9.0", ahora)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.mostrar)
    }

    // 3/4. "Más tarde" oculta y no insiste con esa versión durante 24 h.
    @Test
    fun `mas tarde silencia esa version por 24 horas`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns Result.success(info(version = "1.1.0"))

        val vm = viewModel()
        vm.verificar("1.0", ahora)
        advanceUntilIdle()
        vm.masTarde(ahora)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.mostrar)
        assertEquals("1.1.0", versionPospuesta)

        // Nuevo proceso 1 h después: sigue silenciada.
        ultimaConsulta = ahora
        val vm2 = viewModel()
        vm2.verificar("1.0", ahora + 3_600_000L)
        advanceUntilIdle()
        assertFalse(vm2.uiState.value.mostrar)

        // Nuevo proceso 25 h después: vuelve a avisar.
        val vm3 = viewModel()
        vm3.verificar("1.0", ahora + 25 * 3_600_000L)
        advanceUntilIdle()
        assertTrue(vm3.uiState.value.mostrar)
    }

    // 5. Una versión más nueva que la pospuesta vuelve a avisar de inmediato.
    @Test
    fun `version mas nueva que la pospuesta vuelve a avisar`() = runTest(dispatcher) {
        versionPospuesta = "1.1.0"
        pospuestaEn = ahora - 60_000L
        coEvery { repo.versionPublicada() } returns Result.success(info(version = "1.2.0"))

        val vm = viewModel()
        vm.verificar("1.0", ahora)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.mostrar)
        assertEquals("1.2.0", vm.uiState.value.versionNueva)
    }

    // 6. Sin conexión: silencio total y reintento futuro (consulta no marcada).
    @Test
    fun `error de red es silencioso y no marca la consulta`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns Result.failure(IOException("sin red"))

        val vm = viewModel()
        vm.verificar("1.0", ahora)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.mostrar)
        assertEquals(null, ultimaConsulta)
    }

    // 7. El servidor devuelve error HTTP: mismo comportamiento silencioso.
    @Test
    fun `error http es silencioso`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns Result.failure(RuntimeException("500"))

        val vm = viewModel()
        vm.verificar("1.0", ahora)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.mostrar)
    }

    // 8. Datos inválidos/incompletos se gestionan de forma segura.
    @Test
    fun `datos invalidos o sin url no generan aviso`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns Result.success(info(version = "abc"))
        val vm1 = viewModel()
        vm1.verificar("1.0", ahora)
        advanceUntilIdle()
        assertFalse(vm1.uiState.value.mostrar)

        coEvery { repo.versionPublicada() } returns Result.success(info(url = null))
        ultimaConsulta = null
        val vm2 = viewModel()
        vm2.verificar("1.0", ahora)
        advanceUntilIdle()
        assertFalse(vm2.uiState.value.mostrar)
    }

    // 10. Obligatoria: solo con política explícita y versión bajo la mínima.
    @Test
    fun `obligatoria cuando la instalada es menor a la minima`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns
            Result.success(info(version = "1.2.0", minima = "1.1.0", obligatoria = true))

        val vm = viewModel()
        vm.verificar("1.0", ahora)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.mostrar)
        assertTrue(vm.uiState.value.obligatoria)
    }

    // 10b. Obligatoria no se pospone: reaparece aunque haya "Más tarde".
    @Test
    fun `obligatoria no se pospone`() = runTest(dispatcher) {
        versionPospuesta = "1.2.0"
        pospuestaEn = ahora - 60_000L
        coEvery { repo.versionPublicada() } returns
            Result.success(info(version = "1.2.0", minima = "1.1.0", obligatoria = true))

        val vm = viewModel()
        vm.verificar("1.0", ahora)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.mostrar)
        assertTrue(vm.uiState.value.obligatoria)
    }

    // 10c. `enforce_minimum` con la instalada por encima de la mínima = opcional.
    @Test
    fun `enforce con instalada al dia es opcional`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns
            Result.success(info(version = "1.2.0", minima = "1.0.0", obligatoria = true))

        val vm = viewModel()
        vm.verificar("1.1.0", ahora)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.mostrar)
        assertFalse(vm.uiState.value.obligatoria)
    }

    // 5b. Una sola consulta de red por proceso.
    @Test
    fun `consulta una sola vez por proceso`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns Result.success(info(version = "1.1.0"))

        val vm = viewModel()
        vm.verificar("1.0", ahora)
        advanceUntilIdle()
        vm.verificar("1.0", ahora + 60_000L)
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.versionPublicada() }
    }

    // 9b. Entre procesos: dentro de las 6 h no se vuelve a consultar la red.
    @Test
    fun `respeta el intervalo minimo entre consultas`() = runTest(dispatcher) {
        ultimaConsulta = ahora - 3_600_000L // hace 1 h
        coEvery { repo.versionPublicada() } returns Result.success(info(version = "1.1.0"))

        val vm = viewModel()
        vm.verificar("1.0", ahora)
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.versionPublicada() }
        assertFalse(vm.uiState.value.mostrar)
    }

    // "Actualizar" en la opcional también silencia 24 h; en la obligatoria
    // solo se oculta en la sesión (no persiste posposición).
    @Test
    fun `actualizacion atendida persiste solo en la opcional`() = runTest(dispatcher) {
        coEvery { repo.versionPublicada() } returns Result.success(info(version = "1.1.0"))
        val vm = viewModel()
        vm.verificar("1.0", ahora)
        advanceUntilIdle()
        vm.actualizacionAtendida(ahora)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.mostrar)
        assertEquals("1.1.0", versionPospuesta)

        // Obligatoria: al atenderla no queda posposición guardada.
        versionPospuesta = null
        ultimaConsulta = null
        coEvery { repo.versionPublicada() } returns
            Result.success(info(version = "1.2.0", minima = "1.1.0", obligatoria = true))
        val vm2 = viewModel()
        vm2.verificar("1.0", ahora)
        advanceUntilIdle()
        vm2.actualizacionAtendida(ahora)
        advanceUntilIdle()
        assertFalse(vm2.uiState.value.mostrar)
        assertEquals(null, versionPospuesta)
    }
}
