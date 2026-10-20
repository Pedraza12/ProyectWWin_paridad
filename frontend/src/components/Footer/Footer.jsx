import { CONTACTO } from '../../constants/contacto'
import { Phone, Envelope, Copyright } from 'phosphor-react'
import s from './Footer.module.css'

export default function Footer() {
  return (
    <footer className={s.footer}>
      <div className={s.container}>
        <div className={s.left}>
          <p className={s.copy}>
            <Copyright size={13} weight="regular" />
            {CONTACTO.copyright}. Todos los derechos reservados.
          </p>
        </div>

        <div className={s.contact}>
          <a href={`tel:${CONTACTO.telefono.replace(/\s/g, '')}`}>
            <Phone size={12} weight="regular" />
            {CONTACTO.telefono}
          </a>
          <span className={s.sep} aria-hidden="true">|</span>
          <a href={`mailto:${CONTACTO.email}`}>
            <Envelope size={12} weight="regular" />
            {CONTACTO.email}
          </a>
        </div>
      </div>
    </footer>
  )
}
