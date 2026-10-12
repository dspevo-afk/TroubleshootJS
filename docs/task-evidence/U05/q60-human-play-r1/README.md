# One short RB56 human-play attempt

Please make one human qualitative play attempt on the private 44-part regression candidate and record any prior exposure or assistance. This same candidate has appeared in guided software evidence, so the attempt is not an independent blind trial. This is a qualitative human observation for the existing U05 protocol. It does not freeze HARD, establish a success rate, or claim REL-B release readiness. Human strategy and outcome are needed before a HARD rule is frozen; independent REL-B session work can continue separately.

**Current build:** `82ED33BBD8DE32C145D1C3B4A3CEB5F3`; compact-board candidate over
`7d733a10bb203a1cda7b8f55409cb9b1b6e11cd2`, identified by the
[current compact-layout software evidence](../../PCB/compactness-r1/README.md).
Fresh private requests use placement2; existing saves retain their original
placement1 geometry. This changes the physical arrangement of the same electrical
regression seed below. App replay remains `tsj-alpha/5`, dependency epoch remains
`tsj-generation-dependencies-v16`; default persistence stays unavailable. Current
headed195 checks the ordinary ticket/workbench route with only the additional
read-only `tsjVerifyGeometry=true` metrology flag; the human URL below omits it.
Headed198 qualifies opt-in private file restoration and software-guided repair,
not human findings. Earlier screenshots/builds retain their historical scope.

Start the existing foreground preview from the repository:

```powershell
.\scripts\preview.ps1 -Port 8898 -ForceTcpListener
```

In Edge, open this exact route on the local preview:

```text
http://127.0.0.1:8898/circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyRb56=true&tsjRb56Seed=-1004&tsjRb56Bench=true&tsjRb56PlayerSession=true&tsjRb56Session=true&running=true
```

The cold candidate preparation keeps its existing limit of 90 seconds. The six-minute private session window starts when the ticket is published and includes time spent reading the ticket and menus. There is no minimum play duration, and a successful repair is not guaranteed. Stop when you want; a stuck or unfinished attempt is useful evidence.

Accept the ticket, read the complaint, and examine and measure the board as you normally would. Before any physical change, isolate every supply and wait for residual energy to clear. Use Shop and attempt a repair and customer retest only if you feel ready. Do not use answer hints or ask for the selected fault.

If Results appears after a completed customer retest, close the session from Results. If you stop before Results, end the qualification view or let it expire; the verifier records FAIL and restores its owner. Keep that verifier status distinct from your own recorded human outcome. The private session does not support save, load, share, or replay.

Jot observations during play or fill out [the plain-text note](human-play-note.txt) immediately afterward. Return the plain-text note in the existing conversation if you want the trial recorded, or keep it local. Record only your own visible observations and actions; do not copy private verifier reports, netlists, private state digests, raw exports or selected-fault metadata. The build/seed above are reproduction identifiers, not answer data. The current ordinary ticket/workbench launch is source-bound by headed195; its only added flag exposes read-only geometry for software metrology. Software repair/retest evidence is separate from the human attempt. No human diagnosis or outcome was automated. Keep the trial tab in the foreground. Stop the local preview with Ctrl+C after recording your notes; no background preview is left by these instructions.

Protocol source: [U05 human-trial and later HARD requirements](../README.md) . The protocol calls for strategy, outcome or stopping point, elapsed time, measurements/actions, retries/errors, assistance, and clarity issues.

The protocol prescribes no minimum duration. This configured six-minute attempt can supply initial qualitative findings, including confusion or a stopping point; it does not by itself complete HARD calibration. Independent REL-B save/restore work can continue while those findings are pending. The preview serves only this PC at 127.0.0.1. If port 8898 is busy, leave the existing listener alone and use another free port in both the command and URL.

Current software follow-up: the new layout is qualified by native184/GWT46/
headed195/198 in [the compact-board packet](../../PCB/compactness-r1/README.md).
Fixed40/44/60 outlines are14.58%/16.51%/11.59% smaller; full required text is retained
at checked fit/wheel views. Some gaps remain/increase. The default human URL is
unchanged. The same electrical candidate appeared in guided software repair;
disclose prior exposure/assistance. No human strategy/outcome is supplied and HARD
remains held. Historical independent recovery uses buildE6CA9A4CB99EE6E4301B9F1C980D1830
as recorded in [its matrix](../../REL-B/release-matrix-r1/README.md).
