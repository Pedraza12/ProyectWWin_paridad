// Playbook del agente ADMIN: usuarios, fichas, motor, propuestas, reportes,
// alertas y bitácora.
import { confirmarBorrado } from '../../helpers'

export async function admin(h) {
  const { page } = h
  const nombreFicha = `Ficha Admin Agente ${h.sufijo}`
  let fichaId = null

  await h.paso('crear usuario instructor', async () => {
    await h.ir('/admin/usuarios')
    await h.auditar()
    h.usar('Nuevo Usuario')
    await page.getByRole('button', { name: /Nuevo Usuario/i }).click()

    const form = page.locator('form')
    await form.getByPlaceholder('Ej. María José').fill('Instructor Agente')
    await form.getByPlaceholder('Ej. González Ruiz').fill(h.sufijo)
    await form.locator('select[name="tipoDocumento"]').selectOption('CC')
    await form.getByPlaceholder('Ej. 1234567890').fill(String(h.sufijo))
    await form.getByPlaceholder('Correo personal').fill(`agente.${h.sufijo}@correo.com`)
    await form.locator('select[name="rol"]').selectOption('instructor')

    h.usar('Crear usuario')
    const creado = await h.capturar('POST', '/v1/general-users', () =>
      form.getByRole('button', { name: /Crear usuario/i }).click()
    )
    if (!creado?.usuario?.id) throw new Error('el usuario no se creó')
    await h.esperar('creado.', 10000)
  })

  await h.paso('editar un usuario (transferir de ficha)', async () => {
    await h.ir('/admin/detalle-usuario/4') // Ana Martínez: ficha 1
    h.usar('Editar')
    await page.getByRole('button', { name: /^Editar$/i }).click()
    await page.locator('select[name="fichaId"]').selectOption('2')
    h.usar('Guardar cambios')
    await page.getByRole('button', { name: /Guardar cambios/i }).click()
    await h.esperar('Usuario actualizado correctamente.', 10000)
  })

  await h.paso('restablecer la contraseña de un usuario', async () => {
    await h.ir('/admin/detalle-usuario/4')
    h.usar('Restablecer contraseña')
    await page.getByRole('button', { name: /Restablecer contraseña/i }).click()
    await page.getByRole('button', { name: /Sí, restablecer/i }).click()
    await page.getByText(/[A-Z0-9]{4}-[A-Z0-9]{4}/).first().waitFor({ timeout: 10000 })
  })

  await h.paso('suspender y reactivar un usuario', async () => {
    await h.ir('/admin/usuarios')
    await page.getByPlaceholder(/Nombre, documento, usuario o correo/i).fill('juan.perez@soy.sena.edu.co')
    const fila = page.locator('tr', { hasText: 'juan.perez@soy.sena.edu.co' })
    h.usar('Suspender')
    await fila.getByRole('button', { name: /Suspender/i }).click()
    await page.getByRole('button', { name: /Sí, suspender/i }).click()
    await fila.getByText('Suspendido').waitFor({ timeout: 10000 })
    h.usar('Activar')
    await fila.getByRole('button', { name: /Activar/i }).click()
    await fila.getByText('Activo').waitFor({ timeout: 10000 })
  })

  await h.paso('crear, archivar y borrar una ficha', async () => {
    await h.ir('/admin/fichas')
    h.usar('Crear Ficha')
    await page.getByRole('button', { name: /Crear Ficha/i }).click()

    await page.locator('form select[name="red"]').selectOption('Informática, Diseño y Desarrollo de Software')
    await page.locator('form select[name="programa"]').selectOption('ADSO')
    await page.locator('form select[name="instructorId"]').selectOption({ index: 1 })
    await page.getByPlaceholder('Ej. Análisis y Desarrollo 2718').fill(nombreFicha)
    await page.getByPlaceholder('Ej. 3142101').fill('8' + h.sufijo.slice(0, 4))

    h.usar('Crear ficha')
    const creada = await h.capturar('POST', '/v1/class-groups', () =>
      page.locator('form').getByRole('button', { name: /^Crear ficha$/i }).click()
    )
    fichaId = creada?.id ?? null
    await h.esperar('Ficha creada correctamente.', 10000)

    if (!fichaId) throw new Error('no se obtuvo el id de la ficha creada')
    await h.ir(`/admin/detalle-ficha/${fichaId}`)

    h.usar('Editar ficha')
    await page.getByRole('button', { name: /^Editar$/i }).first().click()
    await page.locator('form select[name="estado"]').selectOption('finalizado')
    h.usar('Guardar cambios')
    await page.locator('form').getByRole('button', { name: /Guardar cambios/i }).click()
    await h.esperar('Ficha actualizada correctamente.', 10000)

    h.usar('Eliminar ficha')
    await page.getByRole('button', { name: /Eliminar ficha/i }).click()
    await confirmarBorrado(page, 'ELIMINAR')
    await page.waitForURL('**/admin/fichas', { timeout: 15000 })
  })

  await h.paso('ajustar el motor de similitudes', async () => {
    await h.ir('/admin/config-similitud')
    await page.locator('input[type="number"]').first().fill('55')
    await page.locator('input[type="number"]').nth(1).fill('9')
    h.usar('Guardar parámetros')
    await page.getByRole('button', { name: /Guardar parámetros/i }).click()
    await page.getByText(/Motor actualizado/i).waitFor({ timeout: 10000 })
  })

  await h.paso('moderar una propuesta (estado, contenido y observación)', async () => {
    await h.ir('/admin/detalle-proyecto/4')

    const sel = page.getByLabel('Cambiar estado')
    const actual = await sel.inputValue()
    await sel.selectOption(actual === 'aprobado' ? 'rechazado' : 'aprobado')
    h.usar('Guardar')
    await page.getByRole('button', { name: /^Guardar$/i }).click()
    await h.esperar('Estado actualizado correctamente.', 10000)

    // Contrato real: editar el contenido de una APROBADA la devuelve a revisión
    // (intervención administrativa) y así lo anuncia; en cualquier otro estado
    // el aviso es el simple. La expectativa se deriva del estado ya persistido.
    const eraAprobada = (await sel.inputValue()) === 'aprobado'

    h.usar('Editar')
    await page.getByRole('button', { name: /^Editar$/i }).click()
    await page.getByLabel('Título').fill(`Propuesta moderada ${h.sufijo}`)
    h.usar('Guardar contenido')
    // La verificación del retorno a revisión se hace contra la RESPUESTA del
    // servidor (fuente de verdad): el selector de estado conserva el valor
    // elegido por el moderador y no refleja la transición automática.
    const guardada = await h.capturar('PUT', '/v1/projects/4', () =>
      page.getByRole('button', { name: /Guardar contenido/i }).click()
    )
    await h.esperar(
      eraAprobada
        ? 'Contenido actualizado. La propuesta volvió a En revisión'
        : 'Contenido actualizado correctamente.',
      10000
    )
    if (eraAprobada && guardada?.estado !== 'pendiente') {
      throw new Error(
        `la intervención no devolvió la propuesta a revisión (estado persistido: ${guardada?.estado})`
      )
    }

    await page.getByLabel('Observación sobre esta propuesta').fill(`Observación admin ${h.sufijo}`)
    h.usar('Agregar observación')
    await page.getByRole('button', { name: /Agregar observación/i }).click()
    await h.esperar(`Observación admin ${h.sufijo}`)

    const article = page.locator('article', { hasText: `Observación admin ${h.sufijo}` }).first()
    h.usar('Eliminar observación')
    await article.getByRole('button', { name: /Eliminar observación/i }).click()
    await confirmarBorrado(page)
  })

  await h.paso('cambiar el estado de una solicitud de soporte', async () => {
    await h.ir('/admin/reportes-fallas')
    await page.locator('tbody tr').first().getByRole('link', { name: /Ver/i }).click()
    await page.waitForURL('**/admin/detalle-reporte/**')
    const sel = page.getByLabel('Cambiar estado')
    const actual = await sel.inputValue()
    await sel.selectOption(actual === 'resuelto' ? 'en_revision' : 'resuelto')
    h.usar('Guardar')
    await page.getByRole('button', { name: /^Guardar$/ }).click()
    await h.esperar('se actualizó correctamente', 10000)
  })

  await h.paso('marcar alertas como leídas', async () => {
    await h.ir('/admin/notificaciones')
    const btn = page.getByRole('button', { name: /Marcar todas como leídas/i })
    await btn.waitFor({ timeout: 10000 })
    if (await btn.isEnabled()) await btn.click()
  })

  await h.paso('ver la bitácora', async () => {
    await h.ir('/admin/bitacora')
    await h.auditar()
    await page.locator('tbody tr').first().waitFor({ timeout: 10000 })
  })

  await h.paso('borrar un usuario sin historial', async () => {
    await h.ir('/admin/detalle-usuario/11') // Patricia Morales: sin propuestas ni equipo
    h.usar('Eliminar')
    await page.getByRole('button', { name: /^Eliminar$/i }).click()
    await confirmarBorrado(page, 'ELIMINAR')
    await page.waitForURL('**/admin/usuarios', { timeout: 15000 })
  })

  await h.paso('eliminar una propuesta rechazada', async () => {
    await h.ir('/admin/detalle-proyecto/8')
    h.usar('Eliminar')
    await page.getByRole('button', { name: /^Eliminar$/i }).first().click()
    await confirmarBorrado(page)
    await page.waitForURL('**/admin/proyectos', { timeout: 15000 })
  })
}
