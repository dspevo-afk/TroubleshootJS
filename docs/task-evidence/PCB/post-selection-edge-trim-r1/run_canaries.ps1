[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repo=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$registry=Join-Path $repo 'scripts/verify-current-contracts.ps1'
$original=[IO.File]::ReadAllBytes($registry)
$originalHash=(Get-FileHash -LiteralPath $registry -Algorithm SHA256).Hash
$expected=(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'original-source.json') -Raw | ConvertFrom-Json).files |
    Where-Object {$_.path -eq 'scripts/verify-current-contracts.ps1'}
if($originalHash -ne $expected.sha256){throw 'Original registry boundary changed'}
$names=@('PcbPostSelectionEdgeTrimTargetContractTest','PcbPostSelectionEdgeTrimCopyContractTest')
$owned=@();$candidateHash=$null;$resultCode=1
Push-Location $repo
try {
    foreach($name in $names){
        $archive=Join-Path $PSScriptRoot ($name+'.java')
        $active=Join-Path $repo ('tests/contracts/'+$name+'.java')
        if(Test-Path -LiteralPath $active){throw 'Unexpected active canary'}
        [IO.File]::WriteAllBytes($active,[IO.File]::ReadAllBytes($archive))
        $owned+=@{path=$active;hash=(Get-FileHash -LiteralPath $active -Algorithm SHA256).Hash}
    }
    $text=[Text.Encoding]::UTF8.GetString($original)
    $eol=if($text.Contains("`r`n")){"`r`n"}else{"`n"}
    $anchor="        @{ Name = 'PcbCanvasOwnerBoundsContractTest';"
    if($text.Split(@($anchor),[StringSplitOptions]::None).Count -ne 2){throw 'Registry anchor'}
    $entries="        @{ Name = 'PcbPostSelectionEdgeTrimTargetContractTest'; Marker = 'PCB post-selection edge trim target ' },"+$eol+
        "        @{ Name = 'PcbPostSelectionEdgeTrimCopyContractTest'; Marker = 'PCB post-selection edge trim copy ' },"+$eol
    [IO.File]::WriteAllBytes($registry,[Text.Encoding]::UTF8.GetBytes($text.Replace($anchor,$entries+$anchor)))
    $candidateHash=(Get-FileHash -LiteralPath $registry -Algorithm SHA256).Hash
    & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $names -ReceiptOutputPath (Join-Path $PSScriptRoot 'canary-receipt.json')
    if($?){$resultCode=0}
} finally {
    if($candidateHash -and (Get-FileHash -LiteralPath $registry -Algorithm SHA256).Hash -ne $candidateHash){throw 'Canary registry changed before cleanup'}
    [IO.File]::WriteAllBytes($registry,$original)
    foreach($row in $owned){
        if((Get-FileHash -LiteralPath $row.path -Algorithm SHA256).Hash -ne $row.hash){throw 'Owned canary changed'}
        Remove-Item -LiteralPath $row.path
    }
    Pop-Location
    Write-Output 'CANARY_CLEANUP: original registry restored and two verified task-only canaries retired; production trial remains.'
}
exit $resultCode
