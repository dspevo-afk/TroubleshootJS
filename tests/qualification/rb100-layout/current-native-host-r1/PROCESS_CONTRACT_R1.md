# Direct-child Windows process contract

`tests/contracts/VerifierBoundedProcessContract.ps1` exercises the existing
`Invoke-VerifierBoundedProcess` owner with an explicit CODE-only Python fixture.
It is a standalone opt-in contract, with no JDK, challenge, Study, DATA, normal
qualifier or player entrypoint. Invoke it using Windows PowerShell 5.1, explicit
module/fixture/executable paths, expected fixture SHA-256, a fresh OS-temp
evidence root and `-Mode Smoke` or `-Mode Suite`. The fixture receives `-I -B`.
Module and child-image bytes are pinned before the module is imported or the
child launched. Expected hashes are caller-supplied evidence, not provenance.
The selected PS5.1 process must resolve its own built-in modules: the lab sets
`PSModulePath` to that executable's `Modules` directory in the child environment
only. Inherited PS7 module search paths failed the early host check; no global
environment, module installation or execution policy is changed.

The positive oracle compares the original owner's PID/start ticks against the
child's independent Win32 current-process creation time and image path, image
SHA-256, raw command line and Python argument vector. Success and exit7 remain
distinct, stdout/stderr are compared, and successful owner log cleanup must
already be complete. The suite adds rapid exits, a real sleeping-child timeout
and malformed invocation rejections before any child state file is created.
Expected timeout evidence remains retained and is not reported as a successful
process operation. Process absence is observed; uncertain PIDs are never killed
by the test. Only the existing owner's retained original handle authorizes stop.

The unchanged owner captures both streams with `ReadToEndAsync`, so this finite
fixture does **not** establish a byte limit during capture. It has no cancel
parameter and no descendant containment proof. Invocation values returned by
the owner are not independent observed-image/argv evidence. Its operation
includes cleanup; successful cleanup is unmeasured separately, never zero.
These limitations block promotion to normal native transport authority.

This contract does not change process implementation, global execution policy,
tools, normal qualifier pins, Source/class/JAR authority, Study holds, budgets,
seeds, epochs, routing, electrical truth or public admission. A current-tool
canary does not refresh a historical qualifier's executable pin. It neither
reads protected data nor repairs the previously uncertified unread boundary.
