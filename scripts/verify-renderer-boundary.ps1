param(
    [string]$RendererPath = 'src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java',
    [string]$ProviderPath = 'src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java'
)

$renderer = Get-Content -LiteralPath $RendererPath -Raw
$provider = Get-Content -LiteralPath $ProviderPath -Raw
$metadata = Get-Content -LiteralPath 'src/com/lushprojects/circuitjs1/client/PhysicalPartRenderMetadata.java' -Raw
$probeProviders = Get-Content -LiteralPath 'src/com/lushprojects/circuitjs1/client/PhysicalPartRenderProbeProviders.java' -Raw

$requiredRendererTokens = @(
    'PhysicalPartRenderRegistry',
    'PhysicalPartRenderContext',
    'getInstalledGeometry',
    'getLooseGeometry',
    'createLooseProbeTarget'
)
foreach ($token in $requiredRendererTokens) {
    if ($renderer.IndexOf($token, [StringComparison]::Ordinal) -lt 0) {
        throw "renderer provider boundary token is missing: $token"
    }
}

$forbiddenRendererTokens = @(
    'drawResistor(',
    'drawDiode(',
    'drawLed(',
    'drawConnector(',
    'PhysicalResistorPart',
    'PhysicalDiodePart',
    'PhysicalLedPart',
    '"RESISTOR"',
    '"DIODE"',
    '"LED"',
    '"CONNECTOR"'
)
foreach ($token in $forbiddenRendererTokens) {
    if ($renderer.IndexOf($token, [StringComparison]::Ordinal) -ge 0) {
        throw "generic renderer still contains component-specific dispatch: $token"
    }
}

$requiredProviderTokens = @(
    'PhysicalPartRenderProvider',
    'PhysicalPartRenderer',
    'PhysicalPackages.AXIAL_RESISTOR',
    'PhysicalPackages.AXIAL_DIODE',
    'PhysicalPackages.THROUGH_HOLE_LED',
    'PhysicalPackages.THROUGH_HOLE_CONNECTOR_2',
    'PhysicalPackages.DEV_CANARY_3',
    'PhysicalPackages.DEV_CANARY_6',
    'getLooseProbeProvider'
)
foreach ($token in $requiredProviderTokens) {
    if ($provider.IndexOf($token, [StringComparison]::Ordinal) -lt 0) {
        throw "physical render provider registration token is missing: $token"
    }
}

foreach ($token in @(
    'instanceof PhysicalResistorPart',
    'instanceof PhysicalDiodePart',
    'instanceof PhysicalLedPart'
)) {
    if ($provider.IndexOf($token, [StringComparison]::Ordinal) -ge 0) {
        throw "physical render provider still gates loose probes by concrete subclass: $token"
    }
}

foreach ($token in @(
    'getLooseProbeProvider',
    'PhysicalPartRenderProbeProvider'
)) {
    if ($metadata.IndexOf($token, [StringComparison]::Ordinal) -lt 0) {
        throw "physical render metadata probe contract token is missing: $token"
    }
}

foreach ($token in @(
    'PhysicalResistorPartProbeTarget',
    'PhysicalDiodePartProbeTarget',
    'PhysicalLedPartProbeTarget'
)) {
    if ($probeProviders.IndexOf($token, [StringComparison]::Ordinal) -lt 0) {
        throw "typed loose probe provider token is missing: $token"
    }
}

Write-Output 'PASS:renderer-provider-boundary'
