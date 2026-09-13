$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$source = [Drawing.Image]::FromFile((Join-Path $root 'marketing/icon/icon-master.png'))
try {
  foreach ($item in @(@{Path='marketing/icon/play-icon-512.png';Size=512}, @{Path='app/src/main/res/drawable-nodpi/ic_launcher_art.png';Size=512})) {
    $target = Join-Path $root $item.Path
    [IO.Directory]::CreateDirectory((Split-Path $target -Parent)) | Out-Null
    $bitmap = [Drawing.Bitmap]::new($item.Size,$item.Size,[Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try {
      $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
      $graphics.DrawImage($source,0,0,$item.Size,$item.Size)
      $bitmap.Save($target,[Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose() }
  }
} finally { $source.Dispose() }
