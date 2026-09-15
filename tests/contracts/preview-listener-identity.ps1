[CmdletBinding()]
param([switch]$Live, [string]$OutputPath = '')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $root 'scripts/VerifierIsolation.psm1') -Force
$isolationModule = Get-Module VerifierIsolation
# Exercise private boundaries in their real module scope without exporting them.
function Test-VerifierSameRootPreviewListenerEligibility {
    & $script:isolationModule { Test-VerifierSameRootPreviewListenerEligibility @args } @args
}
function Get-VerifierPortLeaseBoundOwnershipProof {
    & $script:isolationModule { Get-VerifierPortLeaseBoundOwnershipProof @args } @args
}
function Test-VerifierLiveListenerInspectionAuthorization {
    & $script:isolationModule { Test-VerifierLiveListenerInspectionAuthorization @args } @args
}
$checks = 0
$observations = [Collections.Generic.List[object]]::new()
function Assert-Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAIL preview-listener-identity: $Message" }
    $script:checks++
}
$process = Get-Process -Id $PID
$owner = [pscustomobject]@{
    ProcessId = $PID; ProcessStartTicks = $process.StartTime.ToUniversalTime().Ticks
    ProcessParentProcessId = 1; ProcessParentProcessStartTicks = 1L
    ProcessCommandLine = 'preview-canary'; Script = 'preview.ps1'; Port = 54321
    RunId = 'preview-canary'; Nonce = 'nonce-canary'; Owner = 'run'
    IdentityVerified = $true; Process = $process
}
$context = [pscustomobject]@{Server=$owner;RunId=$owner.RunId;PreviewNonce=$owner.Nonce}
$listener = [pscustomobject]@{ListenerOwnerKind='user-process';Port=$owner.Port;
    ProcessId=$owner.ProcessId;ProcessStartTicks=$owner.ProcessStartTicks}
$inspection = [pscustomobject]@{ListenerOwnerKind='user-process';Listeners=@($listener)}
function Test-Eligible {
    Test-VerifierSameRootPreviewListenerEligibility $context $inspection @($listener) `
        $owner $owner.ProcessId $owner.ProcessStartTicks $owner
}
Assert-Check (Test-Eligible) 'Complete same-root preview was not eligible.'
foreach ($case in @(
    @('Owner','caller'), @('IdentityVerified',$false), @('IdentityVerified','true'),
    @('Process',$null), @('ProcessId','1'), @('ProcessStartTicks',0L),
    @('ProcessParentProcessId',0), @('ProcessParentProcessStartTicks','1'),
    @('ProcessCommandLine',''), @('Script',''), @('RunId','foreign'), @('Nonce','foreign')
)) {
    $name=$case[0]; $saved=$owner.$name
    try { $owner.$name=$case[1]; Assert-Check (-not (Test-Eligible)) "Malformed owner $name accepted." }
    finally { $owner.$name=$saved }
}
foreach ($case in @(@('Port',54322), @('ProcessId',($PID+1)),
        @('ProcessStartTicks',($owner.ProcessStartTicks+1)), @('ProcessId','1'),
        @('ListenerOwnerKind','kernel-transport'))) {
    $name=$case[0]; $saved=$listener.$name
    try { $listener.$name=$case[1]; Assert-Check (-not (Test-Eligible)) "Foreign listener $name accepted." }
    finally { $listener.$name=$saved }
}
foreach ($name in @('Profile','DirectProcessOwner')) {
    try { Add-Member -InputObject $owner -NotePropertyName $name -NotePropertyValue 'foreign'
        Assert-Check (-not (Test-Eligible)) "Non-preview owner $name accepted." }
    finally { $owner.PSObject.Properties.Remove($name) }
}
$other=$owner.PSObject.Copy()
Assert-Check (-not (Test-VerifierSameRootPreviewListenerEligibility $context $inspection `
    @($listener) $other $owner.ProcessId $owner.ProcessStartTicks $other)) 'Copied owner accepted.'
Assert-Check (-not (Test-VerifierSameRootPreviewListenerEligibility $context $inspection `
    @() $owner $owner.ProcessId $owner.ProcessStartTicks $owner)) 'Empty listener set accepted.'
Assert-Check (Test-Eligible) 'Negative canaries changed the healthy fixture.'
$cleanup=$null
if ($Live) {
    $context=$null
    try {
        $context=New-VerifierRunContext $root ''
        [void](Start-VerifierOwnedPreview $context (Join-Path $root 'scripts/preview.ps1') 90 $true)
        $owner=$context.Server
        # Real empty/partial requests that disappear before a response. These
        # reproduce the TcpClient.GetStream/write startup race, not a DOM mock.
        for ($i=0;$i -lt 12;$i++) {
            $client=[Net.Sockets.TcpClient]::new()
            try {
                $client.Connect([Net.IPAddress]::Loopback,[int]$owner.Port)
                if ($i % 2) {
                    $bytes=[Text.Encoding]::ASCII.GetBytes("GET /circuitjs.html HTTP/1.1`r`n")
                    $client.GetStream().Write($bytes,0,$bytes.Length)
                }
                $client.Client.LingerState=[Net.Sockets.LingerOption]::new($true,0)
            } finally { $client.Close() }
        }
        $response=Invoke-WebRequest -UseBasicParsing -Uri ($owner.BaseUrl+'/__tsj/verify-identity') -TimeoutSec 10
        $afterAbort=$response.Content | ConvertFrom-Json
        Assert-Check ($response.StatusCode -eq 200 -and $afterAbort.processId -eq $owner.ProcessId -and
            $afterAbort.processStartTicks -eq $owner.ProcessStartTicks -and
            $afterAbort.verifierRunId -ceq $context.RunId) 'Disconnected clients killed or replaced the live preview.'
        for ($i=0;$i -lt 3;$i++) {
            $proof=Get-VerifierPortLeaseBoundOwnershipProof $context $owner.Lease `
                $owner.ProcessId $owner.ProcessStartTicks $owner $owner
            Assert-Check ($proof.ProofStage -ceq 'preview-root-listener-scoped-identity') 'Real preview used a broad census.'
            Assert-Check ($proof.ProofElapsedMilliseconds -lt 500) 'Real proof exceeded unchanged deadline.'
            $inspection=$proof.Inspection
            Assert-Check (Test-VerifierLiveListenerInspectionAuthorization $inspection $context $owner `
                $owner.ProcessId $owner.ProcessStartTicks) 'Real downstream authorization failed.'
            $observations.Add([ordered]@{stage=$proof.ProofStage;elapsedMs=$proof.ProofElapsedMilliseconds})
        }
        foreach ($case in @(@('ProcessCommandLine','wrong-command'), @('Nonce','wrong-nonce'),
                @('ProcessParentProcessStartTicks',($owner.ProcessParentProcessStartTicks+1)),
                @('ProcessStartTicks',($owner.ProcessStartTicks+1)))) {
            $name=$case[0];$saved=$owner.$name;$accepted=$false
            try {
                $owner.$name=$case[1]
                try { $accepted=Test-VerifierLiveListenerInspectionAuthorization $inspection $context $owner `
                    $owner.ProcessId $owner.ProcessStartTicks } catch { $accepted=$false }
                Assert-Check (-not $accepted) "Real mismatched $name authorized."
            } finally { $owner.$name=$saved }
        }
        $breakpoint=$null
        try {
            $breakpoint=Set-PSBreakpoint -Command Get-VerifierCurrentProcessIdentity -Action { Start-Sleep -Milliseconds 600 }
            Assert-Check (-not (Test-VerifierLiveListenerInspectionAuthorization $inspection $context $owner `
                $owner.ProcessId $owner.ProcessStartTicks)) 'Slow identity proof reset or ignored its deadline.'
        } finally { if ($null -ne $breakpoint) { Remove-PSBreakpoint -Breakpoint $breakpoint } }
        Assert-Check (Test-VerifierLiveListenerInspectionAuthorization $inspection $context $owner `
            $owner.ProcessId $owner.ProcessStartTicks) 'Real preview did not recover after negative canaries.'
    } finally {
        if ($null -ne $context) { $cleanup=Complete-VerifierRun $context }
    }
    Assert-Check ($null -ne $cleanup -and $cleanup.Success) 'Exact live cleanup failed.'
}
$result=[ordered]@{protocol='troubleshootjs-preview-listener-identity-v1';status='PASS';
    checks=$checks;live=[bool]$Live;proofs=$observations.ToArray();
    ownershipBudgetMs=500;cleanup=if($Live){[bool]$cleanup.Success}else{$null}}
if ($OutputPath) {
    [IO.File]::WriteAllText([IO.Path]::GetFullPath($OutputPath),
        ($result | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
}
$result | ConvertTo-Json -Depth 8

Write-Output ("PASS: preview listener identity contracts assertions=" + $checks + " live=" + [bool]$Live)
