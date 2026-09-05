# The historical Task43P baseline is evidence metadata, not the commit a new
# invocation must check out. Candidate identity belongs to the wrapper's Git
# checkout; disposable source/build trees retain their separate byte provenance.
function Get-Task43PCandidateSha([string]$RepositoryRoot, $ExpectedCandidateSha = $null) {
    $safeRoot = $RepositoryRoot.Replace('\', '/')
    $head = (& git -c "safe.directory=$safeRoot" -C $RepositoryRoot rev-parse HEAD 2>$null |
        Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -cnotmatch '^[0-9a-f]{40}$') {
        Throw-VerifierInfrastructure 'Task43P could not freeze the actual candidate HEAD.'
    }
    if ($null -ne $ExpectedCandidateSha -and
            ($ExpectedCandidateSha -isnot [string] -or
                $ExpectedCandidateSha -cnotmatch '^[0-9a-f]{40}$' -or
                $ExpectedCandidateSha -cne $head)) {
        Throw-VerifierInfrastructure 'Task43P actual candidate HEAD did not match the explicit expected candidate SHA.'
    }
    return $head
}

function Test-Task43PCandidateRepositoryState([string]$CandidateSha, $Frozen, $Current) {
    if ($CandidateSha -cnotmatch '^[0-9a-f]{40}$' -or
            $null -eq $Frozen -or $null -eq $Current -or
            $Frozen.headSha -cne $CandidateSha) { return $false }
    foreach ($field in @('headSha', 'sourceVerifierDigest', 'sourceVerifierFileCount',
            'dirty', 'statusText')) {
        if ($Frozen.$field -cne $Current.$field) { return $false }
    }
    return $true
}
