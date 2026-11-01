[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$modulePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\scripts\VerifierIsolation.psm1'))
$namespaceBefore = 'namespace TroubleshootJsVerifierBoundReceiptParent1 {'
$namespaceAfter = 'namespace TroubleshootJsVerifierBoundReceiptParentTest1 {'
$caseRows = New-Object Collections.ArrayList
$outerError = $null
$compiled = $false
$roundTripExact = $false
$sourceSha256 = $null
$transformedSha256 = $null
$moduleSha256Before = $null
$moduleSha256After = $null
$compileElapsedMs = $null
$timer = [Diagnostics.Stopwatch]::StartNew()

function Get-Sha256Hex([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant()
    } finally { $sha.Dispose() }
}

function Normalize-LineEndings([string]$Text) {
    return [regex]::Replace($Text, "\r\n?", "`n")
}

function Replace-ExactlyOnce([string]$Text, [string]$Before, [string]$After, [string]$Label) {
    $count = ([regex]::Matches($Text, [regex]::Escape($Before))).Count
    if ($count -ne 1) { throw "Closed source transform count for $Label was $count, expected 1." }
    return $Text.Replace($Before, $After)
}

$nativeBefore = @(
@'
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr CreateToolhelp32Snapshot(uint flags, uint processId);
'@,
@'
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool Process32FirstW(IntPtr snapshot, ref PROCESSENTRY32 entry);
'@,
@'
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool Process32NextW(IntPtr snapshot, ref PROCESSENTRY32 entry);
'@,
@'
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CloseHandle(IntPtr handle);
'@,
@'
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr OpenProcess(uint access, bool inheritHandle, uint processId);
'@,
@'
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern uint GetProcessId(IntPtr processHandle);
'@,
@'
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool GetProcessTimes(IntPtr processHandle, out FILETIME creationTime,
            out FILETIME exitTime, out FILETIME kernelTime, out FILETIME userTime);
'@,
@'
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool QueryFullProcessImageNameW(IntPtr processHandle,
            uint flags, StringBuilder imagePath, ref uint size);
'@,
@'
        [DllImport("ntdll.dll")]
        private static extern int NtQueryInformationProcess(IntPtr processHandle,
            int informationClass, IntPtr information, int informationLength,
            out int returnLength);
'@
)
$nativeAfter = @(
@'
        private static IntPtr CreateToolhelp32Snapshot(uint flags, uint processId) {
            return SyntheticNativeCalls.CreateToolhelp32Snapshot(flags, processId);
        }
'@,
@'
        private static bool Process32FirstW(IntPtr snapshot, ref PROCESSENTRY32 entry) {
            return SyntheticNativeCalls.Process32FirstW(snapshot, ref entry);
        }
'@,
@'
        private static bool Process32NextW(IntPtr snapshot, ref PROCESSENTRY32 entry) {
            return SyntheticNativeCalls.Process32NextW(snapshot, ref entry);
        }
'@,
@'
        private static bool CloseHandle(IntPtr handle) {
            return SyntheticNativeCalls.CloseHandle(handle);
        }
'@,
@'
        private static IntPtr OpenProcess(uint access, bool inheritHandle, uint processId) {
            return SyntheticNativeCalls.OpenProcess(access, inheritHandle, processId);
        }
'@,
@'
        private static uint GetProcessId(IntPtr processHandle) {
            return SyntheticNativeCalls.GetProcessId(processHandle);
        }
'@,
@'
        private static bool GetProcessTimes(IntPtr processHandle, out FILETIME creationTime,
                out FILETIME exitTime, out FILETIME kernelTime, out FILETIME userTime) {
            return SyntheticNativeCalls.GetProcessTimes(processHandle, out creationTime,
                out exitTime, out kernelTime, out userTime);
        }
'@,
@'
        private static bool QueryFullProcessImageNameW(IntPtr processHandle,
                uint flags, StringBuilder imagePath, ref uint size) {
            return SyntheticNativeCalls.QueryFullProcessImageNameW(processHandle,
                flags, imagePath, ref size);
        }
'@,
@'
        private static int NtQueryInformationProcess(IntPtr processHandle,
                int informationClass, IntPtr information, int informationLength,
                out int returnLength) {
            return SyntheticNativeCalls.NtQueryInformationProcess(processHandle,
                informationClass, information, informationLength, out returnLength);
        }
'@
)

$testOnlyBlock = @'
        public static void ConfigureSyntheticScenario(string id) { SyntheticNativeCalls.Configure(id); }
        public static void ResetSyntheticScenario() { SyntheticNativeCalls.Reset(); }
        public static int FakeOpenProcessCallCount { get { return SyntheticNativeCalls.OpenProcessCallCount; } }
        public static int FakeProcessFirstCallCount { get { return SyntheticNativeCalls.ProcessFirstCallCount; } }
        public static int FakeProcessNextCallCount { get { return SyntheticNativeCalls.ProcessNextCallCount; } }
        public static int FakeProcessTimesCallCount { get { return SyntheticNativeCalls.ProcessTimesCallCount; } }
        public static int FakeParentQueryCallCount { get { return SyntheticNativeCalls.ParentQueryCallCount; } }
        public static int FakeCloseHandleCallCount { get { return SyntheticNativeCalls.CloseHandleCallCount; } }
        public static int FakeCloseFailureCount { get { return SyntheticNativeCalls.CloseFailureCount; } }
        public static int FakeOutstandingHandleCount { get { return SyntheticNativeCalls.OutstandingHandleCount; } }
        public static uint[] FakeOpenAccessMasks { get { return SyntheticNativeCalls.OpenAccessMasks; } }
        public static uint FakeSuccessfulOpenAccessMask { get { return SyntheticNativeCalls.SuccessfulOpenAccessMask; } }

        private static class SyntheticNativeCalls {
            private const int ParentPid = 900;
            private const int AccessDenied = 5;
            private const int NoMoreFiles = 18;
            private const int StatusInfoLengthMismatch = unchecked((int)0xC0000004);
            private const int StatusAccessDenied = unchecked((int)0xC0000005);
            private static readonly IntPtr Snapshot = new IntPtr(7777);
            private static string scenario;
            private static List<PROCESSENTRY32> entries;
            private static Dictionary<IntPtr, int> pidsByHandle;
            private static HashSet<IntPtr> openHandles;
            private static int cursor;
            private static int lastError;
            private static bool snapshotOpen;
            private static List<uint> openAccessMasks;
            private static uint successfulOpenAccessMask;
            public static int OpenProcessCallCount;
            public static int ProcessFirstCallCount;
            public static int ProcessNextCallCount;
            public static int ProcessTimesCallCount;
            public static int ParentQueryCallCount;
            public static int CloseHandleCallCount;
            public static int CloseFailureCount;
            public static int OutstandingHandleCount { get { return openHandles.Count + (snapshotOpen ? 1 : 0); } }
            public static uint[] OpenAccessMasks { get { return openAccessMasks.ToArray(); } }
            public static uint SuccessfulOpenAccessMask { get { return successfulOpenAccessMask; } }

            public static void Reset() {
                scenario = null;
                entries = new List<PROCESSENTRY32>();
                pidsByHandle = new Dictionary<IntPtr, int>();
                openHandles = new HashSet<IntPtr>();
                cursor = -1;
                lastError = 0;
                snapshotOpen = false;
                openAccessMasks = new List<uint>();
                successfulOpenAccessMask = 0;
                OpenProcessCallCount = ProcessFirstCallCount = ProcessNextCallCount = 0;
                ProcessTimesCallCount = ParentQueryCallCount = CloseHandleCallCount = CloseFailureCount = 0;
            }

            private static PROCESSENTRY32 MakeEntry(int pid, int ppid, string name) {
                return new PROCESSENTRY32 { dwSize = (uint)Marshal.SizeOf(typeof(PROCESSENTRY32)),
                    th32ProcessID = (uint)pid, th32ParentProcessID = (uint)ppid, szExeFile = name };
            }

            public static void Configure(string id) {
                Reset();
                scenario = id;
                if (id == "control-valid-one-child") {
                    entries.Add(MakeEntry(202, 800, "other202.exe"));
                    entries.Add(MakeEntry(101, ParentPid, "child101.exe"));
                } else if (id == "control-reduced-rights-fallback") {
                    entries.Add(MakeEntry(101, ParentPid, "child101.exe"));
                } else if (id == "control-valid-empty-filter") {
                    entries.Add(MakeEntry(201, 800, "other201.exe"));
                } else if (id == "partial-toolhelp-enumeration" || id == "native-parent-query-failure" ||
                        id == "image-query-failure" || id == "process-handle-close-failure" ||
                        id == "primary-and-process-close-failure" || id == "snapshot-handle-close-failure") {
                    entries.Add(MakeEntry(101, ParentPid, "child101.exe"));
                } else if (id == "matching-child-open-failure-after-valid-child" ||
                        id == "same-handle-identity-query-failure-after-valid-child") {
                    entries.Add(MakeEntry(101, ParentPid, "child101.exe"));
                    entries.Add(MakeEntry(102, ParentPid, "child102.exe"));
                } else if (id != "first-entry-failure") {
                    throw new ArgumentException("Unknown synthetic scenario.", "id");
                }
            }

            public static int GetLastError() { return lastError; }
            public static IntPtr CreateToolhelp32Snapshot(uint flags, uint processId) {
                if (flags != TH32CS_SNAPPROCESS || processId != 0) { lastError = 87; return new IntPtr(-1); }
                snapshotOpen = true;
                return Snapshot;
            }
            public static bool Process32FirstW(IntPtr snapshot, ref PROCESSENTRY32 entry) {
                ProcessFirstCallCount++;
                if (snapshot != Snapshot || !snapshotOpen) { lastError = 6; return false; }
                if (scenario == "first-entry-failure" || entries.Count == 0) { lastError = NoMoreFiles; return false; }
                cursor = 0;
                entry = entries[cursor];
                return true;
            }
            public static bool Process32NextW(IntPtr snapshot, ref PROCESSENTRY32 entry) {
                ProcessNextCallCount++;
                if (snapshot != Snapshot || !snapshotOpen) { lastError = 6; return false; }
                if (scenario == "partial-toolhelp-enumeration" && cursor == 0) { lastError = AccessDenied; return false; }
                int next = cursor + 1;
                if (next >= entries.Count) { lastError = NoMoreFiles; return false; }
                cursor = next;
                entry = entries[cursor];
                return true;
            }
            public static IntPtr OpenProcess(uint access, bool inheritHandle, uint processId) {
                OpenProcessCallCount++;
                openAccessMasks.Add(access);
                if (scenario == "control-reduced-rights-fallback" &&
                        (access == 0x1400 || access == 0x0400)) {
                    lastError = AccessDenied;
                    return IntPtr.Zero;
                }
                if ((scenario == "matching-child-open-failure-after-valid-child" && processId == 102) || processId == 0) {
                    lastError = AccessDenied;
                    return IntPtr.Zero;
                }
                IntPtr handle = new IntPtr(10000 + (int)processId);
                pidsByHandle[handle] = (int)processId;
                openHandles.Add(handle);
                successfulOpenAccessMask = access;
                return handle;
            }
            public static uint GetProcessId(IntPtr processHandle) {
                int pid;
                if (!pidsByHandle.TryGetValue(processHandle, out pid)) { lastError = 6; return 0; }
                if (scenario == "same-handle-identity-query-failure-after-valid-child" && pid == 102) return (uint)(pid + 1);
                return (uint)pid;
            }
            private static FILETIME MakeFileTime(long value) {
                return new FILETIME { Low = unchecked((uint)(value & 0xffffffffL)), High = unchecked((uint)(value >> 32)) };
            }
            public static bool GetProcessTimes(IntPtr processHandle, out FILETIME creation,
                    out FILETIME exit, out FILETIME kernel, out FILETIME user) {
                ProcessTimesCallCount++;
                creation = exit = kernel = user = new FILETIME();
                int pid;
                if (!pidsByHandle.TryGetValue(processHandle, out pid)) { lastError = 6; return false; }
                if (scenario == "primary-and-process-close-failure" && pid == 101) { lastError = AccessDenied; return false; }
                DateTime created = new DateTime(2024, 1, 1, 0, 0, 0, DateTimeKind.Utc).AddTicks(pid);
                creation = MakeFileTime(created.ToFileTimeUtc());
                return true;
            }
            public static int NtQueryInformationProcess(IntPtr processHandle, int infoClass,
                    IntPtr information, int informationLength, out int returnLength) {
                returnLength = 0;
                int pid;
                if (!pidsByHandle.TryGetValue(processHandle, out pid)) return StatusAccessDenied;
                if (infoClass == ProcessBasicInformation) {
                    ParentQueryCallCount++;
                    if (scenario == "native-parent-query-failure" && pid == 101) return StatusAccessDenied;
                    PROCESS_BASIC_INFORMATION basic = new PROCESS_BASIC_INFORMATION {
                        UniqueProcessId = new IntPtr(pid), InheritedFromUniqueProcessId = new IntPtr(ParentPid) };
                    Marshal.StructureToPtr(basic, information, false);
                    returnLength = Marshal.SizeOf(typeof(PROCESS_BASIC_INFORMATION));
                    return 0;
                }
                if (infoClass == ProcessCommandLineInformation) {
                    if (information == IntPtr.Zero) { returnLength = 16; return StatusInfoLengthMismatch; }
                    Marshal.WriteInt16(information, 0, 0);
                    Marshal.WriteInt16(information, 2, 0);
                    return 0;
                }
                return StatusAccessDenied;
            }
            public static bool QueryFullProcessImageNameW(IntPtr processHandle, uint flags,
                    StringBuilder imagePath, ref uint size) {
                int pid;
                if (!pidsByHandle.TryGetValue(processHandle, out pid)) { lastError = 6; return false; }
                if (scenario == "image-query-failure" && pid == 101) { lastError = AccessDenied; return false; }
                string path = "C:\\Synthetic\\child" + pid.ToString() + ".exe";
                if ((uint)path.Length > size) { lastError = 122; return false; }
                imagePath.Clear(); imagePath.Append(path); size = (uint)path.Length;
                return true;
            }
            public static bool CloseHandle(IntPtr handle) {
                CloseHandleCallCount++;
                if (handle == Snapshot) {
                    if (scenario == "snapshot-handle-close-failure") { CloseFailureCount++; lastError = AccessDenied; return false; }
                    snapshotOpen = false;
                    return true;
                }
                int pid;
                if (pidsByHandle.TryGetValue(handle, out pid)) {
                    if ((scenario == "process-handle-close-failure" || scenario == "primary-and-process-close-failure") && pid == 101) {
                        CloseFailureCount++; lastError = AccessDenied; return false;
                    }
                    pidsByHandle.Remove(handle); openHandles.Remove(handle); return true;
                }
                lastError = 6;
                return false;
            }
        }
'@

try {
    if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
        throw 'A pre-created task output directory is required.'
    }
    $moduleBytes = [IO.File]::ReadAllBytes($modulePath)
    $moduleSha256Before = Get-Sha256Hex $moduleBytes
    $strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
    $moduleText = $strictUtf8.GetString($moduleBytes)

    $tokens = $null
    $parseErrors = $null
    $moduleAst = [Management.Automation.Language.Parser]::ParseInput($moduleText, $modulePath, [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count -ne 0) { throw 'Maintained module failed PowerShell AST parsing.' }
    $functions = @($moduleAst.FindAll({ param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -ceq 'Initialize-VerifierBoundReceiptNativeParentCensus'
    }, $true))
    if ($functions.Count -ne 1) { throw 'Expected one maintained native-parent initializer function.' }
    $assignments = @($functions[0].FindAll({ param($node)
        $node -is [Management.Automation.Language.AssignmentStatementAst] -and
            $node.Left -is [Management.Automation.Language.VariableExpressionAst] -and
            $node.Left.VariablePath.UserPath -ceq 'source'
    }, $true))
    if ($assignments.Count -ne 1) { throw 'Expected one source assignment in maintained initializer.' }
    $sourceStrings = @($assignments[0].Right.FindAll({ param($node)
        $node -is [Management.Automation.Language.StringConstantExpressionAst]
    }, $true))
    if ($sourceStrings.Count -ne 1 -or
            $sourceStrings[0].StringConstantType -ne [Management.Automation.Language.StringConstantType]::SingleQuotedHereString) {
        throw 'Expected exactly one single-quoted C# here-string in maintained initializer.'
    }
    $source = Normalize-LineEndings $sourceStrings[0].Value
    $sourceSha256 = Get-Sha256Hex ([Text.UTF8Encoding]::new($false).GetBytes($source))
    $transformed = Replace-ExactlyOnce $source $namespaceBefore $namespaceAfter 'test namespace'
    for ($index = 0; $index -lt 9; $index++) {
        $transformed = Replace-ExactlyOnce $transformed $nativeBefore[$index] $nativeAfter[$index] "native binding $index"
    }
    $lastErrorToken = 'Marshal.GetLastWin32Error()'
    $lastErrorCount = ([regex]::Matches($transformed, [regex]::Escape($lastErrorToken))).Count
    if ($lastErrorCount -ne 7) { throw "Maintained last-error accessor count was $lastErrorCount, expected 7." }
    $transformed = $transformed.Replace($lastErrorToken, 'SyntheticNativeCalls.GetLastError()')
    $insertMarker = '        private static bool InvalidHandle(IntPtr handle) {'
    $transformed = Replace-ExactlyOnce $transformed $insertMarker ($testOnlyBlock + $insertMarker) 'test-only fake block insertion point'

    $roundTrip = Replace-ExactlyOnce $transformed ($testOnlyBlock + $insertMarker) $insertMarker 'test-only fake block reversal'
    $roundTrip = $roundTrip.Replace('SyntheticNativeCalls.GetLastError()', $lastErrorToken)
    for ($index = 8; $index -ge 0; $index--) {
        $roundTrip = Replace-ExactlyOnce $roundTrip $nativeAfter[$index] $nativeBefore[$index] "native reverse binding $index"
    }
    $roundTrip = Replace-ExactlyOnce $roundTrip $namespaceAfter $namespaceBefore 'namespace reverse'
    $roundTripExact = [string]::Equals($roundTrip, $source, [StringComparison]::Ordinal)
    if (-not $roundTripExact) { throw 'Reverse transform did not reproduce the AST-extracted source exactly.' }
    if ($transformed.Contains('[DllImport(') -or $transformed.Contains('Marshal.GetLastWin32Error()')) {
        throw 'Transformed source retained a real native import or OS last-error reader.'
    }
    $transformedSha256 = Get-Sha256Hex ([Text.UTF8Encoding]::new($false).GetBytes($transformed))

    $compileTimer = [Diagnostics.Stopwatch]::StartNew()
    Add-Type -TypeDefinition $transformed -Language CSharp -ErrorAction Stop | Out-Null
    $compileTimer.Stop()
    $compileElapsedMs = [Math]::Round($compileTimer.Elapsed.TotalMilliseconds, 3)
    $compiled = $true
    $harnessType = 'TroubleshootJsVerifierBoundReceiptParentTest1.ParentCensus' -as [type]
    if ($null -eq $harnessType) { throw 'Source-derived synthetic test type did not load.' }

    $cases = @(
        [pscustomobject]@{ id='control-valid-one-child'; throws=$false; count=1; marker=''; closeData=$false; outstanding=0 },
        [pscustomobject]@{ id='control-valid-empty-filter'; throws=$false; count=0; marker=''; closeData=$false; outstanding=0 },
        [pscustomobject]@{ id='control-reduced-rights-fallback'; throws=$false; count=1; marker=''; closeData=$false; outstanding=0 },
        [pscustomobject]@{ id='first-entry-failure'; throws=$true; count=0; marker='Process32FirstW failed; an empty or incomplete process snapshot is not accepted.'; closeData=$false; outstanding=0 },
        [pscustomobject]@{ id='partial-toolhelp-enumeration'; throws=$true; count=0; marker='Process32NextW did not terminate with no-more-files.'; closeData=$false; outstanding=0 },
        [pscustomobject]@{ id='matching-child-open-failure-after-valid-child'; throws=$true; count=0; marker='OpenProcess failed for a filtered parent child.'; closeData=$false; outstanding=0 },
        [pscustomobject]@{ id='same-handle-identity-query-failure-after-valid-child'; throws=$true; count=0; marker='Retained process handle PID did not match its Toolhelp entry.'; closeData=$false; outstanding=0 },
        [pscustomobject]@{ id='native-parent-query-failure'; throws=$true; count=0; marker='NtQueryInformationProcess(parent) failed'; closeData=$false; outstanding=0 },
        [pscustomobject]@{ id='image-query-failure'; throws=$true; count=0; marker='QueryFullProcessImageNameW failed for a filtered child.'; closeData=$false; outstanding=0 },
        [pscustomobject]@{ id='process-handle-close-failure'; throws=$true; count=0; marker='CloseHandle failed for the filtered process handle.'; closeData=$false; outstanding=1 },
        [pscustomobject]@{ id='primary-and-process-close-failure'; throws=$true; count=0; marker='GetProcessTimes failed for a filtered child.'; closeData=$true; outstanding=1 },
        [pscustomobject]@{ id='snapshot-handle-close-failure'; throws=$true; count=0; marker='CloseHandle failed for the Toolhelp process snapshot.'; closeData=$false; outstanding=1 }
    )

    foreach ($case in $cases) {
        $exception = $null
        $messages = New-Object Collections.ArrayList
        $closeFailureData = $false
        $records = @()
        $harnessType::ConfigureSyntheticScenario([string]$case.id)
        try { $records = @($harnessType::GetByParent(900)) }
        catch {
            $exception = $_.Exception
            $current = $exception
            while ($null -ne $current) {
                [void]$messages.Add([string]$current.Message)
                if ($null -ne $current.Data) {
                    foreach ($key in $current.Data.Keys) { if ([string]$key -ceq 'CloseHandleFailure') { $closeFailureData = $true } }
                }
                $current = $current.InnerException
            }
        }
        $message = [string]::Join(' | ', [string[]]$messages.ToArray())
        $openCalls = [int]$harnessType::FakeOpenProcessCallCount
        $firstCalls = [int]$harnessType::FakeProcessFirstCallCount
        $nextCalls = [int]$harnessType::FakeProcessNextCallCount
        $parentCalls = [int]$harnessType::FakeParentQueryCallCount
        $closeFailures = [int]$harnessType::FakeCloseFailureCount
        $outstanding = [int]$harnessType::FakeOutstandingHandleCount
        $pass = $false
        if ([bool]$case.throws) {
            $pass = ($null -ne $exception -and $records.Count -eq 0 -and
                $message.Contains([string]$case.marker) -and $closeFailureData -eq [bool]$case.closeData -and
                $outstanding -eq [int]$case.outstanding)
        } else {
            $pass = ($null -eq $exception -and $records.Count -eq [int]$case.count -and
                $outstanding -eq [int]$case.outstanding)
            if ($case.id -ceq 'control-valid-one-child') {
                $record = $records[0]
                $pass = $pass -and [int]$record.ProcessId -eq 101 -and
                    [int]$record.ParentProcessId -eq 900 -and [string]$record.Name -ceq 'child101.exe' -and
                    [string]$record.ExecutablePath -ceq 'C:\Synthetic\child101.exe' -and
                    $record.CommandLine -is [string] -and $record.CommandLine.Length -eq 0 -and
                    $record.CreationDate -is [DateTime] -and $record.CreationDate.Kind -eq [DateTimeKind]::Utc -and
                    $record.CreationDate.Ticks -gt 0
            } elseif ($case.id -ceq 'control-valid-empty-filter') {
                $pass = $pass -and $firstCalls -eq 1 -and $nextCalls -eq 1 -and $openCalls -eq 0
            } else {
                $record = $records[0]
                $accessMasks = @($harnessType::FakeOpenAccessMasks)
                $pass = $pass -and [int]$record.ProcessId -eq 101 -and [int]$record.ParentProcessId -eq 900 -and
                    [string]$record.Name -ceq 'child101.exe' -and
                    [string]$record.ExecutablePath -ceq 'C:\Synthetic\child101.exe' -and
                    $record.CommandLine -is [string] -and $record.CommandLine.Length -eq 0 -and
                    $record.CreationDate -is [DateTime] -and $record.CreationDate.Kind -eq [DateTimeKind]::Utc -and
                    $record.CreationDate.Ticks -gt 0 -and $openCalls -eq 3 -and $accessMasks.Count -eq 3 -and
                    [uint32]$accessMasks[0] -eq [uint32]0x1400 -and
                    [uint32]$accessMasks[1] -eq [uint32]0x0400 -and
                    [uint32]$accessMasks[2] -eq [uint32]0x1000 -and
                    [uint32]$harnessType::FakeSuccessfulOpenAccessMask -eq [uint32]0x1000
            }
        }
        if ($case.id -ceq 'partial-toolhelp-enumeration') { $pass = $pass -and $nextCalls -eq 1 }
        if ($case.id -ceq 'matching-child-open-failure-after-valid-child') { $pass = $pass -and $openCalls -eq 4 }
        if ($case.id -ceq 'native-parent-query-failure') { $pass = $pass -and $parentCalls -eq 1 }
        if ($case.id -ceq 'image-query-failure') { $pass = $pass -and $openCalls -eq 1 -and $outstanding -eq 0 }
        if ($case.id -ceq 'process-handle-close-failure' -or $case.id -ceq 'snapshot-handle-close-failure') { $pass = $pass -and $closeFailures -eq 1 }
        if ($case.id -ceq 'primary-and-process-close-failure') { $pass = $pass -and $closeFailures -eq 1 }
        [void]$caseRows.Add([pscustomobject][ordered]@{
            id = [string]$case.id
            status = if ($pass) { 'PASS' } else { 'FAIL' }
            expectedThrow = [bool]$case.throws
            threw = ($null -ne $exception)
            returnedRecordCount = [int]$records.Count
            expectedErrorMarkerFound = if ($case.throws) { $message.Contains([string]$case.marker) } else { $null }
            closeFailureDataFound = $closeFailureData
            fakeCalls = [pscustomobject]@{ first=$firstCalls; next=$nextCalls; open=$openCalls; parentQuery=$parentCalls; closeFailures=$closeFailures }
            requestedAccessMasks = @($harnessType::FakeOpenAccessMasks | ForEach-Object { [uint32]$_ })
            successfulOpenAccessMask = [uint32]$harnessType::FakeSuccessfulOpenAccessMask
            fakeOutstandingHandles = $outstanding
        })
        $harnessType::ResetSyntheticScenario()
    }
} catch {
    $outerError = [string]$_.Exception.Message
    $outerError = $outerError.Replace($modulePath, '<maintained-module>').Replace($OutputDirectory, '<task-output>')
} finally {
    try {
        $moduleBytesAfter = [IO.File]::ReadAllBytes($modulePath)
        $moduleSha256After = Get-Sha256Hex $moduleBytesAfter
        if ($null -ne $moduleSha256Before -and $moduleSha256After -cne $moduleSha256Before) {
            $outerError = 'Maintained module changed during the AST extraction and synthetic test run.'
        }
    } catch {
        if ($null -eq $outerError) { $outerError = 'Could not rehash maintained module after the synthetic test run.' }
    }
    $timer.Stop()
}

$passed = @($caseRows | Where-Object { $_.status -ceq 'PASS' }).Count
$failed = @($caseRows | Where-Object { $_.status -ceq 'FAIL' }).Count
$status = if ($null -eq $outerError -and $compiled -and $roundTripExact -and
    $moduleSha256Before -ceq $moduleSha256After -and $caseRows.Count -eq 12 -and $passed -eq 12 -and $failed -eq 0) {
    'PASS_SYNTHETIC_FAIL_CLOSED'
} else { 'FAIL' }
$report = [pscustomobject][ordered]@{
    schema = 'maintained-native-parent-census-synthetic-errors-v1'
    status = $status
    scope = 'AST-extracted maintained C# body with deterministic synthetic native-call wrappers; no OS process query or real native import'
    maintainedModuleSha256Before = $moduleSha256Before
    maintainedModuleSha256After = $moduleSha256After
    maintainedModuleUnchanged = ($moduleSha256Before -ceq $moduleSha256After)
    extractedSourceSha256 = $sourceSha256
    transformedSourceSha256 = $transformedSha256
    sourceRoundTripExact = $roundTripExact
    transformations = [pscustomobject]@{ namespaceRename=1; nativeBindings=9; lastErrorAccessor=7; syntheticCases=12; dllImportRemaining=0; getLastWin32ErrorRemaining=0 }
    powershellVersion = $PSVersionTable.PSVersion.ToString()
    clrVersion = [Environment]::Version.ToString()
    addTypeCompiled = $compiled
    compileElapsedMs = $compileElapsedMs
    operationElapsedMs = [Math]::Round($timer.Elapsed.TotalMilliseconds, 3)
    processQueriesPerformed = $false
    cases = @($caseRows.ToArray())
    passed = [int]$passed
    failed = [int]$failed
    outerError = $outerError
}
$reportPath = Join-Path $OutputDirectory 'result.json'
[IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 10) + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
foreach ($row in @($caseRows)) { Write-Output ('{0} {1}' -f $row.status, $row.id) }
Write-Output ('RESULT {0} {1}/12 compileMs={2} operationMs={3} sourceRoundTrip={4} moduleStable={5}' -f $status, $passed, $compileElapsedMs, $report.operationElapsedMs, $roundTripExact, $report.maintainedModuleUnchanged)
if ($status -cne 'PASS_SYNTHETIC_FAIL_CLOSED') { exit 1 }
exit 0
