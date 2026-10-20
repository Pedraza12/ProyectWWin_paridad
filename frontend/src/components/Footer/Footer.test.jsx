import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import Footer from './Footer'

describe('Footer', () => {
  it('no incluye navegación duplicada', () => {
    const { container } = render(<Footer />)
    expect(container.querySelector('nav')).toBeNull()
  })

  it('conserva copyright y datos de contacto', () => {
    const { container } = render(<Footer />)
    expect(container.textContent).toContain('Todos los derechos reservados')
    expect(screen.getByRole('link', { name: /323/ })).toHaveAttribute('href', 'tel:3235421165')
    expect(screen.getByRole('link', { name: /sena@correo/i })).toHaveAttribute('href', 'mailto:sena@correo.edu.co')
  })
})
