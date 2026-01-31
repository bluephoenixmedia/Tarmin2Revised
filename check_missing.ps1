$jsonPath = "assets/data/weapons.json"
$placeholder = "assets/images/weapons/empty_weapon.png"

# Create placeholder if it doesn't exist
if (-not (Test-Path $placeholder)) {
    Write-Host "Creating transparent placeholder..."
    Add-Type -AssemblyName System.Drawing
    $bmp = New-Object System.Drawing.Bitmap 32, 32
    # By default, new bitmap is transparent
    $bmp.Save($placeholder, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$content = Get-Content $jsonPath
$regex = '"texturePath":\s*"(.*?)"'

foreach ($line in $content) {
    if ($line -match $regex) {
        $path = $matches[1]
        $fullPath = "assets/$path"
        
        if (-not (Test-Path $fullPath)) {
            Write-Host "Missing: $fullPath - Creating from placeholder..."
            Copy-Item $placeholder $fullPath
        }
    }
}
Write-Host "Done checking."
