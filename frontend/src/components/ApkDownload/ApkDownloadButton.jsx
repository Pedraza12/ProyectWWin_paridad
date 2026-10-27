import { useEffect, useState } from 'react'
import { DownloadSimple } from 'phosphor-react'
import Button from '../Button/Button'
import s from './ApkDownloadButton.module.css'

/**
 * Descarga pública del APK oficial servido por el contenedor
 * (`backend/public/apk/ProyecTWin.apk` → nginx → `/apk/ProyecTWin.apk`).
 * No requiere sesión ni rol: es un enlace estático del Home.
 *
 * Se comprueba una vez que el archivo exista para avisar al visitante. Un
 * fallo de red no bloquea el enlace; solo una respuesta de error o el fallback
 * HTML del SPA lo deshabilitan.
 */
export const APK_URL = '/apk/ProyecTWin.apk'
export const APK_FILENAME = 'ProyecTWin.apk'

export default function ApkDownloadButton({ className = '' }) {
  const [noDisponible, setNoDisponible] = useState(false)

  useEffect(() => {
    let vivo = true
    const controller = new AbortController()
    fetch(APK_URL, { method: 'HEAD', signal: controller.signal })
      .then((respuesta) => {
        // En nginx, un archivo ausente cae en el fallback del SPA y responde
        // 200 con text/html: se detecta por el tipo, no solo por el 404.
        const tipo = respuesta.headers?.get('content-type') || ''
        if (vivo && (respuesta.status >= 400 || tipo.includes('text/html'))) {
          setNoDisponible(true)
        }
      })
      .catch(() => {
        /* Sin conexión o proxy caído: se conserva el enlace directo. */
      })
    return () => {
      vivo = false
      controller.abort()
    }
  }, [])

  return (
    <div className={`${s.wrapper} ${className}`}>
      <Button
        as="a"
        href={APK_URL}
        download={APK_FILENAME}
        variant="secondary"
        aria-describedby={noDisponible ? 'apk-no-disponible' : undefined}
        aria-disabled={noDisponible || undefined}
        onClick={(evento) => {
          if (noDisponible) evento.preventDefault()
        }}
      >
        <DownloadSimple size={16} aria-hidden="true" />
        Descargar APK para Android
      </Button>
      {noDisponible && (
        <span id="apk-no-disponible" role="status" className={s.error}>
          El APK no está disponible en este momento. Intenta de nuevo más tarde.
        </span>
      )}
    </div>
  )
}
