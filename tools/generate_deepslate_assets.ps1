# Generates placeholder deepslate ore/stone block assets for tc4-modern.
# Textures are original procedural placeholders; no Thaumcraft assets are copied.
# Re-run after editing $Blocks to add or change variants.
param(
    [string]$Resources = (Join-Path $PSScriptRoot '..\tc4-modern\src\main\resources')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Clamp([int]$v, [int]$lo, [int]$hi) { [Math]::Max($lo, [Math]::Min($hi, $v)) }

# id, ore speck RGB, highlight RGB
$Blocks = @(
    @{ id = 'deepslate_cinnabar_ore';          speck = @(139, 26, 26);  hi = @(200, 60, 60) },
    @{ id = 'deepslate_air_infused_stone';     speck = @(255, 255, 126); hi = @(255, 255, 220) },
    @{ id = 'deepslate_fire_infused_stone';    speck = @(255, 60, 1);   hi = @(255, 160, 60) },
    @{ id = 'deepslate_water_infused_stone';   speck = @(0, 144, 255);  hi = @(140, 210, 255) },
    @{ id = 'deepslate_earth_infused_stone';   speck = @(0, 160, 0);    hi = @(120, 230, 120) },
    @{ id = 'deepslate_order_infused_stone';   speck = @(238, 204, 255); hi = @(255, 255, 255) },
    @{ id = 'deepslate_entropy_infused_stone'; speck = @(85, 85, 119);  hi = @(150, 150, 190) },
    @{ id = 'deepslate_amber_bearing_stone';   speck = @(232, 160, 32); hi = @(255, 220, 120) }
)

function Write-Json([string]$path, [string]$content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
}

function New-OreTexture([string]$path, [int[]]$speck, [int[]]$hi) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    $rnd = New-Object System.Random ([Math]::Abs($path.GetHashCode()))
    for ($x = 0; $x -lt 16; $x++) {
        for ($y = 0; $y -lt 16; $y++) {
            $g = (74 + $rnd.Next(-14, 15))
            $r = Clamp ($g - 2) 40 110
            $gg = Clamp $g 40 110
            $b = Clamp ($g + 6) 44 116
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $gg, $b))
        }
    }
    for ($i = 0; $i -lt 4; $i++) {
        $cx = $rnd.Next(2, 14); $cy = $rnd.Next(2, 14)
        for ($dx = -1; $dx -le 1; $dx++) {
            for ($dy = -1; $dy -le 1; $dy++) {
                if ($rnd.NextDouble() -lt 0.72) {
                    $px = Clamp ($cx + $dx) 0 15
                    $py = Clamp ($cy + $dy) 0 15
                    $bmp.SetPixel($px, $py, [System.Drawing.Color]::FromArgb(255, $speck[0], $speck[1], $speck[2]))
                }
            }
        }
        $bmp.SetPixel($cx, $cy, [System.Drawing.Color]::FromArgb(255, $hi[0], $hi[1], $hi[2]))
    }
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$pickaxe = @()
$iron = @()
foreach ($b in $Blocks) {
    $id = $b.id
    Write-Json (Join-Path $Resources "assets\tc4modern\blockstates\$id.json") ('{"variants":{"":{"model":"tc4modern:block/' + $id + '"}}}')
    Write-Json (Join-Path $Resources "assets\tc4modern\models\block\$id.json") ('{"parent":"block/cube_all","textures":{"all":"tc4modern:block/' + $id + '"}}')
    Write-Json (Join-Path $Resources "assets\tc4modern\models\item\$id.json") ('{"parent":"tc4modern:block/' + $id + '"}')
    Write-Json (Join-Path $Resources "data\tc4modern\loot_tables\blocks\$id.json") ('{"type":"minecraft:block","pools":[{"rolls":1,"bonus_rolls":0,"entries":[{"type":"minecraft:item","name":"tc4modern:' + $id + '"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}')
    New-OreTexture (Join-Path $Resources "assets\tc4modern\textures\block\$id.png") $b.speck $b.hi
    $pickaxe += "tc4modern:$id"
    if ($id -eq 'deepslate_cinnabar_ore' -or $id -eq 'deepslate_amber_bearing_stone') { $iron += "tc4modern:$id" }
}

function Write-Tag([string]$rel, [string[]]$values) {
    $joined = ($values | ForEach-Object { '"' + $_ + '"' }) -join ','
    Write-Json (Join-Path $Resources $rel) ('{"replace":false,"values":[' + $joined + ']}')
}
Write-Tag 'data\minecraft\tags\blocks\mineable\pickaxe.json' $pickaxe
Write-Tag 'data\minecraft\tags\blocks\needs_iron_tool.json' $iron

Write-Host ("Generated assets for {0} blocks." -f $Blocks.Count)
