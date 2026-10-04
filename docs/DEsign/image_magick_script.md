# --- CONFIGURATION ---
$fuzzValue = "20%"          # Increased from 20% to catch more green blend
$erodeType = "Diamond:2"    # Changed from Disk:1 to Diamond:2 (Try Square:1 for very blocky look)
# ---------------------

New-Item -ItemType Directory -Force -Path transparent

Get-ChildItem * -Include *.png, *.jpg, *.jpeg, *.bmp | ForEach-Object {
    
    # 1. Sample the color at 5,5
    $color = magick $_.FullName -format "%[pixel:p{5,5}]" info:
    
    # 2. Setup output name
    $newName = [io.path]::ChangeExtension($_.Name, "png")
    $outputPath = Join-Path "transparent" $newName
    
    # 3. Process
    magick $_.FullName -fuzz $fuzzValue -transparent $color -channel A -morphology Erode $erodeType +channel $outputPath

    Write-Host "Processed: $($_.Name) | Color: $color | Fuzz: $fuzzValue | Erode: $erodeType"
}