import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ApkDownloadButton, { APK_FILENAME, APK_URL } from './ApkDownloadButton'

describe('ApkDownloadButton', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('expone el APK oficial como enlace público de descarga', () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 200 }))
    render(<ApkDownloadButton />)

    const enlace = screen.getByRole('link', { name: /descargar apk para android/i })
    expect(enlace).toHaveAttribute('href', APK_URL)
    expect(enlace).toHaveAttribute('download', APK_FILENAME)
  })

  it('avisa y bloquea la descarga si el archivo responde 404', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 404 }))
    render(<ApkDownloadButton />)

    expect(await screen.findByRole('status')).toHaveTextContent(/no está disponible/i)
    expect(screen.getByRole('link', { name: /descargar apk para android/i }))
      .toHaveAttribute('aria-disabled', 'true')
  })

  it('detecta el fallback HTML del servidor como APK no disponible', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        status: 200,
        headers: { get: () => 'text/html; charset=utf-8' },
      }),
    )
    render(<ApkDownloadButton />)

    expect(await screen.findByRole('status')).toHaveTextContent(/no está disponible/i)
  })
})
