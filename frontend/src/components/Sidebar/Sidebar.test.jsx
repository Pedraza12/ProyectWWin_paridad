import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Sidebar from './Sidebar'

const LINKS = [
  { to: '/admin/dashboard', label: 'Dashboard', icon: <span>D</span> },
  { to: '/admin/bitacora', label: 'Bitácora', icon: <span>B</span> },
  { to: '/admin/redes-conocimiento', label: 'Redes y programas', group: 'Configuración', icon: <span>R</span> },
  { to: '/admin/config-similitud', label: 'Motor de similitud', group: 'Configuración', icon: <span>M</span> },
]

function renderSidebar() {
  return render(
    <MemoryRouter initialEntries={['/admin/dashboard']}>
      <Sidebar role="admin" links={LINKS} onClose={() => {}} />
    </MemoryRouter>,
  )
}

describe('Sidebar', () => {
  it('muestra el grupo Configuración una sola vez y sin párrafos', () => {
    const { container } = renderSidebar()
    expect(screen.getAllByText('Configuración')).toHaveLength(1)
    expect(container.querySelectorAll('p')).toHaveLength(0)
    expect(screen.getByRole('link', { name: /Redes y programas/ })).toHaveAttribute('href', '/admin/redes-conocimiento')
    expect(screen.getByRole('link', { name: /Motor de similitud/ })).toHaveAttribute('href', '/admin/config-similitud')
  })

  it('conserva los enlaces y el nombre accesible del aside', () => {
    renderSidebar()
    expect(screen.getByRole('complementary', { name: /Navegación principal/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Bitácora/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Cerrar menú/ })).toBeInTheDocument()
  })
})
