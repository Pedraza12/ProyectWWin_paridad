import { useEffect, useState } from 'react'

// Retrasa la propagación de un valor (p. ej. la caja de búsqueda) para no
// lanzar una petición al servidor por cada pulsación.
export function useDebounced(valor, ms = 300) {
  const [debounced, setDebounced] = useState(valor)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(valor), ms)
    return () => clearTimeout(timer)
  }, [valor, ms])

  return debounced
}
