# Shared, read-only schema for the compiled public Remove falsifier. The caller
# owns the browser, source provenance, anchored Java control and final cleanup.
function Assert-Task43PPublicRemoveObject($Value, [string]$Path, [string[]]$Fields) {
    if ($null -eq $Value -or $Value -isnot [pscustomobject] -or $Value -is [array]) {
        Throw-VerifierInfrastructure "Public Remove evidence was not an object at $Path."
    }
    foreach ($property in $Value.PSObject.Properties) {
        if ($Fields -cnotcontains $property.Name) {
            Throw-VerifierInfrastructure "Public Remove evidence had unknown field $Path.$($property.Name)."
        }
    }
    foreach ($field in $Fields) {
        if ($null -eq $Value.PSObject.Properties[$field]) {
            Throw-VerifierInfrastructure "Public Remove evidence omitted $Path.$field."
        }
    }
}

function Assert-Task43PPublicRemoveExact($Value, $Expected, [string]$Path) {
    if ($null -eq $Value -or $Value.GetType() -ne $Expected.GetType() -or $Value -cne $Expected) {
        Throw-VerifierInfrastructure "Public Remove evidence contradicted $Path."
    }
}

function Assert-Task43PPublicRemoveState($State, [string]$Stage) {
    Assert-Task43PPublicRemoveObject $State $Stage @('powerOff', 'selectedInstalledR1',
        'emptyTray', 'removedOriginalVisible', 'removeButtonCount',
        'removeButtonDisabled', 'removeButtonVisible', 'removeButtonHitTest',
        'liftLeadEnabled')
    if (-not (Test-VerifierStrictIntegralValue $State.removeButtonCount 1L 1L)) {
        Throw-VerifierInfrastructure "Public Remove $Stage did not find one exact action button."
    }
    foreach ($field in @('powerOff', 'selectedInstalledR1', 'emptyTray',
            'removeButtonDisabled', 'removeButtonVisible', 'removeButtonHitTest',
            'liftLeadEnabled')) {
        Assert-Task43PPublicRemoveExact $State.$field $true ($Stage + '.' + $field)
    }
    Assert-Task43PPublicRemoveExact $State.removedOriginalVisible $false ($Stage + '.removedOriginalVisible')
}

function Assert-Task43PPublicRemoveEvidence($Value, [string]$RunId, [string]$RouteId,
        [string]$Nonce, [string]$RequestId, [string]$ExecutionDigest) {
    Assert-Task43PPublicRemoveObject $Value 'publicRemove' @('protocol',
        'runId', 'routeId', 'navigationMarker', 'normalPlayerUrl', 'inputMethod', 'inputEventCount',
        'directControllerPositive', 'before', 'after')
    Assert-Task43PPublicRemoveExact $Value.protocol 'TSJ-TASK43P-PUBLIC-REMOVE-1' 'protocol'
    Assert-Task43PPublicRemoveExact $Value.runId $RunId 'runId'
    Assert-Task43PPublicRemoveExact $Value.routeId $RouteId 'routeId'
    Assert-Task43PPublicRemoveExact $Value.inputMethod 'Input.dispatchMouseEvent' 'inputMethod'
    if (-not (Test-VerifierStrictIntegralValue $Value.inputEventCount 2L 2L)) {
        Throw-VerifierInfrastructure 'Public Remove evidence omitted the exact two real mouse events.'
    }
    if ($Value.normalPlayerUrl -isnot [string]) {
        Throw-VerifierInfrastructure 'Public Remove evidence omitted its exact normal-player URL.'
    }
    if ($Value.navigationMarker -isnot [string] -or $Value.navigationMarker -cnotmatch '^[0-9a-f]{32}$') {
        Throw-VerifierInfrastructure 'Public Remove evidence omitted its exact navigation marker.'
    }
    $url = $null
    if (-not [Uri]::TryCreate($Value.normalPlayerUrl, [UriKind]::Absolute, [ref]$url) -or
            $url.Scheme -cne 'http' -or $url.Host -cne '127.0.0.1' -or
            $url.AbsolutePath -cne '/circuitjs.html' -or $url.UserInfo -ne '' -or
            $url.Fragment -ne '') {
        Throw-VerifierInfrastructure 'Public Remove evidence used an unexpected player route.'
    }
    $expectedQuery = @('tsjChallenge=led', 'seed=3', 'tsjVerifyGeometry=true',
        ('tsjVerifierRun=' + $RunId), ('tsjVerifierRoute=' + $RouteId),
        ('tsjVerifierNavigation=' + $Value.navigationMarker)) | Sort-Object
    $actualQuery = $url.Query.TrimStart('?').Split('&') | Sort-Object
    if (($actualQuery -join '&') -cne ($expectedQuery -join '&')) {
        Throw-VerifierInfrastructure 'Public Remove evidence used a developer or mismatched player query.'
    }
    $direct = $Value.directControllerPositive
    Assert-Task43PPublicRemoveObject $direct 'directControllerPositive' @('protocol',
        'method', 'componentId', 'family', 'seed', 'partId', 'availableBefore',
        'dispatchReturned', 'removedAfter', 'cleanupDispatchReturned', 'restoredAfter',
        'ownerRestored', 'runId', 'routeId', 'nonce', 'requestId', 'executionDigest')
    foreach ($pair in @(
            @('protocol', 'TSJ-TASK43P-PUBLIC-REMOVE-CONTROL-1'),
            @('method', 'PcbWorkbenchController.dispatch'), @('componentId', 'R1'),
            @('family', 'LED_INDICATOR'), @('partId', 'R1_ORIGINAL'),
            @('runId', $RunId), @('routeId', $RouteId), @('nonce', $Nonce),
            @('requestId', $RequestId), @('executionDigest', $ExecutionDigest))) {
        Assert-Task43PPublicRemoveExact $direct.($pair[0]) $pair[1] ('direct.' + $pair[0])
    }
    if (-not (Test-VerifierStrictIntegralValue $direct.seed 3L 3L)) {
        Throw-VerifierInfrastructure 'Public Remove direct control used a different seed.'
    }
    foreach ($field in @('availableBefore', 'dispatchReturned', 'removedAfter',
            'cleanupDispatchReturned', 'restoredAfter', 'ownerRestored')) {
        Assert-Task43PPublicRemoveExact $direct.$field $true ('direct.' + $field)
    }
    foreach ($stage in @('before', 'after')) {
        Assert-Task43PPublicRemoveState $Value.$stage $stage
    }
}
