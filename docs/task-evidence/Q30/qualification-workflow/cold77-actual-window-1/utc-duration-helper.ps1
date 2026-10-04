function Get-Q30UtcElapsedSeconds {
    param([Parameter(Mandatory=$true)][string]$StartedUtc,
          [Parameter(Mandatory=$true)][string]$FinishedUtc)
    $q30DurationStart = [DateTimeOffset]::Parse($StartedUtc).ToUniversalTime()
    $q30DurationFinish = [DateTimeOffset]::Parse($FinishedUtc).ToUniversalTime()
    return ($q30DurationFinish - $q30DurationStart).TotalSeconds
}
