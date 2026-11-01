[CmdletBinding()]
param(
    [string]$RepositoryRoot = '',
    [string]$ModulePath = '',
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
$timer = [Diagnostics.Stopwatch]::StartNew()
$checks = New-Object System.Collections.ArrayList
$hostInfo = [ordered]@{
    hostName = $Host.Name
    powershellVersion = $PSVersionTable.PSVersion.ToString()
    clrVersion = [Environment]::Version.ToString()
    is64BitProcess = [Environment]::Is64BitProcess
}
$setupError = $null
$resolvedRepositoryRoot = $null
$resolvedModulePath = $null
$fullOutputPath = $null
$actualModuleSha256 = $null
$embeddedSourceSha256 = $null
$embeddedSourceByteLength = $null
$testScriptSha256 = $null
$moduleImportWarnings = @()
$fixtureHashes = [ordered]@{}
$expectedIpv4 = [ordered]@{
    HeaderSize = 4; RowSize = 24; CountOffset = 0; StateOffset = 0
    LocalAddressOffset = 4; LocalScopeOffset = -1; LocalPortOffset = 8
    RemoteAddressOffset = 12; RemoteScopeOffset = -1; RemotePortOffset = 16
    ProcessIdOffset = 20
}
$expectedIpv6 = [ordered]@{
    HeaderSize = 4; RowSize = 56; CountOffset = 0; StateOffset = 48
    LocalAddressOffset = 0; LocalScopeOffset = 16; LocalPortOffset = 20
    RemoteAddressOffset = 24; RemoteScopeOffset = 40; RemotePortOffset = 44
    ProcessIdOffset = 52
}
$expectedIpv4Marshal = [ordered]@{
    HeaderSize = 4; RowSize = 24; CountOffset = 0; StateOffset = 0
    LocalAddressOffset = 4; LocalPortOffset = 8; RemoteAddressOffset = 12
    RemotePortOffset = 16; ProcessIdOffset = 20
}

function Add-Check {
    param([string]$Name, [bool]$Passed, [object]$Expected, [object]$Actual)
    [void]$script:checks.Add([pscustomobject]@{
        name = $Name
        passed = $Passed
        expected = $Expected
        actual = $Actual
    })
}

function Put-U32LittleEndian {
    param([byte[]]$Buffer, [int]$Offset, [uint32]$Value)
    [byte[]]$raw = [BitConverter]::GetBytes($Value)
    [Array]::Copy($raw, 0, $Buffer, $Offset, 4)
}

function Put-Bytes {
    param([byte[]]$Buffer, [int]$Offset, [byte[]]$Bytes)
    [Array]::Copy($Bytes, 0, $Buffer, $Offset, $Bytes.Length)
}

function Get-BytesSha256 {
    param([byte[]]$Bytes)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function New-Ipv4Fixture {
    # Microsoft MIB_TCPTABLE_OWNER_PID: count at byte 0, first row byte 4.
    # The documented MIB_TCPROW_OWNER_PID fields form the 24-byte rows below.
    $rowSize = 24
    $rowOffset = 4
    [byte[]]$buffer = New-Object byte[] (4 + (2 * $rowSize))
    Put-U32LittleEndian $buffer 0 2

    $row = $rowOffset
    Put-U32LittleEndian $buffer ($row + 0) 2
    Put-Bytes $buffer ($row + 4) ([byte[]](127,0,0,1))
    Put-Bytes $buffer ($row + 8) ([byte[]](1,187,0,0))       # 443, network byte order
    Put-Bytes $buffer ($row + 12) ([byte[]](222,173,190,239)) # LISTEN remote address is undefined
    Put-Bytes $buffer ($row + 16) ([byte[]](190,239,222,173)) # LISTEN remote port is undefined
    Put-U32LittleEndian $buffer ($row + 20) 4242

    $row = $rowOffset + $rowSize
    Put-U32LittleEndian $buffer ($row + 0) 2
    Put-Bytes $buffer ($row + 4) ([byte[]](0,0,0,0))
    Put-Bytes $buffer ($row + 8) ([byte[]](31,144,0,0))       # 8080, network byte order
    Put-Bytes $buffer ($row + 12) ([byte[]](165,90,195,60))
    Put-Bytes $buffer ($row + 16) ([byte[]](250,1,2,3))
    Put-U32LittleEndian $buffer ($row + 20) 3030
    return ,$buffer
}

function New-Ipv6Fixture {
    # Microsoft MIB_TCP6TABLE_OWNER_PID: count at byte 0, first row byte 4.
    # Rows use documented order: local address/scope/port, remote address/scope/port,
    # state, owning PID. All offsets and row stride here are literal oracle values.
    $rowSize = 56
    $rowOffset = 4
    [byte[]]$buffer = New-Object byte[] (4 + (3 * $rowSize))
    Put-U32LittleEndian $buffer 0 3

    $row = $rowOffset
    [byte[]]$loopback = New-Object byte[] 16
    $loopback[15] = 1
    Put-Bytes $buffer ($row + 0) $loopback
    Put-Bytes $buffer ($row + 16) ([byte[]](0,0,0,0))
    Put-Bytes $buffer ($row + 20) ([byte[]](1,187,0,0))       # 443, network byte order
    Put-Bytes $buffer ($row + 24) ([byte[]](165,90,195,60,1,2,3,4,5,6,7,8,9,10,11,12))
    Put-Bytes $buffer ($row + 40) ([byte[]](18,52,86,120))   # undefined LISTEN scope junk
    Put-Bytes $buffer ($row + 44) ([byte[]](250,1,2,3))      # undefined LISTEN port junk
    Put-U32LittleEndian $buffer ($row + 48) 2
    Put-U32LittleEndian $buffer ($row + 52) 4243

    $row = $rowOffset + $rowSize
    [byte[]]$wildcard = New-Object byte[] 16
    Put-Bytes $buffer ($row + 0) $wildcard
    Put-Bytes $buffer ($row + 16) ([byte[]](0,0,0,0))
    Put-Bytes $buffer ($row + 20) ([byte[]](31,145,0,0))     # 8081, network byte order
    Put-Bytes $buffer ($row + 24) ([byte[]](9,8,7,6,5,4,3,2,1,0,9,8,7,6,5,4))
    Put-Bytes $buffer ($row + 40) ([byte[]](170,187,204,221))
    Put-Bytes $buffer ($row + 44) ([byte[]](250,1,2,3))
    Put-U32LittleEndian $buffer ($row + 48) 2
    Put-U32LittleEndian $buffer ($row + 52) 4244

    $row = $rowOffset + (2 * $rowSize)
    [byte[]]$linkLocal = New-Object byte[] 16
    $linkLocal[0] = 254
    $linkLocal[1] = 128
    $linkLocal[15] = 1
    Put-Bytes $buffer ($row + 0) $linkLocal
    Put-Bytes $buffer ($row + 16) ([byte[]](0,0,0,2))       # scope 2, network byte order
    Put-Bytes $buffer ($row + 20) ([byte[]](32,251,0,0))    # 8443, network byte order
    Put-Bytes $buffer ($row + 24) ([byte[]](17,34,51,68,85,102,119,136,153,170,187,204,221,238,255,1))
    Put-Bytes $buffer ($row + 40) ([byte[]](1,2,3,4))
    Put-Bytes $buffer ($row + 44) ([byte[]](5,6,7,8))
    Put-U32LittleEndian $buffer ($row + 48) 2
    Put-U32LittleEndian $buffer ($row + 52) 4245
    return ,$buffer
}

function Get-ObservedMarshalLayout {
    param([string]$Family, [System.Reflection.Assembly]$Assembly)
    $ns = 'TroubleshootJS.Verifier.NativeListenerR1.'
    if ($Family -eq 'IPv4') {
        $rowType = $Assembly.GetType($ns + 'MibTcpRowOwnerPid', $true)
        $tableType = $Assembly.GetType($ns + 'MibTcpTableOwnerPid', $true)
        $rowFields = [ordered]@{
            StateOffset = 'State'; LocalAddressOffset = 'LocalAddress'; LocalPortOffset = 'LocalPort'
            RemoteAddressOffset = 'RemoteAddress'; RemotePortOffset = 'RemotePort'; ProcessIdOffset = 'OwningPid'
        }
    }
    else {
        $rowType = $Assembly.GetType($ns + 'MibTcp6RowOwnerPid', $true)
        $tableType = $Assembly.GetType($ns + 'MibTcp6TableOwnerPid', $true)
        $rowFields = [ordered]@{
            StateOffset = 'State'; LocalAddressOffset = 'LocalAddress'; LocalScopeOffset = 'LocalScopeId'
            LocalPortOffset = 'LocalPort'; RemoteAddressOffset = 'RemoteAddress'; RemoteScopeOffset = 'RemoteScopeId'
            RemotePortOffset = 'RemotePort'; ProcessIdOffset = 'OwningPid'
        }
    }
    $marshalType = [Runtime.InteropServices.Marshal]
    $flags = [Reflection.BindingFlags]::Public -bor [Reflection.BindingFlags]::Static
    $sizeArgTypes = New-Object System.Type[] 1
    $sizeArgTypes[0] = [System.Type]
    $sizeMethod = $marshalType.GetMethod('SizeOf', $flags, $null, $sizeArgTypes, $null)
    $offsetArgTypes = New-Object System.Type[] 2
    $offsetArgTypes[0] = [System.Type]
    $offsetArgTypes[1] = [string]
    $offsetMethod = $marshalType.GetMethod('OffsetOf', $flags, $null, $offsetArgTypes, $null)
    $observed = [ordered]@{
        HeaderSize = $offsetMethod.Invoke($null, [object[]]@($tableType, 'FirstRow')).ToInt32()
        RowSize = $sizeMethod.Invoke($null, [object[]]@($rowType))
        CountOffset = $offsetMethod.Invoke($null, [object[]]@($tableType, 'NumberOfEntries')).ToInt32()
    }
    foreach ($key in $rowFields.Keys) {
        $observed[$key] = $offsetMethod.Invoke($null, [object[]]@($rowType, $rowFields[$key])).ToInt32()
    }
    return $observed
}

function Get-ObservedProviderLayout {
    param([string]$Family)
    $af = if ($Family -eq 'IPv4') { 2 } else { 23 }
    $layout = [TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider]::GetLayoutInfoForPureTest($af)
    return [ordered]@{
        HeaderSize = $layout.HeaderSize; RowSize = $layout.RowSize; CountOffset = $layout.CountOffset
        StateOffset = $layout.StateOffset; LocalAddressOffset = $layout.LocalAddressOffset
        LocalScopeOffset = $layout.LocalScopeOffset; LocalPortOffset = $layout.LocalPortOffset
        RemoteAddressOffset = $layout.RemoteAddressOffset; RemoteScopeOffset = $layout.RemoteScopeOffset
        RemotePortOffset = $layout.RemotePortOffset; ProcessIdOffset = $layout.ProcessIdOffset
    }
}

function Compare-Layout {
    param([string]$Family, [System.Collections.IDictionary]$Expected, [System.Collections.IDictionary]$Actual, [string]$Source)
    foreach ($key in $Expected.Keys) {
        $e = [int]$Expected[$key]
        $a = [int]$Actual[$key]
        Add-Check -Name ($Family + ' ABI ' + $Source + ' ' + $key) -Passed ($e -eq $a) -Expected $e -Actual $a
    }
}

function Expect-InvalidData {
    param([string]$Name, [scriptblock]$Action)
    try {
        $ignored = & $Action
        Add-Check -Name $Name -Passed $false -Expected 'InvalidDataException' -Actual 'decoder accepted malformed input'
    }
    catch {
        $exception = $_.Exception
        while ($null -ne $exception.InnerException) { $exception = $exception.InnerException }
        $isInvalidData = $exception -is [System.IO.InvalidDataException]
        Add-Check -Name $Name -Passed $isInvalidData -Expected 'InvalidDataException' -Actual ($exception.GetType().FullName + ': ' + $exception.Message)
    }
}

function Test-EmptyTable {
    param([string]$Family)
    [byte[]]$empty = New-Object byte[] 4
    Put-U32LittleEndian $empty 0 0
    if ($Family -eq 'IPv4') {
        $rows = [TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider]::DecodeIpv4ForPureTest($empty, 4)
    }
    else {
        $rows = [TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider]::DecodeIpv6ForPureTest($empty, 4)
    }
    Add-Check -Name ($Family + ' complete empty table') -Passed ($rows.Length -eq 0) -Expected 0 -Actual $rows.Length
}

try {
    $rootInput = if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
        Join-Path $PSScriptRoot '..\..'
    } else { $RepositoryRoot }
    $rootItem = Resolve-Path -LiteralPath $rootInput -ErrorAction Stop
    if ($rootItem.Provider.Name -cne 'FileSystem') {
        throw 'RepositoryRoot must resolve to a filesystem directory.'
    }
    $resolvedRepositoryRoot = [IO.Path]::GetFullPath($rootItem.ProviderPath)
    $rootDirectoryItem = Get-Item -LiteralPath $resolvedRepositoryRoot -Force -ErrorAction Stop
    if (($rootDirectoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'RepositoryRoot may not be a reparse point.'
    }
    if (-not (Test-Path -LiteralPath $resolvedRepositoryRoot -PathType Container)) {
        throw 'RepositoryRoot must be an existing directory.'
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
    if (-not (Test-Path -LiteralPath $resolvedModulePath -PathType Leaf)) {
        throw 'ModulePath must resolve to an existing module file.'
    }
    $rootPrefix = $resolvedRepositoryRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedModulePath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'ModulePath must remain physically within RepositoryRoot.'
    }
    $walkPath = $resolvedModulePath
    while ($walkPath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        $walkItem = Get-Item -LiteralPath $walkPath -Force -ErrorAction Stop
        if (($walkItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'RepositoryRoot and ModulePath may not traverse a reparse point.'
        }
        if ($walkPath -eq $resolvedRepositoryRoot) { break }
        $walkPath = Split-Path -Parent $walkPath
        if ([string]::IsNullOrWhiteSpace($walkPath)) { throw 'ModulePath escaped RepositoryRoot during physical path validation.' }
    }
    if ($walkPath -ne $resolvedRepositoryRoot) {
        throw 'ModulePath physical ancestors did not terminate at RepositoryRoot.'
    }

    if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
        $outputCandidate = [IO.Path]::GetFullPath($OutputPath)
        if ([IO.Path]::GetExtension($outputCandidate) -ine '.json') {
            throw 'OutputPath must name a new JSON file.'
        }
        if (Test-Path -LiteralPath $outputCandidate) {
            throw 'OutputPath must be a new file; existing output is never overwritten.'
        }
        $outputParent = Split-Path -Parent $outputCandidate
        if (-not (Test-Path -LiteralPath $outputParent -PathType Container)) {
            throw 'OutputPath parent directory must already exist.'
        }
        $fullOutputPath = Join-Path ([IO.Path]::GetFullPath((Resolve-Path -LiteralPath $outputParent).ProviderPath)) (Split-Path -Leaf $outputCandidate)
        if (Test-Path -LiteralPath $fullOutputPath) {
            throw 'OutputPath must be a new file; existing output is never overwritten.'
        }
    }

    if (-not [BitConverter]::IsLittleEndian) {
        throw 'Fixture byte order requires the selected Windows little-endian ABI host.'
    }

    $actualModuleSha256 = Get-BytesSha256 ([IO.File]::ReadAllBytes($resolvedModulePath))
    $testScriptSha256 = Get-BytesSha256 ([IO.File]::ReadAllBytes($PSCommandPath))

    $imported = @(Import-Module -Name $resolvedModulePath -PassThru -ErrorAction Stop -WarningVariable moduleImportWarnings -WarningAction SilentlyContinue)
    $matchingModules = @($imported | Where-Object {
        $_ -is [System.Management.Automation.PSModuleInfo] -and
        [string]::Equals([IO.Path]::GetFullPath($_.Path), $resolvedModulePath, [StringComparison]::OrdinalIgnoreCase)
    })
    if ($matchingModules.Count -ne 1) {
        throw 'The exact validated module path did not return one import handle.'
    }
    $module = $matchingModules[0]

    $encodedSource = & $module {
        $initializer = Get-Command Initialize-VerifierNativeTcpTableInterop -CommandType Function -ErrorAction Stop
        $assignmentPredicate = {
            param($node)
            return (
                $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
                $node.Left -is [System.Management.Automation.Language.VariableExpressionAst] -and
                $node.Left.VariablePath.UserPath -ceq 'encodedSource'
            )
        }
        $assignments = @($initializer.ScriptBlock.Ast.FindAll($assignmentPredicate, $true))
        if ($assignments.Count -ne 1) {
            throw ('The private initializer must contain one encodedSource assignment; found ' + $assignments.Count + '.')
        }
        $literalPredicate = {
            param($node)
            return $node -is [System.Management.Automation.Language.StringConstantExpressionAst]
        }
        $literals = @($assignments[0].Right.FindAll($literalPredicate, $true))
        if ($literals.Count -ne 1) {
            throw ('The encodedSource assignment must contain one string literal; found ' + $literals.Count + '.')
        }
        return [string]$literals[0].Value
    }
    [byte[]]$embeddedSourceBytes = [Convert]::FromBase64String($encodedSource)
    $embeddedSourceSha256 = Get-BytesSha256 $embeddedSourceBytes
    $embeddedSourceByteLength = $embeddedSourceBytes.Length
    $resolvedModulePathExact = [string]::Equals([IO.Path]::GetFullPath($module.Path), $resolvedModulePath, [StringComparison]::OrdinalIgnoreCase)
    $moduleHashValid = $actualModuleSha256 -match '^[0-9a-f]{64}$'
    Add-Check -Name 'module source path and SHA-256 provenance captured' -Passed ($resolvedModulePathExact -and $moduleHashValid) -Expected 'exact imported module handle path; actual 64-digit SHA-256 recorded without a pinned candidate hash' -Actual ($resolvedModulePath + ' | ' + $actualModuleSha256)
    $embeddedSourceHashValid = ($embeddedSourceByteLength -gt 0 -and $embeddedSourceSha256 -match '^[0-9a-f]{64}$')
    Add-Check -Name 'embedded C# source SHA-256 provenance captured' -Passed $embeddedSourceHashValid -Expected 'nonempty source literal decoded from the exact private initializer; actual 64-digit SHA-256 recorded without a pinned candidate hash' -Actual ($embeddedSourceByteLength.ToString() + ' bytes | ' + $embeddedSourceSha256)

    $providerTypeName = 'TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider'
    if ($null -ne ($providerTypeName -as [type])) {
        throw 'The pure contract requires a fresh process with no preloaded native provider type.'
    }
    $compileTimer = [Diagnostics.Stopwatch]::StartNew()
    & $module { Initialize-VerifierNativeTcpTableInterop }
    $compileTimer.Stop()
    $providerType = $providerTypeName -as [type]
    $compilePass = ($null -ne $providerType -and $providerType.IsAbstract -and $providerType.IsSealed)
    Add-Check -Name 'initialize actual module-embedded C# provider through exact imported module handle' -Passed $compilePass -Expected 'private compiler defines the actual static TcpTableProvider type in this fresh host' -Actual (($null -ne $providerType).ToString() + ' | ' + $compileTimer.Elapsed.TotalMilliseconds.ToString('F3') + ' ms')
    if (-not $compilePass) {
        throw 'The private module initializer did not produce the expected static provider type.'
    }

    $assembly = $providerType.Assembly
    $observed4Marshal = Get-ObservedMarshalLayout -Family 'IPv4' -Assembly $assembly
    $observed6Marshal = Get-ObservedMarshalLayout -Family 'IPv6' -Assembly $assembly
    $observed4Provider = Get-ObservedProviderLayout -Family 'IPv4'
    $observed6Provider = Get-ObservedProviderLayout -Family 'IPv6'
    Compare-Layout -Family 'IPv4' -Expected $expectedIpv4Marshal -Actual $observed4Marshal -Source 'Marshal'
    Compare-Layout -Family 'IPv4' -Expected $expectedIpv4 -Actual $observed4Provider -Source 'provider API'
    Compare-Layout -Family 'IPv6' -Expected $expectedIpv6 -Actual $observed6Marshal -Source 'Marshal'
    Compare-Layout -Family 'IPv6' -Expected $expectedIpv6 -Actual $observed6Provider -Source 'provider API'
    [byte[]]$ipv4 = New-Ipv4Fixture
    [byte[]]$ipv6 = New-Ipv6Fixture
    $fixtureHashes.ipv4LoopbackAndWildcard = Get-BytesSha256 $ipv4
    $fixtureHashes.ipv6LoopbackWildcardAndScopedLinkLocal = Get-BytesSha256 $ipv6

    $rows4 = [TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider]::DecodeIpv4ForPureTest($ipv4, $ipv4.Length)
    $v4Actual = @($rows4 | ForEach-Object { $_.Address + '|' + $_.Port + '|' + $_.ProcessId + '|' + $_.State + '|' + $_.Family })
    $v4Expected = @('127.0.0.1|443|4242|2|2', '0.0.0.0|8080|3030|2|2')
    $v4Pass = ($v4Actual.Count -eq $v4Expected.Count)
    if ($v4Pass) {
        for ($i = 0; $i -lt $v4Expected.Count; $i++) { if ($v4Actual[$i] -ne $v4Expected[$i]) { $v4Pass = $false } }
    }
    Add-Check -Name 'IPv4 pure decode loopback wildcard address port PID state' -Passed $v4Pass -Expected ($v4Expected -join ';') -Actual ($v4Actual -join ';')

    Test-EmptyTable -Family 'IPv4'
    Test-EmptyTable -Family 'IPv6'

    $truncatedHeader4 = [byte[]](0,0,0,0)
    Expect-InvalidData -Name 'IPv4 truncated header' -Action { [TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider]::DecodeIpv4ForPureTest($truncatedHeader4, 3) }

    $truncatedBody4 = [byte[]]$ipv4.Clone()
    Expect-InvalidData -Name 'IPv4 truncated declared rows' -Action { [TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider]::DecodeIpv4ForPureTest($truncatedBody4, $truncatedBody4.Length - 1) }

    [byte[]]$excessiveCount4 = [byte[]](161,134,1,0)
    Expect-InvalidData -Name 'IPv4 excessive row count' -Action { [TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider]::DecodeIpv4ForPureTest($excessiveCount4, 4) }

    [byte[]]$badState4 = $ipv4.Clone()
    Put-U32LittleEndian $badState4 4 1
    Expect-InvalidData -Name 'IPv4 non-listening state' -Action { [TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider]::DecodeIpv4ForPureTest($badState4, $badState4.Length) }

    [byte[]]$badPort4 = $ipv4.Clone()
    Put-Bytes $badPort4 12 ([byte[]](1,187,0,1))
    Expect-InvalidData -Name 'IPv4 malformed network-order port upper bits' -Action { [TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider]::DecodeIpv4ForPureTest($badPort4, $badPort4.Length) }

    $ipv6DecodePass = $false
    $ipv6DecodeActual = ''
    try {
        $rows6 = [TroubleshootJS.Verifier.NativeListenerR1.TcpTableProvider]::DecodeIpv6ForPureTest($ipv6, $ipv6.Length)
        $v6Actual = @($rows6 | ForEach-Object { $_.Address + '|' + $_.Port + '|' + $_.ProcessId + '|' + $_.State + '|' + $_.Family })
        $v6Expected = @('::1|443|4243|2|23', '::|8081|4244|2|23', 'fe80::1%2|8443|4245|2|23')
        $ipv6DecodePass = ($v6Actual.Count -eq $v6Expected.Count)
        if ($ipv6DecodePass) {
            for ($i = 0; $i -lt $v6Expected.Count; $i++) { if ($v6Actual[$i] -ne $v6Expected[$i]) { $ipv6DecodePass = $false } }
        }
        $ipv6DecodeActual = $v6Actual -join ';'
        Add-Check -Name 'IPv6 pure decode loopback wildcard scoped address port PID state' -Passed $ipv6DecodePass -Expected ($v6Expected -join ';') -Actual $ipv6DecodeActual
    }
    catch {
        $ipv6DecodeActual = $_.Exception.GetType().FullName + ': ' + $_.Exception.Message
        Add-Check -Name 'IPv6 pure decode loopback wildcard scoped address port PID state' -Passed $false -Expected 'three documented-layout rows decoded with network-order port/scope' -Actual $ipv6DecodeActual
    }
}
catch {
    $setupError = $_.Exception.GetType().FullName + ': ' + $_.Exception.Message
}

$timer.Stop()
$failures = @($checks | Where-Object { -not $_.passed })
if ($null -ne $setupError) {
    $outcome = 'BLOCKED'
    $exitCode = 2
}
elseif ($failures.Count -eq 0) {
    $outcome = 'PASS'
    $exitCode = 0
}
else {
    $outcome = 'FAIL'
    $exitCode = 1
}

$result = [ordered]@{
    outcome = $outcome
    repositoryRoot = $resolvedRepositoryRoot
    modulePath = $resolvedModulePath
    moduleSha256 = $actualModuleSha256
    moduleImportWarnings = @($moduleImportWarnings)
    embeddedCSharpSourceByteLength = $embeddedSourceByteLength
    embeddedCSharpSourceSha256 = $embeddedSourceSha256
    testScriptPath = $PSCommandPath
    testScriptSha256 = $testScriptSha256
    host = $hostInfo
    elapsedMilliseconds = [Math]::Round($timer.Elapsed.TotalMilliseconds, 3)
    noOsQueryContract = 'Only exact module import, private compile-only initializer, source/literal provenance inspection, reflection/Marshal layout inspection, managed byte-array construction, and pure decode APIs were used. GetListenerRows, GetExtendedTcpTable, DLL/library loads, context/listener/preview/process/CIM/network actors were not called.'
    oracle = @{
        ipv4 = $expectedIpv4
        ipv6 = $expectedIpv6
        tableCountOffset = 0
        tableFirstRowOffset = 4
        ipv4RowStride = 24
        ipv6RowStride = 56
        source = 'Literal Windows ABI offsets and hand-authored fixture bytes. Fixture construction does not read provider layout.'
    }
    fixtureSha256 = $fixtureHashes
    observed = @{
        ipv4Marshal = $observed4Marshal
        ipv4ProviderLayout = $observed4Provider
        ipv6Marshal = $observed6Marshal
        ipv6ProviderLayout = $observed6Provider
    }
    checkCount = $checks.Count
    passCount = @($checks | Where-Object { $_.passed }).Count
    failCount = $failures.Count
    checks = @($checks.ToArray())
    setupError = $setupError
}
$json = $result | ConvertTo-Json -Depth 9
[Console]::Out.WriteLine($json)
if ($null -ne $fullOutputPath -and $null -eq $setupError) {
    $fileBytes = [Text.Encoding]::UTF8.GetBytes($json)
    $stream = $null
    try {
        $stream = [IO.File]::Open($fullOutputPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        $stream.Write($fileBytes, 0, $fileBytes.Length)
    }
    finally {
        if ($null -ne $stream) { $stream.Dispose() }
    }
}
if ($outcome -eq 'PASS') {
    [Console]::Out.WriteLine('PASS: native listener contracts assertions=' + $checks.Count)
}
else {
    [Console]::Out.WriteLine($outcome + ': native listener contracts assertions=' + $checks.Count + ' passed=' + $result.passCount + ' failed=' + $result.failCount)
}
exit $exitCode