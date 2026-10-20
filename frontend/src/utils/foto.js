import { apiSubirArchivo } from '../lib/api'

const MAX_BYTES = 5 * 1024 * 1024
const LADO = 256

// Procesa la imagen en el navegador (recorte cuadrado + JPEG) y devuelve un
// Blob listo para subir o convertir a data URL.
export async function procesarFotoBlob(file) {
  if (!file) throw new Error('Selecciona un archivo.')
  if (!file.type.startsWith('image/')) throw new Error('El archivo debe ser una imagen.')
  if (file.size > MAX_BYTES) throw new Error('La imagen no debe superar 5MB.')

  const dataUrl = await leerComoDataUrl(file)
  const img = await cargarImagen(dataUrl)

  const lado = Math.min(img.naturalWidth, img.naturalHeight)
  const sx = (img.naturalWidth - lado) / 2
  const sy = (img.naturalHeight - lado) / 2

  const canvas = document.createElement('canvas')
  canvas.width = LADO
  canvas.height = LADO
  canvas.getContext('2d').drawImage(img, sx, sy, lado, lado, 0, 0, LADO, LADO)

  return await new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('No fue posible procesar la imagen.'))),
      'image/jpeg',
      0.82,
    )
  })
}

// Formato histórico (data URL). Se conserva como respaldo y compatibilidad.
export async function procesarFoto(file) {
  return blobADataUrl(await procesarFotoBlob(file))
}

// Resuelve la foto de perfil: sube a Cloudinary (servidor) y, si no está
// configurado o falla, mantiene el guardado local (data URL) de siempre.
export async function resolverFotoPerfil(file) {
  const blob = await procesarFotoBlob(file)

  try {
    const datos = await apiSubirArchivo('/media/foto', blob, { nombre: 'perfil.jpg', campo: 'foto' })
    if (datos?.foto_url && /^https:\/\//i.test(datos.foto_url)) return datos.foto_url
  } catch {
    /* Cloudinary no configurado o sin red: se guarda localmente */
  }

  return blobADataUrl(blob)
}

function blobADataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('No fue posible leer la imagen.'))
    reader.readAsDataURL(blob)
  })
}

function leerComoDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('No fue posible leer la imagen.'))
    reader.readAsDataURL(file)
  })
}

function cargarImagen(src) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('La imagen no es válida.'))
    img.src = src
  })
}
