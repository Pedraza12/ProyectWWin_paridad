# -*- coding: utf-8 -*-
"""Verificación final de fuentes: estilos de título, tema y primeras páginas."""
import zipfile
import re

RUTA = r"D:\ProyecTwin-MVP\docs\manual-tecnico\Manual_Tecnico_ProyecTwin.docx"
z = zipfile.ZipFile(RUTA)
s = z.read("word/styles.xml").decode("utf-8")
t = z.read("word/theme/theme1.xml").decode("utf-8")

print("=== Tema (fuentes base) ===")
for grupo in ("majorFont", "minorFont"):
    m = re.search(r"<a:" + grupo + r">\s*<a:latin typeface=\"([^\"]*)\"", t)
    print(f"{grupo}: {m.group(1) if m else '?'}")

print("\n=== Estilos de título ===")
for nombre in ["heading 1", "heading 2", "heading 3", "Title", "Normal",
               "LeyendaManual", "CodigoManual", "TablaTexto"]:
    m = re.search(r'<w:style [^>]*>\s*<w:name w:val="' + re.escape(nombre) + r'"/>.*?</w:style>', s, re.S)
    if not m:
        print(f"{nombre}: NO ENCONTRADO")
        continue
    frag = m.group(0)
    rfonts = re.search(r"<w:rFonts [^/]*/>", frag)
    sz = re.findall(r'<w:sz w:val="(\d+)"', frag)
    print(f"{nombre}:")
    print("   rFonts:", rfonts.group(0) if rfonts else "(hereda)")
    print("   sz:", sz)
