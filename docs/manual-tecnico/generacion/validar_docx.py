# -*- coding: utf-8 -*-
"""validar_docx.py — Validación estructural del Manual Técnico generado.

Comprueba: existencia y apertura del archivo, secciones y paginación,
presencia de los 8 capítulos, TOC diligenciado por Word, numeración de
leyendas (figuras/tablas/códigos) y estilos principales.
"""
import os
import re
import sys
from docx import Document
from docx.shared import Pt

AQUI = os.path.dirname(os.path.abspath(__file__))
DOCX = os.path.abspath(os.path.join(AQUI, "..", "Manual_Tecnico_ProyecTwin.docx"))

ok, avisos = [], []

if not os.path.exists(DOCX):
    print("FALLA: no existe el documento " + DOCX)
    sys.exit(1)

doc = Document(DOCX)
texto_total = "\n".join(p.text for p in doc.paragraphs)
for t in doc.tables:
    for fila in t.rows:
        for c in fila.cells:
            texto_total += "\n" + c.text

print(f"Archivo: {DOCX}")
print(f"Tamaño: {os.path.getsize(DOCX)/1e6:.2f} MB")
print(f"Párrafos: {len(doc.paragraphs)} | Tablas: {len(doc.tables)} | Secciones: {len(doc.sections)}")

# 1) Capítulos presentes
for cap in ["CAPÍTULO 1", "CAPÍTULO 2", "CAPÍTULO 3", "CAPÍTULO 4",
            "CAPÍTULO 5", "CAPÍTULO 6", "CAPÍTULO 7", "CAPÍTULO 8",
            "REFERENCIAS BIBLIOGRÁFICAS", "ANEXO A", "ANEXO F"]:
    if cap in texto_total:
        ok.append(f"Presente: {cap}")
    else:
        avisos.append(f"FALTA: {cap}")

# 2) TOC diligenciado (Word inserta las entradas en el campo)
m = re.search(r"CAPÍTULO 8\. INTERFACES Y FUNCIONES IMPLEMENTADAS[^\n]*?(\d+)", texto_total)
if m:
    ok.append(f"TOC diligenciado: 'CAPÍTULO 8' con página {m.group(1)}")
else:
    # Puede estar en varias líneas (entrada + tab + número)
    m2 = re.search(r"CAPÍTULO 8\. INTERFACES Y FUNCIONALIDADES IMPLEMENTADAS\s*[\t\.]*\s*(\d+)", texto_total)
    if m2:
        ok.append(f"TOC diligenciado: 'CAPÍTULO 8' con página {m2.group(1)}")
    else:
        avisos.append("TOC: no se encontró la entrada del capítulo 8 con número de página (¿campo sin actualizar?)")

# 3) Listas de figuras y tablas diligenciadas
if re.search(r"Índice de figuras[\s\S]{0,200}?Figura 1\.", texto_total):
    ok.append("Lista de figuras diligenciada")
else:
    avisos.append("Lista de figuras: sin entradas visibles")

if re.search(r"Índice de tablas[\s\S]{0,200}?Tabla 1\.", texto_total):
    ok.append("Lista de tablas diligenciada")
else:
    avisos.append("Lista de tablas: sin entradas visibles")

# 4) Conteo de leyendas
figs = sorted(set(int(x) for x in re.findall(r"Figura (\d+)\.", texto_total)))
tabs = sorted(set(int(x) for x in re.findall(r"Tabla (\d+)\.", texto_total)))
print(f"Leyendas de figura detectadas: {len(figs)} (máx {max(figs) if figs else 0})")
print(f"Leyendas de tabla detectadas: {len(tabs)} (máx {max(tabs) if tabs else 0})")
if len(figs) >= 32:
    ok.append(f"32 figuras numeradas (detectadas {len(figs)})")
else:
    avisos.append(f"Figuras: se esperaban 32, detectadas {len(figs)}")
if len(tabs) >= 66:
    ok.append(f"66 tablas numeradas (detectadas {len(tabs)})")
else:
    avisos.append(f"Tablas: se esperaban 66, detectadas {len(tabs)}")

# 5) Estilos y tipografías
n = doc.styles["Normal"]
h1 = doc.styles["Heading 1"]
h2 = doc.styles["Heading 2"]
print(f"Normal: {n.font.name} {n.font.size.pt if n.font.size else '?'} pt")
print(f"Heading 1: {h1.font.name} {h1.font.size.pt if h1.font.size else '?'} pt bold={h1.font.bold}")
print(f"Heading 2: {h2.font.name} {h2.font.size.pt if h2.font.size else '?'} pt bold={h2.font.bold}")
if n.font.name == "Arial" and n.font.size == Pt(11):
    ok.append("Estilo Normal en Arial 11")
if h1.font.size == Pt(13) and h2.font.size == Pt(12):
    ok.append("Jerarquía de títulos H1 13 / H2 12")

# 6) Secciones: encabezados y campos PAGE
import xml.etree.ElementTree as ET
NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
for i, sec in enumerate(doc.sections):
    hdr = "".join(p.text for p in sec.header.paragraphs).strip()
    xml = sec._sectPr.xml
    roman = 'w:fmt="lowerRoman"' in xml
    decimal = 'w:fmt="decimal"' in xml
    print(f"Sección {i}: encabezado='{hdr[:40]}' | romana={roman} | decimal={decimal}")

# 7) updateFields activo
xml_settings = doc.settings.element.xml
if "updateFields" in xml_settings:
    ok.append("Actualización de campos al abrir activada (w:updateFields)")

# 8) Muestra de acentos correctos
for muestra in ["Arquitectura", "Ejecución", "Diseño", "Índice"]:
    if muestra in texto_total:
        ok.append(f"Acentos correctos: «{muestra}»")

print("\n--- RESULTADOS ---")
for o in ok:
    print("OK  ", o)
for a in avisos:
    print("AVISO", a)
print(f"\nTotal OK: {len(ok)} | Avisos: {len(avisos)}")
