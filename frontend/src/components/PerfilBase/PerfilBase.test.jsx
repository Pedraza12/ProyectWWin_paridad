import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import PerfilBase from './PerfilBase'

const mocks = vi.hoisted(() => ({
  perfil: {},
  recargar: vi.fn(),
  actualizar: vi.fn(),
  obtener: vi.fn(),
  cambiarCorreo: vi.fn(),
  resolverFotoPerfil: vi.fn(),
  sincronizarSesion: vi.fn(),
  cambiarMiContrasena: vi.fn(),
}))

vi.mock('../../lib/useApi', () => ({
  useApi: () => ({
    data: mocks.perfil,
    cargando: false,
    error: null,
    recargar: mocks.recargar,
  }),
}))

vi.mock('../../contexts/AuthContext', () => ({
  useAuth: () => ({
    sincronizarSesion: mocks.sincronizarSesion,
    cambiarMiContrasena: mocks.cambiarMiContrasena,
  }),
}))

vi.mock('../../lib/recursos', () => ({
  usuarios: {
    actualizar: mocks.actualizar,
    obtener: mocks.obtener,
    cambiarCorreo: mocks.cambiarCorreo,
  },
}))

vi.mock('../../utils/foto', () => ({
  resolverFotoPerfil: mocks.resolverFotoPerfil,
}))

const PERFIL = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Pérez',
  correo: 'ana@test.local',
  rol: 'aprendiz',
  tipo_documento: 'CC',
  numero_documento: '12345678',
  foto_url: null,
}

function renderPerfil({ perfil = {}, soloLectura = false } = {}) {
  mocks.perfil = { ...PERFIL, ...perfil }

  return render(
    <MemoryRouter>
      <PerfilBase
        user={{ id: PERFIL.id, nombre: PERFIL.nombre, correo: PERFIL.correo }}
        role="aprendiz"
        soloLectura={soloLectura}
      />
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.perfil = { ...PERFIL }
  mocks.actualizar.mockResolvedValue({})
  mocks.obtener.mockResolvedValue({ ...PERFIL })
  mocks.cambiarCorreo.mockResolvedValue({})
  mocks.resolverFotoPerfil.mockResolvedValue('data:image/jpeg;base64,foto')
  mocks.recargar.mockResolvedValue(undefined)
})

describe('PerfilBase', () => {
  it('muestra el documento como dato de solo lectura en el perfil propio', async () => {
    const user = userEvent.setup()
    renderPerfil()

    expect(screen.getByText('CC 12345678')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Editar perfil/ }))

    const documento = screen.getByLabelText('Documento de identidad')
    expect(documento).toHaveValue('CC 12345678')
    expect(documento).toHaveAttribute('readonly')
  })

  it('guarda nombre y apellido con payload mínimo y sincroniza la sesión', async () => {
    const user = userEvent.setup()
    renderPerfil()

    await user.click(screen.getByRole('button', { name: /Editar perfil/ }))
    await user.clear(screen.getByLabelText(/Nombres/))
    await user.type(screen.getByLabelText(/Nombres/), 'Ana María')
    await user.clear(screen.getByLabelText(/Apellidos/))
    await user.type(screen.getByLabelText(/Apellidos/), 'Gómez')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => {
      expect(mocks.actualizar).toHaveBeenCalledTimes(1)
    })

    expect(mocks.actualizar).toHaveBeenCalledWith(PERFIL.id, {
      nombre: 'Ana María',
      apellido: 'Gómez',
    })
    expect(mocks.obtener).not.toHaveBeenCalled()
    expect(mocks.sincronizarSesion).toHaveBeenCalledWith('Ana María Gómez', PERFIL.correo)
  })

  it('mantiene el cambio de correo en su flujo independiente', async () => {
    const user = userEvent.setup()
    renderPerfil()

    await user.click(screen.getByRole('button', { name: 'Cambiar correo' }))

    const modal = screen.getByRole('alertdialog')
    await user.type(within(modal).getByLabelText(/Nuevo correo electrónico/), 'NUEVO@Test.LOCAL')
    await user.type(within(modal).getByLabelText(/Contraseña actual/), 'Actual123')
    await user.click(within(modal).getByRole('button', { name: 'Cambiar correo' }))

    await waitFor(() => {
      expect(mocks.cambiarCorreo).toHaveBeenCalledWith('nuevo@test.local', 'Actual123')
    })
    expect(mocks.actualizar).not.toHaveBeenCalled()
  })

  it('no muestra el documento en perfiles públicos o de solo lectura', () => {
    renderPerfil({ soloLectura: true })

    expect(screen.queryByText('Documento')).not.toBeInTheDocument()
    expect(screen.queryByText('CC 12345678')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Documento de identidad')).not.toBeInTheDocument()
  })

  it('muestra No registrado si falta el tipo o el número de documento', () => {
    renderPerfil({
      perfil: { tipo_documento: 'CC', numero_documento: '' },
    })

    expect(screen.getByText('No registrado')).toBeInTheDocument()
  })

  it('mantiene la foto en su flujo independiente', async () => {
    const user = userEvent.setup()
    const { container } = renderPerfil()

    await user.click(screen.getByRole('button', { name: /Editar perfil/ }))

    const input = container.querySelector('input[type="file"]')
    expect(input).not.toBeNull()

    await user.upload(input, new File(['foto'], 'foto.png', { type: 'image/png' }))

    await waitFor(() => {
      expect(mocks.actualizar).toHaveBeenCalledWith(PERFIL.id, {
        foto_url: 'data:image/jpeg;base64,foto',
      })
    })
  })
})
