# Generates deepslate ore/stone block data assets for tc4-modern (block states, models, loot tables, tags).
# Models draw vanilla's own deepslate texture with an ore overlay on top. The overlay textures are not shipped:
# DeepslateOreTextures derives them at runtime from the installed Thaumcraft, so no Thaumcraft art is copied.
# Re-run after editing $Blocks to add or change variants.
param(
    [string]$Resources = (Join-Path $PSScriptRoot '..\tc4-modern\src\main\resources')
)
$ErrorActionPreference = 'Stop'
$Blocks = @(
    @{ id = 'deepslate_cinnabar_ore' },
    @{ id = 'deepslate_air_infused_stone' },
    @{ id = 'deepslate_fire_infused_stone' },
    @{ id = 'deepslate_water_infused_stone' },
    @{ id = 'deepslate_earth_infused_stone' },
    @{ id = 'deepslate_order_infused_stone' },
    @{ id = 'deepslate_entropy_infused_stone' },
    @{ id = 'deepslate_amber_bearing_stone' }
)

function Write-Json([string]$path, [string]$content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
}

# Shared two-layer cube: vanilla deepslate, then the cutout ore overlay (the same layering as grass_block's side).
$faces = { param($t) (('down','up','north','south','west','east') | ForEach-Object { '"' + $_ + '":{"texture":"#' + $t + '","cullface":"' + $_ + '"}' }) -join ',' }
Write-Json (Join-Path $Resources "assets\tc4modern\models\block\deepslate_ore_overlay.json") ('{"parent":"block/block","render_type":"minecraft:cutout",' +
    '"textures":{"particle":"minecraft:block/deepslate","base":"minecraft:block/deepslate"},"elements":[' +
    '{"from":[0,0,0],"to":[16,16,16],"faces":{' + (& $faces 'base') + '}},' +
    '{"from":[0,0,0],"to":[16,16,16],"faces":{' + (& $faces 'overlay') + '}}]}')
Write-Json (Join-Path $Resources "assets\tc4modern\models\block\deepslate_infused_overlay.json") ('{"parent":"block/block","render_type":"minecraft:cutout",' +
    '"textures":{"particle":"minecraft:block/deepslate","base":"minecraft:block/deepslate"},"elements":[' +
    '{"from":[0,0,0],"to":[16,16,16],"faces":{' + (& $faces 'base') + '}},' +
    '{"from":[0,0,0],"to":[16,16,16],"forge_data":{"block_light":10},"faces":{' + (& $faces 'overlay') + '}}]}')

$pickaxe = @()
$iron = @()
foreach ($b in $Blocks) {
    $id = $b.id
    Write-Json (Join-Path $Resources "assets\tc4modern\blockstates\$id.json") ('{"variants":{"":{"model":"tc4modern:block/' + $id + '"}}}')
    # Infused stones glow like the original renderer (brightness >= 160, i.e. block light 10).
    $parent = if ($id -like '*_infused_stone') { 'deepslate_infused_overlay' } else { 'deepslate_ore_overlay' }
    Write-Json (Join-Path $Resources "assets\tc4modern\models\block\$id.json") ('{"parent":"tc4modern:block/' + $parent + '","textures":{"overlay":"tc4modern:block/' + $id + '"}}')
    Write-Json (Join-Path $Resources "assets\tc4modern\models\item\$id.json") ('{"parent":"tc4modern:block/' + $id + '"}')
    Write-Json (Join-Path $Resources "data\tc4modern\loot_tables\blocks\$id.json") ('{"type":"minecraft:block","pools":[{"rolls":1,"bonus_rolls":0,"entries":[{"type":"minecraft:item","name":"tc4modern:' + $id + '"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}')
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
