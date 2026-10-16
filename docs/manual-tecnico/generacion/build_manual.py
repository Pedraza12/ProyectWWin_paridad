# -*- coding: utf-8 -*-
"""
build_manual.py — Orquestador de generación del Manual Técnico de ProyecTwin.

Uso:
    python build_manual.py

Genera:
    ../Manual_Tecnico_ProyecTwin.docx   (dentro de docs/manual-tecnico/)

El documento usa estilos nativos de Word, campos automáticos de índice y
listas, numeración romana en preliminares y árabe en el cuerpo. La
actualización de los campos se realiza al abrir el documento
(w:updateFields) y, cuando Word está disponible, mediante el script
actualizar_campos.ps1.
"""
import os
import sys
import zipfile
import re

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

from manual_lib import Manual
import contenido_a, contenido_b, contenido_c, contenido_d, contenido_e, contenido_f

RAIZ_MANUAL = os.path.abspath(os.path.join(AQUI, ".."))
SALIDA = os.path.join(RAIZ_MANUAL, "Manual_Tecnico_ProyecTwin.docx")

P = {
    "fig": os.path.join(RAIZ_MANUAL, "figuras"),
    "cap": os.path.join(RAIZ_MANUAL, "capturas"),
    "logo": r"D:\ProyecTwin-MVP\frontend\public\images\Logo-ProyecTwin.png",
}


def forzar_tema_arial(ruta_docx):
    """Sustituye las fuentes principales/secundarias del tema por Arial para
    que cualquier texto que herede del tema (índices, pies, etc.) use Arial."""
    temp = ruta_docx + ".tmp"
    with zipfile.ZipFile(ruta_docx, "r") as zin, zipfile.ZipFile(temp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            datos = zin.read(item.filename)
            if item.filename == "word/theme/theme1.xml":
                texto = datos.decode("utf-8")
                texto = re.sub(r"(<a:majorFont>.*?<a:latin typeface=\")[^\"]*(\")", r"\1Arial\2", texto, count=1, flags=re.S)
                texto = re.sub(r"(<a:minorFont>.*?<a:latin typeface=\")[^\"]*(\")", r"\1Arial\2", texto, count=1, flags=re.S)
                datos = texto.encode("utf-8")
            zout.writestr(item, datos)
    os.replace(temp, ruta_docx)


def main():
    M = Manual(RAIZ_MANUAL)

    # Preliminares (portada sin encabezado; el resto en sección con numeración romana)
    contenido_a.escribir(M, P)

    # Cuerpo (nueva sección, numeración árabe desde 1)
    sec = M.salto_seccion()
    M.encabezado_y_pie(sec, "Manual Técnico — ProyecTwin", numeracion="decimal", inicio=1)

    contenido_b.escribir(M, P)   # Capítulos 1 y 2
    contenido_c.escribir(M, P)   # Capítulos 3 y 4
    contenido_d.escribir(M, P)   # Capítulos 5 y 6
    contenido_e.escribir(M, P)   # Capítulos 7 y 8
    contenido_f.escribir(M, P)   # Referencias y anexos

    ruta = M.guardar(SALIDA)
    forzar_tema_arial(ruta)
    print(f"DOCX generado: {ruta}")
    print(f"Figuras: {M.n_figuras} | Tablas: {M.n_tablas} | Fragmentos de código: {M.n_codigos}")


if __name__ == "__main__":
    main()
