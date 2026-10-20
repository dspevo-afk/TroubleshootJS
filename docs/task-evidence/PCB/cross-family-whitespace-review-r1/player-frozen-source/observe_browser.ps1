[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('before','preclose','after')][string]$Side,
      [Parameter(Mandatory=$true)][string]$PrivateRoot)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$metaPath=Join-Path $PrivateRoot 'browser.json'
$meta=Get-Content -LiteralPath $metaPath -Raw|ConvertFrom-Json
if([string]::IsNullOrWhiteSpace($meta.profileDirectory) -or
   -not [IO.Path]::IsPathRooted($meta.profileDirectory) -or
   [IO.Path]::GetFileName($meta.profileDirectory) -notlike 'playwright_chromiumdev_profile-*'){
    throw 'Require the exact fresh launchServer profile path'
}
$target=Join-Path $PSScriptRoot ('browser-host-'+$Side+'.json')
if(Test-Path -LiteralPath $target){throw 'Preserve prior browser observation'}
$beforePath=Join-Path $PrivateRoot 'browser-host-before.json'
if($Side -eq 'after'){
    $before=Get-Content -LiteralPath $beforePath -Raw|ConvertFrom-Json
    $p=Get-Process -Id $before.pid -ErrorAction SilentlyContinue
    $exited=$null -eq $p -or [string]$p.StartTime.ToUniversalTime().Ticks -cne [string]$before.startTicks
    if(-not $exited){throw 'Recorded browser birth remains live'}
    $profiles=@(Get-CimInstance Win32_Process|Where-Object {$null -ne $_.CommandLine -and $_.CommandLine.IndexOf($meta.profileDirectory,[StringComparison]::OrdinalIgnoreCase) -ge 0})
    if($profiles.Count -ne 0){throw 'Owned browser profile has live process'}
    if(Test-Path -LiteralPath $meta.profileDirectory){throw 'Owned API profile cleanup is incomplete; preserve it'}
    @{status='PASS';pid=$before.pid;startTicks=$before.startTicks;recordedBirthExited=$true;profileAbsent=$true;profileProcessesAbsent=$true;limits='Fresh owned profile only; four earlier blocked profiles untouched'}|ConvertTo-Json|Set-Content -LiteralPath $target
}else{
    $p=Get-Process -Id $meta.pid -ErrorAction Stop
    $cim=Get-CimInstance Win32_Process -Filter ('ProcessId='+$meta.pid)
    if([IO.Path]::GetFullPath($cim.ExecutablePath) -ine [IO.Path]::GetFullPath($meta.executablePath)){throw 'Browser executable differs'}
    if($cim.CommandLine.IndexOf($meta.profileDirectory,[StringComparison]::OrdinalIgnoreCase) -lt 0 -or
       $cim.CommandLine -match '--no-sandbox|--headless'){throw 'Browser profile/sandbox/visibility differs'}
    if($Side -eq 'before'){
        if(Test-Path -LiteralPath $beforePath){throw 'Preserve previous private browser identity'}
        @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$cim.ExecutablePath;commandLine=$cim.CommandLine;profileDirectory=$meta.profileDirectory}|ConvertTo-Json|Set-Content -LiteralPath $beforePath
    }else{
        $before=Get-Content -LiteralPath $beforePath -Raw|ConvertFrom-Json
        if([string]$p.StartTime.ToUniversalTime().Ticks -cne [string]$before.startTicks -or $cim.CommandLine -cne $before.commandLine){throw 'Browser birth or command changed'}
    }
    @{status='PASS';pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=[IO.Path]::GetFileName($cim.ExecutablePath);resolvedExecutableMatch=$true;profileArgumentMatched=$true;sandboxEnabled=$true;headless=$false;privateMetadataSha256=(Get-FileHash -LiteralPath $metaPath -Algorithm SHA256).Hash.ToLowerInvariant()}|ConvertTo-Json|Set-Content -LiteralPath $target
}
Write-Output ('PASS fresh browser host identity '+$Side)
