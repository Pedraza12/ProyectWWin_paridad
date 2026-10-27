import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../../../contexts/ThemeContext'
import Home from './Home'

vi.mock('../../../lib/useApi', () => ({
  useApi: () => ({ data: null }),
}))

vi.mock('../../../components/TextType/TextType', () => ({
  default: () => null,
}))

describe('Home', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 200 }))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('muestra la descarga pública del APK sin iniciar sesión', () => {
    render(
      <ThemeProvider>
        <MemoryRouter>
          <Home />
        </MemoryRouter>
      </ThemeProvider>,
    )

    const enlace = screen.getByRole('link', { name: /descargar apk para android/i })
    expect(enlace).toHaveAttribute('href', '/apk/ProyecTWin.apk')
    expect(enlace).toHaveAttribute('download', 'ProyecTWin.apk')
  })
})
