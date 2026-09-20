# Windows Quick Play preview launch recovery

## Scope and candidate

Base HEAD: `5b07cdef23af774bcc34cbdb3fa3f4b76e517948` on
`codex/task43p-final-recovery`. This correction changes only the PowerShell
preview launcher and stopper. Existing Java, web and unrelated evidence edits
in the worktree were preserved. The user-reported stale state was archived by
the launcher before this task; its recorded PID was absent.

## Cause and correction

The preview computes execution-tree provenance before it binds its listener.
A direct provenance measurement took 10.16 seconds for 1,491 files. One later
successful launcher run took 14.94 seconds end to end, close to the former
15-second startup allowance. When readiness timed out with an empty stderr
file, `Get-Content -Raw` supplied null and the launcher's `.Trim()` call hid
the timeout with `You cannot call a method on a null-valued expression`.

The launcher now allows 60 seconds, safely normalizes empty diagnostics and
reports whether the child exited or timed out. Its internal module import no
longer prints the unapproved-verb warning. A successful detached preview also
outlives its launcher parent; the stopper now verifies the current process and
the live preview identity route before it terminates that exact child.

## Checks

| Check | Result | Evidence |
| --- | --- | --- |
| Reproduce short startup | FAIL before correction | `start-preview.ps1 -QuickPlay -StartupTimeoutSeconds 5` reached the null call at former line 646. |
| Current bounded timeout | PASS | `start-preview.ps1 -QuickPlay -StartupTimeoutSeconds 1` reported `startup timeout on port 8899 (1 s)`; state absent and zero listeners after cleanup. |
| Normal launch | PASS | `start-preview.ps1 -QuickPlay -BuildIfMissing` started the preview without an import warning and printed the correct Quick Play URL; one run took 14.94 seconds. Existing compiled output was used. |
| Desktop launcher host | PASS | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-preview.ps1 -QuickPlay -BuildIfMissing` exited 0 in 10.83 seconds; the same host's one-second negative launch exited 1 with the explicit timeout and no state/listener. |
| Actual player route | PASS | In-app browser opened the production URL, showed a customer ticket, and accepted the ticket through a visible button click to display the board. Browser error log was empty. See [ticket](ticket.png) and [board](board.png). |
| Detached stop | PASS | `stop-preview.ps1` stopped launcher-orphaned preview PIDs through exact route/process proof, including a `powershell.exe` host run; state removal and zero listeners were confirmed. |
| Stale-state refusal | Expected rejection | `stop-preview.ps1 -StateFile <archived-stale-state>` exited 2, kept the active preview and state intact. This rejection is not a successful stop. |
| Existing strict canaries | PASS | `verify-gate-b.ps1 -GateBStopPreviewProbe -SkipJdkCheck` and `-GateBStartPreviewAdoptionProbe -SkipJdkCheck` exited 0. |

The screenshots show the current local production output with pre-existing
uncommitted web edits. They prove the launcher reached a playable page, not a
fresh JDK8/GWT build of those unrelated edits. No Java production source was
changed by this correction, so `scripts/build.ps1` was not applicable. The
test browser tab was closed; the task-owned preview was stopped; the active
preview state is absent and port 8899 has no listener. Next unstarted roadmap
milestone: U02.
