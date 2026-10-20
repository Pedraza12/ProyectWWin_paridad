import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import QuickActions from './QuickActions'

const ITEMS = [
  {
    to: '/aprendiz/propuestas?crear=1',
    icon: <span aria-hidden="true">+</span>,
    titulo: 'Nueva Propuesta',
    descripcion: 'Crea tu propuesta y analízala al instante',
  },
]

describe('QuickActions', () => {
  it('renderiza tarjetas enlazadas con título y descripción', () => {
    render(
      <MemoryRouter>
        <QuickActions items={ITEMS} />
      </MemoryRouter>,
    )

    const enlace = screen.getByRole('link', { name: /Nueva Propuesta/ })
    expect(enlace).toHaveAttribute('href', '/aprendiz/propuestas?crear=1')
    expect(screen.getByText('Crea tu propuesta y analízala al instante')).toBeInTheDocument()
  })

  it('soporta la variante stack sin cambiar las acciones', () => {
    render(
      <MemoryRouter>
        <QuickActions items={ITEMS} variant="stack" />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: /Nueva Propuesta/ })).toBeInTheDocument()
  })

  it('no falla cuando la lista está vacía', () => {
    const { container } = render(
      <MemoryRouter>
        <QuickActions items={[]} />
      </MemoryRouter>,
    )
    expect(container.querySelectorAll('a')).toHaveLength(0)
  })
})
