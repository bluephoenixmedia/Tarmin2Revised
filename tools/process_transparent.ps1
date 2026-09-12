param(
    [string]$SourceDir = "tools/raw_weapons",
    [string]$OutputDir = "tools/raw_weapons/transparent",
    [string]$fuzzValue = "10%",
    [string]$erodeType = "Diamond:2"
)

$sourcePath = Resolve-Path $SourceDir
$destPath = Join-Path $sourcePath "transparent"
if ($OutputDir -ne "tools/raw_weapons/transparent") {
    $destPath = Resolve-Path $OutputDir
}

New-Item -ItemType Directory -Force -Path $destPath | Out-Null

Get-ChildItem -Path $sourcePath -File | Where-Object { $_.Extension -match '\.(png|jpg|jpeg|bmp)$' } | ForEach-Object {
    $color = magick $_.FullName -format "%[pixel:p{5,5}]" info:
    $newName = [System.IO.Path]::ChangeExtension($_.Name, "png")
    $outputPath = Join-Path $destPath $newName
    
    magick $_.FullName -fuzz $fuzzValue -transparent $color -channel A -morphology Erode $erodeType +channel $outputPath
    Write-Host "Processed: $($_.Name) | Color: $color | Fuzz: $fuzzValue | Erode: $erodeType -> $outputPath"
}
