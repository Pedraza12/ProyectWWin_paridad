# exportar_pdf.ps1
# Exporta el Manual Técnico a PDF (solo lectura del DOCX) para verificación visual.
# Usa SaveAs2 con formato PDF (wdFormatPDF=17), que en este entorno funciona de
# forma más estable que ExportAsFixedFormat.
$ErrorActionPreference = "Stop"
$ruta = Join-Path (Split-Path -Parent $PSScriptRoot) "Manual_Tecnico_ProyecTwin.docx"
$pdf  = Join-Path (Split-Path -Parent $PSScriptRoot) "Manual_Tecnico_ProyecTwin_verificacion.pdf"

if (Test-Path $pdf) { Remove-Item $pdf -Force }

$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Open($ruta, $false, $true)  # ReadOnly
    $doc.SaveAs2([string]$pdf, [int]17)                # 17 = PDF
    $doc.Close($false)
    if (Test-Path $pdf) {
        Write-Output ("PDF exportado: {0} ({1:N1} MB)" -f $pdf, ((Get-Item $pdf).Length / 1MB))
    } else {
        Write-Output "ERROR: el PDF no se creó"
    }
} finally {
    $word.Quit()
}
