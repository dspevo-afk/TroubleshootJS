# Preserved first-source defects

The first transformer9f029073 generated source successfully: actual0,
383.2761ms operation/649.9266ms wrapper. Root source review rejected that source
before compile/run because it moved opt-in ArmRun construction outside the measured
wrapper group and omitted new guard release from measured cleanup duration.
Generation PASS was not accepted source or runtime proof.

Original transformer/notes are archived byte-for-byte; all four first outputs
remain retained. The corrected transformer efdeccf1 keeps construction in the
original measured group and adds actual opt-in guard-release time to existing
cleanup measurement without inventing owner/context flags. Final generation,
compile, focused/default/copy checks and review use corrected source only.

The six deliberate generator negative controls are EXPECTED_REJECTION with
actual exit2, separate from native PASS. No prior failure, budget, independent
oracle, unrelated worktree or uncertain resource was removed or weakened.

Staged whitespace review also rejected one extra EOF blank line in the generator.
The exact prior bytes/notes were archived. Removing that single newline changed
no AST or generated Java/diff bytes; fresh generator host controls and an exact
native-input reuse audit resolved the packaging defect without weakening checks.
