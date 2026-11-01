# Retained terminal-preview recovery

**Status: VALIDATED LOCAL; publication pending.** This is a host-resource
prerequisite only. It does not qualify Q100 layout timing, a playable RB100, or any
electrical/model behavior.

## Scope and invariants

`VerifierIsolation` retains the original ownerless one-CDP recovery mode and adds
one narrowly admitted form: one bound, close-unattempted CDP session beside one
fully terminal run-owned preview. Recovery preserves the original lease/server
history and requires the preview's canonical lowercase GUID-N lease identity,
canonical claim path, terminal proof, and fresh original process/listener absence
at admission, after claim, before intent, and immediately before journaling a
close attempt. The recovery authority remains scoped to the exact run, preview,
and CDP session. Active or caller-owned previews, extra resources, and malformed
records fail before resource mutation. Existing process, root, listener, job,
natural-exit, claim, profile, and cleanup proofs remain required. The 15-second
shutdown and 120-second cleanup limits are unchanged; each actual host case also
requires a maintained 600-second outer process bound.

No solver, client, UI, seed, schema, epoch, Player capability, or public admission
behavior changes. Normal cleanup and the original ownerless recovery mode remain
unchanged. See [PLAN.md](PLAN.md) for the frozen scope and acceptance boundaries.

## Reproduction commands

Run from this worktree root in Windows PowerShell 5.1.
Use a fresh, task-owned `<unique-temp>` directory for receipts and each real case.
The final Developer build must be installed in this worktree's `war` directory.
`<JDK8>` is the installed JDK 8 home; there is no build step inside the recovery
host tests.

```powershell
$jdk8 = '<JDK8>'
.\scripts\build.ps1 -JavaHome $jdk8 -Target Compile -Variant Developer -Style OBF

$temp = '<unique-temp>'
New-Item -ItemType Directory -Path $temp -ErrorAction Stop | Out-Null
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File .\scripts\verify-current-contracts.ps1 `
  -RetainedPreviewRecoveryContractOnly `
  -ReceiptOutputPath (Join-Path $temp 'focused-fixtures.txt')

# Compatibility control against the separately pinned original5e module copy.
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File .\tests\contracts\retained-preview-recovery-contract.ps1 `
  -ModulePath '<original5e VerifierIsolation.psm1>' `
  -ExpectedModuleSha256 '5e0ae0c8fb6beb20c2d9fff3f652074136032197eab61f450c5fdceaa4e3a1fa' -LegacyOnly

# Run each case separately through the maintained outer supervisor. Replace
# <unique-temp> with a fresh directory root before each case.
$root = (Get-Location).Path
$powershell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
Import-Module (Join-Path $root 'scripts\VerifierIsolation.psm1') -Force
$parent = Join-Path $root 'tests\contracts\browser-terminal-preview-recovery.ps1'
$moduleSha = 'fc160736e0de967cbea5090a9546e771e64207181db51978f1350bc1b77f64e6'
$case = 'TerminalPreview' # repeat with 'Ownerless' and a different evidence directory
$arguments = @('-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass',
  '-File',$parent,'-Case',$case,'-RepositoryRoot',$root,
  '-EvidenceDirectory',(Join-Path '<unique-temp>' $case),
  '-ExpectedModuleSha256',$moduleSha)
$bounded = Invoke-VerifierBoundedProcess $powershell $arguments 600000
if (-not $bounded.TerminationProven -or $bounded.ExitCode -ne 0) {
  throw "Recovery case failed: exit=$($bounded.ExitCode), terminated=$($bounded.TerminationProven)"
}
$bounded.Stdout

# Final normal cleanup/navigation regression, after the host cases finish.
.\scripts\verify-a03-browser.ps1 -Gate Current -WorktreeRoot $root `
  -OutputPath (Join-Path $temp 'current.json') -TimeoutSeconds 90
```

The focused runner emits only the retained-preview fixture contract; it does not
run the Java matrix or real host recovery. The real-case parent script records a
controlled JSON result beneath each evidence directory. Do not publish raw
authority manifests, recovery nonces, process command lines, or uncontrolled logs.

## Validation

| Check | Status | Recorded result and limit |
|---|---|---|
| Final-source JDK 8/GWT Developer build | PASS | 174.3973063 s; 1,199 inputs, 357 outputs. Input SHA-256 `5dd76a5abe9ebe2e9c90aff2489189e361c79c8824ccd1e0de1ceed19f6a1e83`; output SHA-256 `611f4f10e55796a05d4e0769b3d66d3265b3f6fc25c987d6102ed8a6c162b064`. Inventory SHA-256 `26baece0c292a21a304cf47cba9c5fa171519272aa2bc926d52449bc2f31ffa5`. |
| Maintained synthetic recovery contract | PASS | 192 assertions / 14,681 ms; source fixture SHA-256 `e49d54a8cce255139b558e005530fec11cbce87ca757f029eaf6147ff17d65ba`. |
| Registered focused runner | PASS | 192 assertions / 16,645 ms; exit 0, termination proven; runner controls passed. This is fixture/source-order evidence, not OS recovery proof. |
| Original5e ownerless-only control | PASS | 14 assertions / 1,607 ms for bound and issued-contained legacy modes; preview census and host recovery were not run by this control. |
| Actual prior admission and live-listener refusal | PASS | Scoped host observations; they do not replace the two final selected host cases. |
| Actual recovery following a failed run | PASS | Recovery operation 62,853 ms and within the 120-second cleanup upper bound; exact cleanup subphase duration was not separately reported. The earlier injected canary remains FAIL. |
| Current1 normal regression | PASS, pre-registration only | Nine routes / 351.9357935 s. This run preceded registration of the final host tests and is not final Current evidence. |
| Final registered Current regression | PASS | Current2: nine routes / 351.1663231 s, exit 0 and termination proven; source, scripts, build inputs, web and complete execution tree unchanged. Shutdown 9,968 ms. Cleanup succeeded; exact aggregate cleanup duration is not supplied. |
| First `TerminalPreview` host attempt | FAIL | Child exit 2 / termination proven; parent JSON decoding failed before recovery. Its lost inner diagnostics leave the original cause unconfirmed. The corrected harness preserves child streams; the failure and exact source remain preserved. |
| Final `Ownerless` host case | PASS | Exact child, witness, source, immutable identity, receipt, process, listener, claim and profile proofs pass. Child setup 6,548 ms; recovery API 52,737 ms; case 60,772 ms; externally bounded process 61,331 ms, exit 0 and termination proven. |
| Final `TerminalPreview` host case | PASS | Same proofs plus preserved terminal preview history and fresh preview process/listener/claim absence. Child setup 38,135 ms includes preview terminalization 8,511 ms; recovery API 55,777 ms; case 95,453 ms; external process 96,097 ms, exit 0 and termination proven. |
| Actual HTTP.sys ownership canary | PASS | Default owned preview and real PID 4 listener. Positive proofs 297 and 302 ms; specific typed wrong-identity rejection 139 ms; all within the original 500 ms cap. Nonce restored and manifest, claim and handles unchanged. Operation 27,771 ms; cleanup 8,562 ms. |
| Final consumed-input audit | PASS | Eleven build/execution comparisons; 22,801 ms. All compiler inputs, outputs, source, script, web and complete execution digests/counts equal the passing Current2 artifact. This audits reuse after test/documentation changes. |
| Self-hosted CI | NOT RUN | Self-hosted runner setup remains a prerequisite. |

The retained recovery owner is `scripts/VerifierIsolation.psm1`, SHA-256
`fc160736e0de967cbea5090a9546e771e64207181db51978f1350bc1b77f64e6`. The
parent is `620f8467545634f68ee9ee007c6560644e203ef98c3ed3f40da66e0014b4bf9c`;
child is `274a358238a8d165904fb2d7f6940149562a08cac923bbdeef6a2bedae9b5471`.
The parent rechecks all four pins after its fresh module import before recovery.
Earlier source iterations and failures remain preserved and are not reclassified.
The raw evidence containing authority and process details stays private.

Both recovery API intervals bound cleanup from above and are below 120 seconds;
the API does not supply exact nested cleanup or natural-shutdown subphase times.
The terminal-preview cleanup and recovery intervals sum to 64,288 ms, but that
sum is not a continuous wall-clock interval. The 15-second natural shutdown
limit remains enforced. Current's exact aggregate cleanup duration is not supplied.

The first live harness attempts remain FAIL: lost child diagnostics, misuse of
the path checker's exact-root switch, and missing lease-owner fields in the child
witness were corrected without dropping assertions. The complete frozen pre/post
helper fixture then passed, using independently recorded child identity and a
clearly synthetic claim-presence lookup. A separate existing 500 ms kernel proof
miss remains a failure; the later focused canary and final host cases do not
establish its general cause or a universal host-stability cure. The failed
ownerless case's retained browser was recovered in a separate 53,430 ms API call;
that cleanup does not change the case result.

## Limits

Publication is still pending. The injected canary remains
FAIL despite the later successful recovery observation. HARD/PSYCHOTIC remain
unavailable and unrun; protected qualification data remains unmaterialized and
the historical unread boundary remains uncertified. Q100 timing is not closed,
zero authored-100 layouts have been accepted, and the Q10040/48 experiments were
not adopted. No full matrix, 30-root, 2-by-31, modest-host, or CI claim follows.
