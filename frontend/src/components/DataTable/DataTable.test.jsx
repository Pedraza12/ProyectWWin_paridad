import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import DataTable from './DataTable'

vi.mock('./DataTable.module.css', () => ({
  default: {
    scroll: 'scroll',
    table: 'table',
    caption: 'caption',
    'align-start': 'align-start',
    'align-center': 'align-center',
    'align-end': 'align-end',
    stickyCol: 'stickyCol',
    expandRow: 'expandRow',
    empty: 'empty',
  },
}))

const COLUMNAS = [
  { key: 'nombre', header: 'Nombre' },
  { key: 'estado', header: 'Estado', render: (r) => <strong>{r.estado}</strong> },
]
const FILAS = [{ id: 1, nombre: 'Ana', estado: 'Activo' }]

describe('DataTable', () => {
  it('muestra vacío cuando no hay filas', () => {
    render(<DataTable columns={COLUMNAS} rows={[]} empty="Nada por aquí." />)
    expect(screen.getByText('Nada por aquí.')).toBeInTheDocument()
  })

  it('renderiza celdas con data-label para modo tarjetas', () => {
    const { container } = render(<DataTable columns={COLUMNAS} rows={FILAS} ariaLabel="Usuarios" />)
    expect(screen.getByRole('table', { name: 'Usuarios' })).toBeInTheDocument()
    const celda = container.querySelector('td[data-label="Estado"]')
    expect(celda?.textContent).toBe('Activo')
  })

  it('aplica sticky a la última columna por defecto y permite desactivarlo', () => {
    const { container, rerender } = render(<DataTable columns={COLUMNAS} rows={FILAS} />)
    let celdas = container.querySelectorAll('td')
    expect(celdas[celdas.length - 1].className).toContain('stickyCol')

    rerender(<DataTable columns={COLUMNAS} rows={FILAS} stickyLast={false} />)
    celdas = container.querySelectorAll('td')
    expect(celdas[celdas.length - 1].className).not.toContain('stickyCol')
  })
})
