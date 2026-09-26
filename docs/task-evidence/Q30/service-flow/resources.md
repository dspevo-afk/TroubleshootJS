# Task resource accounting

- Final preview: PowerShell running this worktree's `scripts/preview.ps1`, port
  8903, PID 37052, creation ticks 639259887579316188. Ownership was recorded by
  `start-preview.ps1` before use and revalidated by `stop-preview.ps1` before
  termination. `preview-stop-preparation-final.log`: PASS/exit 0, process stopped
  and port positively released. The maintained wrapper removed its owned state.
- Earlier preview PIDs were also stopped through the same maintained wrapper;
  their individual `preview-stop-*.log` files retain results. In particular,
  PID 37944 (creation ticks 639259875962462736) and diagnosis PID 28952
  (639259882653695675) were stopped before rebuilding.
- Native children and task-owned scratch: PASS cleanup in the full native and
  final affected-native logs. An earlier timeout's two partial output files were
  copied into this evidence folder; its verified task-only scratch was removed.
- Independent reader: seven exact temporary files removed, per its exit-0
  canary report. No persistent helper process was launched.
- Browser: every completed final-census tab and the final player tab were closed
  through Browser. Earlier crashed temporary tabs 1, 2 and 4 remain listed as
  `This page crashed`; Browser's crash-URL policy blocked their cleanup. Cleanup
  for those three tabs is UNPROVED. No unrelated browser/runtime was terminated.
- Documented build outputs under `.tools`/`war` remain in the isolated worktree.
  The dirty ordinary checkout was not edited or cleaned.
