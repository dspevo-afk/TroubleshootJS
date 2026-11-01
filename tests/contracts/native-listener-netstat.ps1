[CmdletBinding()]
param(
   [string]$RepositoryRoot = '',
   [string]$ModulePath = ''
)

$ErrorActionPreference = 'Stop'
$assertions = 0

function Assert-True([bool]$Condition, [string]$Message) {
    $script:assertions++
    if (-not $Condition) { throw ('ASSERTION FAILED: ' + $Message) }
}

function Get-Sha256Lower([string]$Path) {
    $bytes = [IO.File]::ReadAllBytes($Path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

$rootInput = if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    Join-Path $PSScriptRoot '..\..'
} else { $RepositoryRoot }
$rootItem = Resolve-Path -LiteralPath $rootInput -ErrorAction Stop
if ($rootItem.Provider.Name -cne 'FileSystem') {
    throw 'RepositoryRoot must resolve to a filesystem directory.'
}
$resolvedRepositoryRoot = [IO.Path]::GetFullPath($rootItem.ProviderPath)
$rootDirectoryItem = Get-Item -LiteralPath $resolvedRepositoryRoot -Force -ErrorAction Stop
if (-not $rootDirectoryItem.PSIsContainer -or
        ($rootDirectoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'RepositoryRoot must be a non-reparse filesystem directory.'
}
$moduleInput = if ([string]::IsNullOrWhiteSpace($ModulePath)) {
    Join-Path $resolvedRepositoryRoot 'scripts\VerifierIsolation.psm1'
} elseif ([IO.Path]::IsPathRooted($ModulePath)) {
    $ModulePath
} else {
    Join-Path $resolvedRepositoryRoot $ModulePath
}
$moduleItem = Resolve-Path -LiteralPath $moduleInput -ErrorAction Stop
if ($moduleItem.Provider.Name -cne 'FileSystem') {
    throw 'ModulePath must resolve to a filesystem file.'
}
$resolvedModulePath = [IO.Path]::GetFullPath($moduleItem.ProviderPath)
$rootPrefix = $resolvedRepositoryRoot.TrimEnd([IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not (Test-Path -LiteralPath $resolvedModulePath -PathType Leaf) -or
        -not $resolvedModulePath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'ModulePath must resolve to a file inside RepositoryRoot.'
}
$walkPath = $resolvedModulePath
while ($walkPath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    $walkItem = Get-Item -LiteralPath $walkPath -Force -ErrorAction Stop
    if (($walkItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'RepositoryRoot and ModulePath may not traverse a reparse point.'
    }
    $walkPath = Split-Path -Parent $walkPath
    if ([string]::IsNullOrWhiteSpace($walkPath)) {
        throw 'ModulePath escaped RepositoryRoot during physical path validation.'
    }
}
if ($walkPath -cne $resolvedRepositoryRoot) {
    throw 'ModulePath physical ancestors did not terminate at RepositoryRoot.'
}
$modulePathRelative = $resolvedModulePath.Substring($rootPrefix.Length).Replace('\', '/')
$actualModuleSha256 = Get-Sha256Lower $resolvedModulePath
$moduleProvenanceValid = ($modulePathRelative.Length -gt 0 -and
    $actualModuleSha256 -cmatch '^[0-9a-f]{64}$')

Assert-True ($PSVersionTable.PSEdition -ceq 'Desktop' -and
    $PSVersionTable.PSVersion.Major -eq 5) 'Windows PowerShell 5 Desktop host'
Assert-True $moduleProvenanceValid 'actual module path and SHA-256 provenance captured'
Write-Output ('MODULE_PROVENANCE path=' + $modulePathRelative + ' sha256=' + $actualModuleSha256)
$module = Import-Module $resolvedModulePath -Force -DisableNameChecking -PassThru -ErrorAction Stop

$report = & $module {
    $script:netstatR2RecordCount = 0
    $script:netstatR2GetNetCalls = 0
    $script:netstatR2BoundedCalls = New-Object Collections.ArrayList
    $script:netstatR2RouteOutput = @(
        'Active Connections',
        'Proto Local Address          Foreign Address        State           PID',
        'UDP   0.0.0.0:5353           *:*                                    1201',
        'UDP   [::]:5353              *:*                                    1202',
        'TCP   127.0.0.1:8081         0.0.0.0:0              LISTENING       811'
    ) -join ([string][char]13 + [string][char]10)

    function Get-VerifierListenerProcessRecord($ProcessId, $LocalAddress, $Port, $Source,
            $PreviewContext = $null, $PreviewOwner = $null) {
        $script:netstatR2RecordCount++
        return [pscustomobject]@{
            LocalAddress = $LocalAddress
            Port = [int]$Port
            ProcessId = [int]$ProcessId
            ProcessStartTicks = 987654321L
            ListenerOwnerKind = $script:VerifierUserProcessOwnerKind
            ListenerOwnerProof = $script:VerifierUserProcessOwnerProof
            ListenerOwnerEvidence = $script:VerifierUserProcessOwnerEvidence
            Source = $Source
        }
    }
    function Get-NetTCPConnection {
        [CmdletBinding()]
        param([int]$LocalPort, [string]$State)
        $script:netstatR2GetNetCalls++
        return @()
    }
    function netstat.exe { }
    function Invoke-VerifierBoundedProcess([string]$Executable, [object[]]$Arguments,
            [int]$TimeoutMilliseconds) {
        [void]$script:netstatR2BoundedCalls.Add([pscustomobject]@{
            Executable = $Executable
            Arguments = [object[]]@($Arguments)
            TimeoutMilliseconds = $TimeoutMilliseconds
        })
        return [pscustomobject]@{
            Stdout = [string]$script:netstatR2RouteOutput
            Stderr = ''
            ExitCode = 0
        }
    }
    function New-NetstatR2Record([string]$Address, [int]$ProcessId, [string]$Source) {
        return [pscustomobject]@{
            LocalAddress = $Address
            Port = 8081
            ProcessId = $ProcessId
            ProcessStartTicks = 987654321L
            ListenerOwnerKind = $script:VerifierUserProcessOwnerKind
            ListenerOwnerProof = $script:VerifierUserProcessOwnerProof
            ListenerOwnerEvidence = $script:VerifierUserProcessOwnerEvidence
            Source = $Source
        }
    }
    function Test-NetstatR2ParserThrows([object[]]$Lines, [int]$ExitCode = 0) {
        try {
            [void](Parse-VerifierNetstatListenerOutput 8081 $Lines $ExitCode)
            return $false
        } catch {
            return $true
        }
    }

    $header = @(
        'Active Connections',
        'Proto Local Address          Foreign Address        State           PID'
    )
    $udpOnlyLines = @($header) + @(
        'UDP   0.0.0.0:5353           *:*                                    1201',
        'UDP   [::]:5353              *:*                                    1202'
    )
    $udpRecordStart = [int]$script:netstatR2RecordCount
    $udpOnly = Parse-VerifierNetstatListenerOutput 8081 $udpOnlyLines
    $udpOnlyUdpRecordCount = [int]$script:netstatR2RecordCount - $udpRecordStart

    $connectedUdpOnlyLines = @($header) + @(
        'UDP   0.0.0.0:52442          [2001:db8::25]:443                      1201',
        'UDP   [::]:52443             192.0.2.25:443                          1202'
    )
    $connectedUdpRecordStart = [int]$script:netstatR2RecordCount
    $connectedUdpOnly = Parse-VerifierNetstatListenerOutput 8081 $connectedUdpOnlyLines
    $connectedUdpRecordCount = [int]$script:netstatR2RecordCount - $connectedUdpRecordStart

    $mixedLines = @($header) + @(
        'UDP   0.0.0.0:5353           *:*                                    1201',
        'UDP   [::]:5353              *:*                                    1202',
        'TCP   127.0.0.1:9090         0.0.0.0:0              LISTENING       901',
        'TCP   [::1]:8081             [::]:0                 LISTENING       811'
    )
    $mixedStart = [int]$script:netstatR2RecordCount
    $mixed = Parse-VerifierNetstatListenerOutput 8081 $mixedLines
    $mixedCallCount = [int]$script:netstatR2RecordCount - $mixedStart

    $dualStackLines = @($header) + @(
        'TCP   [::1]:8081             [::]:0                 LISTENING       811',
        'TCP   [::]:8081              [::]:0                 LISTENING       812'
    )
    $netstatDual = Parse-VerifierNetstatListenerOutput 8081 $dualStackLines
    $nativeOne = New-NetstatR2Record '::1' 811 'GetExtendedTcpTable'
    $nativeTwo = New-NetstatR2Record '::' 812 'GetExtendedTcpTable'
    $nativeDual = New-VerifierListenerInspection $true $true @($nativeOne, $nativeTwo) 'GetExtendedTcpTable' '' $null $null $null -StructuralOnly
    $netstatTuples = @($netstatDual.Listeners | ForEach-Object {
        $_.LocalAddress + '|' + $_.Port + '|' + $_.ProcessId + '|' + $_.ProcessStartTicks
    } | Sort-Object)
    $nativeTuples = @($nativeDual.Listeners | ForEach-Object {
        $_.LocalAddress + '|' + $_.Port + '|' + $_.ProcessId + '|' + $_.ProcessStartTicks
    } | Sort-Object)

    $malformedUdpPid = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   0.0.0.0:5353           *:*                                    not-a-pid'
    ))
    $malformedUdpForeign = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   0.0.0.0:5353           0.0.0.0:0                              1201'
    ))
    $malformedUdpRange = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   0.0.0.0:65536          *:*                                    1201'
    ))
    $malformedUdpAddress = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   [not-an-ip]:5353      *:*                                    1201'
    ))
    $headerlessConnectedUdpOnly = Test-NetstatR2ParserThrows @(
        'UDP   0.0.0.0:52442          [2001:db8::25]:443                      1201',
        'UDP   [::]:52443             192.0.2.25:443                          1202'
    )
    $malformedUdpForeignHost = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   0.0.0.0:5353           remote.example:443                      1201'
    ))
    $malformedUdpForeignPortRange = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   0.0.0.0:5353           192.0.2.25:65536                       1201'
    ))
    $malformedUdpForeignPortSpelling = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   0.0.0.0:5353           192.0.2.25:0443                        1201'
    ))
    $malformedUdpForeignAddress = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   0.0.0.0:5353           999.0.0.1:443                          1201'
    ))
    $malformedUdpForeignAbbreviatedIpv4 = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   0.0.0.0:5353           127.1:443                              1201'
    ))
    $malformedUdpForeignBracketedIpv4 = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   0.0.0.0:5353           [192.0.2.25]:443                       1201'
    ))
    $malformedUdpForeignUnbracketedIpv6 = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   0.0.0.0:5353           2001:db8::25:443                       1201'
    ))
    $headerlessValidUdpOnly = Test-NetstatR2ParserThrows @(
        'UDP   0.0.0.0:5353           *:*                                    1201'
    )
    $abbreviatedIpv4Udp = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   127.1:5353             *:*                                    1201'
    ))
    $leadingZeroIpv4Udp = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   127.000.000.001:5353   *:*                                    1201'
    ))
    $outOfRangeIpv4Udp = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   999.0.0.1:5353         *:*                                    1201'
    ))
    $bracketedIpv4Udp = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   [127.0.0.1]:5353      *:*                                    1201'
    ))
    $unbracketedIpv6Udp = Test-NetstatR2ParserThrows (@($header) + @(
        'UDP   ::1:5353               *:*                                    1201'
    ))
    $unknownTcp = Test-NetstatR2ParserThrows (@($header) + @('TCP malformed'))
    $errorText = Test-NetstatR2ParserThrows (@($header) + @('netstat: error: fixture'))
    $nonzeroExit = Test-NetstatR2ParserThrows $header 1
    $headerOnly = Parse-VerifierNetstatListenerOutput 8081 $header
    $emptyOutput = Test-NetstatR2ParserThrows @()

    $defaultCallStart = [int]$script:netstatR2GetNetCalls
    $defaultRecordStart = [int]$script:netstatR2RecordCount
    $boundedStart = [int]$script:netstatR2BoundedCalls.Count
    $defaultRoute = Get-VerifierLoopbackListenerRecords 8081
    $defaultGetNetCalls = [int]$script:netstatR2GetNetCalls - $defaultCallStart
    $defaultRecordCount = [int]$script:netstatR2RecordCount - $defaultRecordStart
    $defaultBoundedCalls = [int]$script:netstatR2BoundedCalls.Count - $boundedStart

    $forcedCallStart = [int]$script:netstatR2GetNetCalls
    $forcedRecordStart = [int]$script:netstatR2RecordCount
    $forcedBoundedStart = [int]$script:netstatR2BoundedCalls.Count
    $forcedRoute = Get-VerifierLoopbackListenerRecords 8081 -PreferNetstat
    $forcedGetNetCalls = [int]$script:netstatR2GetNetCalls - $forcedCallStart
    $forcedRecordCount = [int]$script:netstatR2RecordCount - $forcedRecordStart
    $forcedBoundedCalls = @($script:netstatR2BoundedCalls | Select-Object -Skip $forcedBoundedStart)
    $forcedArgs = @($forcedBoundedCalls[0].Arguments)

    return [pscustomobject]@{
        UdpOnlyKnownEmpty = [bool]($udpOnly.Success -and $udpOnly.Known -and
            -not $udpOnly.HasListeners -and $udpOnly.Listeners.Count -eq 0)
        UdpRowsCreatedNoOwnerRecords = [bool]($udpOnlyUdpRecordCount -eq 0)
        ConnectedUdpOnlyKnownEmpty = [bool]($connectedUdpOnly.Success -and $connectedUdpOnly.Known -and
            -not $connectedUdpOnly.HasListeners -and $connectedUdpOnly.Listeners.Count -eq 0)
        ConnectedUdpRowsCreatedNoOwnerRecords = [bool]($connectedUdpRecordCount -eq 0)
        MixedHasOnlyTargetTcp = [bool]($mixed.Success -and $mixed.Known -and
            $mixed.Listeners.Count -eq 1 -and $mixed.Listeners[0].LocalAddress -ceq '::1' -and
            $mixed.Listeners[0].Port -eq 8081 -and $mixed.Listeners[0].ProcessId -eq 811)
        MixedOwnerRecordCount = $mixedCallCount
        DualStackCanonicalAddresses = [bool]($netstatDual.Listeners.Count -eq 2 -and
            $netstatDual.Listeners[0].LocalAddress -in @('::1', '::') -and
            $netstatDual.Listeners[1].LocalAddress -in @('::1', '::') -and
            @($netstatDual.Listeners | Where-Object { $_.LocalAddress -ceq '::1' }).Count -eq 1 -and
            @($netstatDual.Listeners | Where-Object { $_.LocalAddress -ceq '::' }).Count -eq 1)
        NativeAndNetstatExactTupleSetEqual = [bool](@(Compare-Object $nativeTuples $netstatTuples -CaseSensitive).Count -eq 0)
        MalformedUdpPidRejected = [bool]$malformedUdpPid
        MalformedUdpForeignRejected = [bool]$malformedUdpForeign
        MalformedUdpRangeRejected = [bool]$malformedUdpRange
        MalformedUdpAddressRejected = [bool]$malformedUdpAddress
        HeaderlessConnectedUdpOnlyRejected = [bool]$headerlessConnectedUdpOnly
        MalformedUdpForeignHostRejected = [bool]$malformedUdpForeignHost
        MalformedUdpForeignPortRangeRejected = [bool]$malformedUdpForeignPortRange
        MalformedUdpForeignPortSpellingRejected = [bool]$malformedUdpForeignPortSpelling
        MalformedUdpForeignAddressRejected = [bool]$malformedUdpForeignAddress
        MalformedUdpForeignAbbreviatedIpv4Rejected = [bool]$malformedUdpForeignAbbreviatedIpv4
        MalformedUdpForeignBracketedIpv4Rejected = [bool]$malformedUdpForeignBracketedIpv4
        MalformedUdpForeignUnbracketedIpv6Rejected = [bool]$malformedUdpForeignUnbracketedIpv6
        HeaderlessValidUdpOnlyRejected = [bool]$headerlessValidUdpOnly
        AbbreviatedIpv4UdpRejected = [bool]$abbreviatedIpv4Udp
        LeadingZeroIpv4UdpRejected = [bool]$leadingZeroIpv4Udp
        OutOfRangeIpv4UdpRejected = [bool]$outOfRangeIpv4Udp
        BracketedIpv4UdpRejected = [bool]$bracketedIpv4Udp
        UnbracketedIpv6UdpRejected = [bool]$unbracketedIpv6Udp
        UnknownTcpRejected = [bool]$unknownTcp
        ErrorTextRejected = [bool]$errorText
        NonzeroExitRejected = [bool]$nonzeroExit
        HeaderOnlyStillKnownEmpty = [bool]($headerOnly.Success -and $headerOnly.Known -and -not $headerOnly.HasListeners)
        EmptyOutputStillRejected = [bool]$emptyOutput
        DefaultGetNetCalls = $defaultGetNetCalls
        DefaultProcessRecordCount = $defaultRecordCount
        DefaultBoundedProcessCalls = $defaultBoundedCalls
        ForcedGetNetCalls = $forcedGetNetCalls
        ForcedProcessRecordCount = $forcedRecordCount
        ForcedBoundedProcessCalls = $forcedBoundedCalls.Count
        ForcedArgv = [string]($forcedArgs -join ',')
        ForcedTimeoutMilliseconds = [int]$forcedBoundedCalls[0].TimeoutMilliseconds
        ForcedIPv4Address = [string]$forcedRoute.Listeners[0].LocalAddress
        DefaultProviderStillFirst = [bool]($defaultRoute.Success -and $defaultRoute.Known -and
            -not $defaultRoute.HasListeners -and $defaultRoute.Source -ceq 'Get-NetTCPConnection' -and
            $defaultGetNetCalls -eq 1 -and $defaultBoundedCalls -eq 0)
        ForcedNetstatUsesOneBoundedSnapshot = [bool]($forcedRoute.Success -and $forcedRoute.Known -and
            $forcedRoute.Listeners.Count -eq 1 -and $forcedRoute.Listeners[0].LocalAddress -ceq '127.0.0.1' -and
            $forcedGetNetCalls -eq 0 -and $forcedBoundedCalls.Count -eq 1 -and
            $forcedArgs.Count -eq 1 -and $forcedArgs[0] -ceq '-ano' -and
            $forcedBoundedCalls[0].TimeoutMilliseconds -eq 5000)
        ForcedNetstatSource = [string]$forcedRoute.Source
    }
}

foreach ($name in @(
    'UdpOnlyKnownEmpty',
    'UdpRowsCreatedNoOwnerRecords',
    'ConnectedUdpOnlyKnownEmpty',
    'ConnectedUdpRowsCreatedNoOwnerRecords',
    'MixedHasOnlyTargetTcp',
    'DualStackCanonicalAddresses',
    'NativeAndNetstatExactTupleSetEqual',
    'MalformedUdpPidRejected',
    'MalformedUdpForeignRejected',
    'MalformedUdpRangeRejected',
    'MalformedUdpAddressRejected',
    'HeaderlessConnectedUdpOnlyRejected',
    'MalformedUdpForeignHostRejected',
    'MalformedUdpForeignPortRangeRejected',
    'MalformedUdpForeignPortSpellingRejected',
    'MalformedUdpForeignAddressRejected',
    'MalformedUdpForeignAbbreviatedIpv4Rejected',
    'MalformedUdpForeignBracketedIpv4Rejected',
    'MalformedUdpForeignUnbracketedIpv6Rejected',
    'HeaderlessValidUdpOnlyRejected',
    'AbbreviatedIpv4UdpRejected',
    'LeadingZeroIpv4UdpRejected',
    'OutOfRangeIpv4UdpRejected',
    'BracketedIpv4UdpRejected',
    'UnbracketedIpv6UdpRejected',
    'UnknownTcpRejected',
    'ErrorTextRejected',
    'NonzeroExitRejected',
    'HeaderOnlyStillKnownEmpty',
    'EmptyOutputStillRejected',
    'DefaultProviderStillFirst',
    'ForcedNetstatUsesOneBoundedSnapshot'
)) {
    Assert-True ([bool]$report.$name) $name
}
Assert-True ([int]$report.MixedOwnerRecordCount -eq 1) 'UDP and unrelated TCP rows do not create owner records'
Assert-True ([int]$report.DefaultProcessRecordCount -eq 0) 'default Get-NetTCPConnection empty proof uses zero process records'
Assert-True ([int]$report.ForcedProcessRecordCount -eq 1) 'forced IPv4 netstat proof creates one process record'
Assert-True ($report.ForcedIPv4Address -ceq '127.0.0.1') 'forced provider preserves the IPv4 endpoint'
Assert-True ($report.ForcedNetstatSource -ceq 'netstat') 'forced provider retains truthful netstat source'

Write-Output ('PASS: native listener netstat contracts assertions=' + $assertions)
Write-Output ('DEFAULT_GETNET_CALLS=' + $report.DefaultGetNetCalls + ' PROCESS_RECORDS=' + $report.DefaultProcessRecordCount + ' BOUNDED_CALLS=' + $report.DefaultBoundedProcessCalls)
Write-Output ('FORCED_IPV4_GETNET_CALLS=' + $report.ForcedGetNetCalls + ' PROCESS_RECORDS=' + $report.ForcedProcessRecordCount + ' BOUNDED_CALLS=' + $report.ForcedBoundedProcessCalls + ' ARGV=' + $report.ForcedArgv + ' TIMEOUT_MS=' + $report.ForcedTimeoutMilliseconds)
Write-Output 'ACTORS=none'
Write-Output 'OS_LISTENER_QUERY=none'
Write-Output 'NETWORK=none'
