# actualizar_campos.ps1
# Actualiza los campos automáticos (TOC, listas de figuras/tablas, PAGE) del
# Manual Técnico de ProyecTwin usando Microsoft Word y guarda el documento.
# Complementa a w:updateFields (Word también los actualizaría al abrir).
#
# Uso:  powershell -ExecutionPolicy Bypass -File actualizar_campos.ps1
$ErrorActionPreference = "Stop"
$ruta = Join-Path (Split-Path -Parent $PSScriptRoot) "Manual_Tecnico_ProyecTwin.docx"

if (-not (Test-Path $ruta)) {
    Write-Output "ERROR: no existe $ruta"
    exit 1
}

$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Open($ruta, $false, $false)
    $doc.Fields.Update() | Out-Null
    foreach ($toc in $doc.TablesOfContents) { $toc.Update() | Out-Null }
    foreach ($tof in $doc.TablesOfFigures) { $tof.Update() | Out-Null }
    $doc.Repaginate()
    $paginas = $doc.ComputeStatistics(2)   # wdStatisticPages
    $palabras = $doc.ComputeStatistics(0)  # wdStatisticWords
    $doc.Save()
    $doc.Close()
    Write-Output "Campos actualizados. Páginas: $paginas | Palabras: $palabras"
} finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}
